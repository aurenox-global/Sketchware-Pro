package mod.hey.studios.compiler.flutter;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import a.a.a.ProjectBuilder;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.flutter.FlutterPluginCompiler;
import pro.sketchware.flutter.FlutterPluginSupport;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;

/**
 * Empaquetado de la **parte Android de los plugins** de Flutter (carril I, Fase 8).
 *
 * <p>Implementa el plan de 6 pasos del carril P (§3.3) usando lo que el fork ya tiene:
 * <ol>
 *   <li><b>detección</b> {@link FlutterPluginSupport#detectAndroidPlugins(File)} (ya existía);</li>
 *   <li><b>fuentes</b> Java/Kotlin del plugin + las generadas por Flutter
 *       (<code>.dart_tool/flutter_build/**</code>, que incluye
 *       <code>io/flutter/plugins/GeneratedPluginRegistrant.java</code>) — antes el registrant se
 *       generaba y <b>no</b> entraba a la compilación;</li>
 *   <li><b>manifests</b> de los plugins fusionados con
 *       {@link pro.sketchware.flutter.FlutterPackagingSupport#mergePluginManifests(File, List)};</li>
 *   <li><b>dependencias</b> de Gradle del plugin resueltas a AAR con el
 *       {@link DependencyResolver} del fork y registradas como <i>local libraries</i> del proyecto
 *       (que es como el build ya lleva jars + dex + res + packageName);</li>
 *   <li><b>compilación</b> con ECJ (Java) y kotlinc (Kotlin) del fork a
 *       <code>yq.compiledClassesPath</code>, el mismo directorio que D8 dexea;</li>
 *   <li><b>dexado</b>: lo hace el pipeline normal del fork tras este paso.</li>
 * </ol>
 *
 * <p>Todo es <b>best effort</b> y nunca lanza: si algo falta (sin red para los AAR, sin ECJ…), se
 * registra en el log y se devuelve <code>false</code>; el APK sigue construyéndose y, como el
 * <code>GeneratedPluginRegistrant</code> de Flutter envuelve cada plugin en su propio
 * <code>try/catch</code>, un plugin que no se pudo compilar no tumba la app al arrancar.
 *
 * <p><b>NO verificado en dispositivo</b> (el carril I no puede compilar el APK: el padre es el único
 * que lanza el build). El punto exacto que falta está listado en el informe del carril.
 */
public final class FlutterPluginPackager {

    private static final String TAG = "FlutterPluginPackager";

    private FlutterPluginPackager() {
    }

