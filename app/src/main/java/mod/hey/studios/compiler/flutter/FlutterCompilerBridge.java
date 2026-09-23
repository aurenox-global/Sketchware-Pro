package mod.hey.studios.compiler.flutter;

import android.content.Context;
import android.util.Log;

import java.io.File;

import a.a.a.ProjectBuilder;
import a.a.a.yq;
import pro.sketchware.featureflags.FeatureFlags;
import pro.sketchware.flutter.FlutterBuildMode;
import pro.sketchware.flutter.FlutterDartCompileResult;
import pro.sketchware.flutter.FlutterDartCompiler;
import pro.sketchware.flutter.FlutterPackagingSupport;
import pro.sketchware.flutter.FlutterProject;
import pro.sketchware.flutter.FlutterProjectDefaults;
import pro.sketchware.flutter.FlutterProjectStore;
import pro.sketchware.flutter.FlutterToolchainManager;

/**
 * Punto de entrada del build Flutter/Dart dentro del pipeline Android del fork
 * (carril C, Fase 7). Espejo de {@code mod.hey.studios.compiler.kotlin.KotlinCompilerBridge}.
 *
 * <p>Se invoca desde {@code DesignActivity} y {@code ExportProjectActivity}
 * <b>antes de</b> {@code builder.compileResources()}: los assets de Flutter
 * ({@code flutter_assets/}) se enlazan con AAPT2 mediante {@code -A}
 * ({@code mod/jbk/build/compiler/resource/ResourceCompiler.java:183}), asi que tienen que
 * existir en disco en ese momento. Las nativas y el embedding se inyectan en el mismo paso
 * y los recogen fases posteriores ({@code buildApk}/{@code getClasspath}).
 *
 * <p>Deteccion de "es un proyecto Flutter": igual que Kotlin, por ficheros
 * ({@code pubspec.yaml} en {@code <proyecto>/files/flutter/}), no por un enum de lenguaje.
 */
public class FlutterCompilerBridge {

    private static final String TAG = "FlutterCompilerBridge";

    /**
     * @return {@code true} si el proyecto tiene un scaffold Flutter ({@code pubspec.yaml}).
     */
    public static boolean isFlutterProject(ProjectBuilder builder) {
        if (builder == null || builder.yq == null) {
            return false;
        }
        File flutterRoot = flutterRootDirectory(builder.yq);
        return new File(flutterRoot, "pubspec.yaml").isFile();
    }

    /** Raiz del proyecto Flutter: {@code <mysc>/files/flutter}. */
    public static File flutterRootDirectory(yq workspace) {
        if (workspace.getFlutterRootDirectory() != null) {
            return workspace.getFlutterRootDirectory();
        }
        return new File(workspace.projectMyscPath + "files" + File.separator + "flutter");
    }

    /**
     * Compila el Dart del proyecto (si es Flutter) y deja los artefactos listos para AAPT2/D8.
     *
     * <p>No lanza excepciones: cualquier fallo se registra y devuelve {@code false} para que el
     * build Android continue (el APK saldra sin Flutter, pero se puede ver el motivo en el log).
     *
     * @return {@code true} si el proyecto no era Flutter o si el staging termino bien.
     */
    public static boolean compileFlutterCodeIfPossible(Context context, ProjectBuilder builder) {
        if (!isFlutterProject(builder)) {
            return true;
        }

        if (context != null && !FeatureFlags.isEnabled(context, FeatureFlags.Key.FLUTTER_EXPERIMENTAL_ENABLE)) {
            Log.d(TAG, "FLUTTER_EXPERIMENTAL_ENABLE desactivado; se omite el build Flutter");
            return true;
        }

        File flutterRoot = flutterRootDirectory(builder.yq);
        try {
            File projectFilesDir = new File(builder.yq.projectMyscPath, "files");
            FlutterProject project = FlutterProjectStore.load(projectFilesDir);
            // Por defecto DEBUG_JIT: es el unico modo que la prueba E2E pudo arrancar en el
            // dispositivo (engine debug + kernel_blob.bin). RELEASE_AOT falla a proposito mas
            // abajo, con un mensaje que explica el por que (gen_snapshot sin compressed pointers).
            FlutterBuildMode mode = project != null
                    ? project.getMode()
                    : FlutterProjectDefaults.DEFAULT_MODE;

            Log.d(TAG, "Proyecto Flutter detectado en " + flutterRoot.getAbsolutePath() + " (modo " + mode + ")");

            // El toolchain se pide PARA EL MODO: el engine debug y el release son artefactos
            // distintos, y de aqui tambien sale el framework Dart + sus dependencias de pub.
            if (!FlutterToolchainManager.ensureInstalled(context, mode, message -> {
                Log.d(TAG, message);
                return kotlin.Unit.INSTANCE;
            })) {
                Log.w(TAG, "Toolchain Flutter no disponible (modo " + mode + "); se omite el build Dart");
                Log.w(TAG, "Instala/actualiza el toolchain desde el menu Flutter del editor "
                        + "(Flutter: estado del toolchain) y vuelve a compilar.");
                return false;
            }

            FlutterDartCompileResult result = FlutterDartCompiler.compile(
                    context, flutterRoot, mode, message -> {
                Log.d(TAG, message);
                return kotlin.Unit.INSTANCE;
            });

            if (!result.getLog().isEmpty()) {
                Log.d(TAG, result.getLog());
            }

            if (!result.getSuccess()) {
                Log.e(TAG, "La compilacion Dart fallo; se continua sin Flutter");
                return false;
            }

            return deployFlutterArtifacts(context, builder, result);
        } catch (Throwable t) {
            Log.e(TAG, "Fallo inesperado compilando Flutter", t);
            return false;
        }
    }