    /**
     * Compila y registra la parte Android de todos los plugins del proyecto.
     *
     * @return número de plugins con parte nativa empaquetados correctamente (0 si no hay plugins).
     */
    public static int packPlugins(Context context, ProjectBuilder builder, StringBuilder log) {
        if (builder == null || builder.yq == null) {
            log.append("[warn] sin builder/yq: no se empaquetan plugins Flutter\n");
            return 0;
        }
        File flutterRoot = FlutterCompilerBridge.flutterRootDirectory(builder.yq);

        List<FlutterPluginSupport.AndroidPlugin> plugins;
        try {
            plugins = FlutterPluginSupport.detectAndroidPlugins(flutterRoot);
        } catch (Throwable t) {
            Log.e(TAG, "No se pudieron detectar los plugins Flutter", t);
            log.append("[warn] deteccion de plugins fallida: ").append(t).append('\n');
            return 0;
        }
        if (plugins.isEmpty()) {
            log.append("[ok] sin plugins Android que empaquetar\n");
            return 0;
        }

        log.append("[info] plugins Android detectados: ").append(plugins.size()).append('\n');
        for (FlutterPluginSupport.AndroidPlugin plugin : plugins) {
            log.append("       - ").append(plugin.toString())
                    .append(" [java=").append(plugin.getJavaSources().size())
                    .append(", kotlin=").append(plugin.getKotlinSources().size()).append("]\n");
        }

        /* 1) Fuentes Java/Kotlin de los plugins. */
        List<File> javaSources = new ArrayList<>();
        List<File> kotlinSources = new ArrayList<>();
        List<File> pluginManifests = new ArrayList<>();
        boolean needsKotlinStdlib = false;
        for (FlutterPluginSupport.AndroidPlugin plugin : plugins) {
            javaSources.addAll(plugin.getJavaSources());
            kotlinSources.addAll(plugin.getKotlinSources());
            pluginManifests.addAll(plugin.getAndroidManifestFiles());
            if (!plugin.getKotlinSources().isEmpty()) {
                needsKotlinStdlib = true;
            }
        }

        /* 2) Fuentes generadas por Flutter (.dart_tool/flutter_build/**, incluido el registrant). */
        List<File> flutterBuildSources = FlutterPluginCompiler.collectFlutterBuildSources(flutterRoot);
        if (flutterBuildSources.isEmpty()) {
            log.append("[warn] no hay fuentes en .dart_tool/flutter_build (¿se genero el registrant?); ")
                    .append("se regeneran los registrantes ahora\n");
            try {
                FlutterPluginSupport.prepareEntrypoint(context, flutterRoot, message -> {
                    log.append("       ").append(message).append('\n');
                    return kotlin.Unit.INSTANCE;
                }, log);
                flutterBuildSources = FlutterPluginCompiler.collectFlutterBuildSources(flutterRoot);
            } catch (Throwable t) {
                Log.w(TAG, "No se pudieron regenerar los registrantes", t);
                log.append("[warn] no se pudieron regenerar los registrantes: ").append(t).append('\n');
            }
        }

        /* 3) Dependencias de Gradle de los plugins (AAR) -> local libraries del proyecto. */
        List<String> dependencyJars = resolvePluginDependencies(context, builder, plugins, log);

        /* 4) El codigo Kotlin del plugin necesita kotlin-stdlib en el classpath y en el APK. */
        if (needsKotlinStdlib) {
            try {
                builder.builtInLibraryManager.addLibrary(BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB);
                log.append("[ok] kotlin-stdlib anadida a las built-in libraries (plugin Kotlin)\n");
            } catch (Throwable t) {
                log.append("[warn] no se pudo anadir kotlin-stdlib: ").append(t).append('\n');
            }
        }

        /* 5) Classpath: android.jar + embedding Flutter + built-in libs + local libs + AAR de plugins. */
        StringBuilder classpath = new StringBuilder(builder.getClasspath());
        for (String jar : dependencyJars) {
            if (classpath.indexOf(jar) < 0) {
                classpath.append(":").append(jar);
            }
        }

        /* 6) Compilacion (ECJ + kotlinc) al directorio de clases que D8 dexea. */
        File destination = new File(builder.yq.compiledClassesPath);
        File kotlinHome = new File(builder.yq.binDirectoryPath, "kotlin_home");
        FlutterPluginCompiler.PluginCompileResult result = FlutterPluginCompiler.compilePluginSources(
                javaSources, kotlinSources, flutterBuildSources, classpath.toString(), destination, kotlinHome);
        log.append(result.getLog());
        for (String missing : result.getMissingSteps()) {
            log.append("[warn] ").append(missing).append('\n');
        }

        /* 7) Manifest(s) de los plugins. */
        int merged = pro.sketchware.flutter.FlutterPackagingSupport.mergePluginManifests(
                new File(builder.yq.androidManifestPath), pluginManifests);
        log.append("[ok] manifest de plugins: ").append(merged).append(" entradas fusionadas\n");

        if (!result.getSuccess()) {
            log.append("[warn] la parte Android de los plugins NO se compilo por completo; ")
                    .append("la app arrancara con el plugin sin registrar (el registrant va en try/catch)\n");
            return 0;
        }
        return plugins.size();
    }