    /**
     * Variante para el orquestador: el resultado de Dart ya se calculo y solo falta inyectarlo.
     */
    public static boolean compileFlutterCodeIfPossible(
            Context context,
            ProjectBuilder builder,
            FlutterDartCompileResult dartResult) {
        if (!isFlutterProject(builder)) {
            return true;
        }
        if (dartResult == null || !dartResult.getSuccess()) {
            Log.e(TAG, "Sin resultado de compilacion Dart valido");
            return false;
        }
        try {
            return deployFlutterArtifacts(context, builder, dartResult);
        } catch (Throwable t) {
            Log.e(TAG, "Fallo inyectando artefactos Flutter", t);
            return false;
        }
    }

    /**
     * Inyecta manifest, nativas, assets, embedding y reglas R8 en el builder.
     *
     * @return {@code true} si todo lo importante quedo colocado.
     */
    public static boolean deployFlutterArtifacts(
            Context context,
            ProjectBuilder builder,
            FlutterDartCompileResult dartResult) {
        boolean ok = true;

        /* 1) Manifest: FlutterActivity como launcher + flutterEmbedding=2 + extractNativeLibs. */
        if (!FlutterPackagingSupport.injectFlutterManifest(new File(builder.yq.androidManifestPath))) {
            Log.w(TAG, "No se pudo inyectar FlutterActivity en el AndroidManifest.xml");
            ok = false;
        }

        /* 2) Nativa libflutter.so (+ libapp.so en AOT) en jniLibs del proyecto. El modo decide
              cual de los dos engines se copia. */
        File libAppSo = dartResult.getLibAppSoPath() != null ? new File(dartResult.getLibAppSoPath()) : null;
        if (!FlutterPackagingSupport.stageNativeLibraries(
                context,
                new File(builder.fpu.getPathNativelibs(builder.yq.sc_id)),
                libAppSo,
                dartResult.getMode())) {
            Log.w(TAG, "No se pudieron colocar las nativas de Flutter");
            ok = false;
        }

        /* 3) flutter_assets/ en el dir de assets que enlaza AAPT2 (-A). */
        if (dartResult.getFlutterAssetsDir() != null) {
            if (!FlutterPackagingSupport.stageFlutterAssets(
                    new File(dartResult.getFlutterAssetsDir()), new File(builder.yq.assetsPath))) {
                Log.w(TAG, "No se pudieron colocar los flutter_assets");
                ok = false;
            }
        }

        /* 4) Embedding: clases al compiledClassesPath (las dexea D8) + jar para el classpath.
              El jar del modo es lo que hace que FlutterLoader elija JIT o AOT. */
        int classes = FlutterPackagingSupport.prepareEmbeddingClasses(
                context, builder.yq.binDirectoryPath, builder.yq.compiledClassesPath, dartResult.getMode());
        if (classes <= 0) {
            Log.w(TAG, "No se pudo preparar el embedding de Flutter (clases=" + classes + ")");
            ok = false;
        }

        /* 5) R8: las clases del embedding entran por reflexion/JNI, hay que conservarlas. */
        if (builder.proguard != null && builder.proguard.isShrinkingEnabled()) {
            FlutterPackagingSupport.appendFlutterKeepRules(
                    new File(builder.proguard.getCustomProguardRules()));
        }

        Log.d(TAG, "Artefactos Flutter inyectados en el builder (ok=" + ok + ")");
        return ok;
    }

    /**
     * Anade el jar del embedding Flutter al classpath del proyecto (ecj y D8).
     *
     * <p>Patron identico a
     * {@code KotlinCompilerBridge.maybeAddKotlinFilesToClasspath(StringBuilder, yq)}.
     */
    public static void maybeAddFlutterEmbeddingToClasspath(StringBuilder classpath, yq workspace) {
        File jar = FlutterPackagingSupport.embeddingJarForClasspath(workspace.binDirectoryPath);
        if (jar.isFile()) {
            classpath.append(":").append(jar.getAbsolutePath());
        }
    }
}