    /**
     * Resuelve los <code>implementation("g:a:v")</code> de los plugins a AAR con el resolvedor del
     * fork y los registra como <i>local libraries</i> del proyecto.
     *
     * <p>El resolvedor baja el AAR (y sus transitivas) a
     * <code>&lt;external&gt;/.sketchware/libs/local_libs/&lt;artifactId&gt;-v&lt;version&gt;/</code>,
     * lo descomprime (<code>classes.jar</code>, <code>res/</code>…) y lo dexa (<code>classes.dex</code>),
     * que es exactamente el contrato de {@code ManageLocalLibrary}: con esos datos el jar entra al
     * classpath, la res al merge de recursos y el dex al APK.
     *
     * <p>Necesita <b>red</b> (Maven/Google Maven). Sin ella se documenta el paso que falta y se
     * sigue: los plugins cuya parte Android no se pueda compilar quedan en el log.
     *
     * @return rutas de los <code>classes.jar</code> resueltos (vacío si no había deps o falló).
     */
    private static List<String> resolvePluginDependencies(
            Context context,
            ProjectBuilder builder,
            List<FlutterPluginSupport.AndroidPlugin> plugins,
            StringBuilder log) {
        List<String> jars = new ArrayList<>();
        LinkedHashSet<String> coordinates = new LinkedHashSet<>();
        for (FlutterPluginSupport.AndroidPlugin plugin : plugins) {
            for (String coordinate : FlutterPluginSupport.readGradleDependencies(plugin.getPackageDirectory())) {
                if (coordinate.contains(":")) {
                    coordinates.add(coordinate);
                }
            }
        }
        if (coordinates.isEmpty()) {
            log.append("[ok] los plugins no declaran dependencias de Gradle extra\n");
            return jars;
        }

        File localLibsDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs");
        for (String coordinate : coordinates) {
            String[] parts = coordinate.split(":");
            if (parts.length < 3) {
                log.append("[warn] coordenada no soportada: ").append(coordinate).append('\n');
                continue;
            }
            final String groupId = parts[0];
            final String artifactId = parts[1];
            final String version = parts[2];
            final List<String> resolvedNames = new ArrayList<>();
            final List<String> errors = new ArrayList<>();
            log.append("[info] resolviendo ").append(coordinate).append("...\n");
            try {
                DependencyResolver resolver = new DependencyResolver(
                        groupId, artifactId, version, false, builder.build_settings);
                resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                    @Override
                    public void onDownloadError(org.cosmic.ide.dependency.resolver.api.Artifact artifact, Throwable error) {
                        errors.add("descarga: " + error);
                    }

                    @Override
                    public void dexingFailed(org.cosmic.ide.dependency.resolver.api.Artifact artifact, Exception e) {
                        errors.add("dexado: " + e);
                    }

                    @Override
                    public void onTaskCompleted(List<String> artifacts) {
                        resolvedNames.addAll(artifacts);
                    }
                });
            } catch (Throwable t) {
                errors.add(t.toString());
            }

            if (resolvedNames.isEmpty()) {
                log.append("[warn] no se pudo resolver ").append(coordinate)
                        .append(" (¿sin red para Maven/Google Maven?). Paso que falta: registrar el AAR ")
                        .append("como local library manualmente. Detalle: ").append(errors).append('\n');
                continue;
            }
            if (!errors.isEmpty()) {
                log.append("[warn] incidencias resolviendo ").append(coordinate).append(": ")
                        .append(errors).append('\n');
            }
            for (String name : resolvedNames) {
                File dir = new File(localLibsDir, name);
                File jar = new File(dir, "classes.jar");
                File dex = new File(dir, "classes.dex");
                if (!jar.isFile()) {
                    continue;
                }
                jars.add(jar.getAbsolutePath());
                registerLocalLibrary(builder, dir, jar, dex);
            }
        }
        log.append("[ok] dependencias de plugins: ").append(jars.size()).append(" jars\n");
        return jars;
    }

    /**
     * Registra un AAR resuelto como local library del proyecto (mismo esquema que
     * <code>LocalLibrariesUtil</code>): claves {@code name}, {@code jarPath}, {@code dexPath},
     * {@code packageName}, {@code resPath} y {@code assetsPath}.
     */
    private static void registerLocalLibrary(ProjectBuilder builder, File dir, File jar, File dex) {
        if (builder.mll == null || builder.mll.list == null) {
            return;
        }
        for (HashMap<String, Object> existing : builder.mll.list) {
            Object name = existing.get("name");
            if (name instanceof String && ((String) name).equals(dir.getName())) {
                return;
            }
        }
        HashMap<String, Object> entry = new HashMap<>();
        entry.put("name", dir.getName());
        entry.put("jarPath", jar.getAbsolutePath());
        if (dex.isFile()) {
            entry.put("dexPath", dex.getAbsolutePath());
        }
        File config = new File(dir, "config");
        if (config.isFile()) {
            entry.put("packageName", FileUtil.readFile(config.getAbsolutePath()).trim());
        }
        File res = new File(dir, "res");
        if (res.isDirectory()) {
            entry.put("resPath", res.getAbsolutePath());
        }
        File assets = new File(dir, "assets");
        if (assets.isDirectory()) {
            entry.put("assetsPath", assets.getAbsolutePath());
        }
        builder.mll.list.add(entry);
        Log.d(TAG, "Local library registrada para un plugin: " + dir.getName());
    }

    /** Ruta del directorio de clases compiladas del proyecto (utilidad de diagnostico). */
    public static String compiledClassesPath(ProjectBuilder builder) {
        return new FilePathUtil().getPathJava(builder.yq.sc_id);
    }
}
