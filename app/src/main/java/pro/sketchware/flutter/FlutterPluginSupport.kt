package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Soporte de **plugins de Flutter** sin Gradle (carril P, Fase 8).
 *
 * Que hace hoy (verificado con evidencia en el dispositivo):
 * 1. Lee el bloque `flutter: plugin: platforms: android:` del `pubspec.yaml` de **cada paquete
 *    resuelto por pub** y resuelve la cadena de plugins federados (`default_package:`
 *    `path_provider` -> `path_provider_android`, y `implements:`).
 * 2. Genera los dos registrantes de los que depende Flutter:
 *    - `GeneratedPluginRegistrant.java` (equivalente exacto al que escribe `flutter build`; lo busca
 *      `FlutterActivity` por reflexion en `io.flutter.plugins`). Solo para plugins con
 *      `pluginClass:` (codigo Java/Kotlin).
 *    - `dart_plugin_registrant.dart` + un `entrypoint.dart` que llama a `registerPlugins()` antes del
 *      `main()` del usuario. Solo para plugins con `dartPluginClass:`.
 * 3. Expone lo que falta para compilar la parte Android de cada plugin (fuentes, manifests, deps de
 *    Gradle resueltas a AAR) **sin afirmar que funciona**: hoy el fork compila el codigo Dart, pero
 *    la compilacion de las fuentes Java/Kotlin del plugin y el merge de manifests los tiene que hacer
 *    el compilador del fork (ECJ / kotlinc, que ya van en `app/build.gradle`).
 *
 * Estado real de los plugins probados en el emulador (pub cache, 2026-09-23):
 * - `path_provider_android 2.3.1`: **no tiene ni Java ni Kotlin**; declara solo
 *   `dartPluginClass: PathProviderAndroid` y depende de `jni`/`jni_flutter`. Su parte Android es
 *   Dart+JNI y necesita `libdartjni.so`, que el paquete `jni` **no publica precompilado** (hay que
 *   construirlo con el NDK). Bloqueado sin NDK.
 * - `shared_preferences_android 2.4.28`: Kotlin (paquete `io.flutter.plugins.sharedpreferences`
 *   dentro de `android/src/main/kotlin`), manifest propio y deps de Gradle `androidx.datastore`,
 *   `androidx.datastore-preferences`, `androidx.preference`. Compilable con el kotlinc del fork,
 *   pero necesita resolver esos 3 AAR y hacer merge del manifest.
 */
object FlutterPluginSupport {

    private const val TAG = "FlutterPluginSupport"

    /** Paquete Java del registrant, el que `FlutterActivity` busca por reflexion. */
    const val REGISTRANT_PACKAGE = "io.flutter.plugins"

    /** Ruta del paquete en forma de directorios. */
    const val REGISTRANT_PACKAGE_PATH = "io/flutter/plugins"

    /** Nombre de clase que busca el embedding (`FlutterActivityAndFragmentDelegate`). */
    const val REGISTRANT_CLASS = "GeneratedPluginRegistrant"

    /**
     * Plugin Android detectado. Todos los campos son `null`-ables a proposito: un plugin puede tener
     * solo parte Dart (`dartPluginClass`) o solo parte nativa (`pluginClass`).
     */
    class AndroidPlugin(
        /** Paquete que declara el plugin (`path_provider`, `shared_preferences`). */
        val pluginName: String,
        /** Paquete que trae la implementacion (`path_provider_android`); == pluginName si es directa. */
        val implementationName: String,
        /** `package:` del bloque android (`io.flutter.plugins.pathprovider`). */
        val androidPackage: String?,
        /** `pluginClass:` (clase Java/Kotlin registrada en el engine). */
        val pluginClass: String?,
        /** `dartPluginClass:` (clase Dart con `registerWith()`). */
        val dartPluginClass: String?,
        /** Directorio raiz del paquete de implementacion en el pub cache. */
        val packageDirectory: File,
        val javaSources: List<File>,
        val kotlinSources: List<File>,
        val androidManifestFiles: List<File>,
    ) {
        /** Nombre Java completo del plugin nativo (`io.flutter.plugins.pathprovider.PathProviderPlugin`). */
        val fullyQualifiedPluginClass: String?
            get() {
                val pkg = androidPackage
                val clazz = pluginClass
                return if (pkg.isNullOrBlank() || clazz.isNullOrBlank()) null else "$pkg.$clazz"
            }

        /** `true` si no hay nada que compilar en Java/Kotlin (solo registro Dart). */
        val isDartOnly: Boolean get() = pluginClass == null

        /** `true` si trae fuentes Kotlin (necesita kotlinc; el fork lo lleva empaquetado). */
        val hasKotlin: Boolean get() = kotlinSources.isNotEmpty()

        override fun toString(): String =
            "$pluginName -> $implementationName" +
                (fullyQualifiedPluginClass?.let { " ($it)" } ?: " (solo Dart)")
    }

    /* -------------------------------------------------------------------------------------- */
    /* Deteccion                                                                                */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Plugins Android de un proyecto, leyendo los `pubspec.yaml` de los paquetes resueltos.
     *
     * @param packageConfigFile `.dart_tool/package_config.json` (real de pub o sintetico; el formato
     *   es el mismo). Si no existe, se devuelve una lista vacia.
     */
    @JvmStatic
    fun detectAndroidPlugins(flutterRoot: File, packageConfigFile: File): List<AndroidPlugin> {
        val packages = FlutterPubResolver.readResolvedPackages(packageConfigFile)
        if (packages.isEmpty()) {
            return emptyList()
        }

        val declarations = HashMap<String, PluginDeclaration>()
        for (pkg in packages) {
            val pubspec = File(pkg.rootDirectory, "pubspec.yaml")
            val declaration = parsePluginDeclaration(pubspec) ?: continue
            declarations[pkg.name] = declaration.copy(packageDirectory = pkg.rootDirectory)
        }

        val plugins = LinkedHashMap<String, AndroidPlugin>()
        for ((name, declaration) in declarations) {
            val android = declaration.android ?: continue

            // Cadena de plugins federados:
            // - `implements: X`   -> este paquete ES la implementacion (path_provider_android); el
            //   plugin de cara al usuario es X.
            // - `default_package: Y` -> este paquete es la fachada (path_provider) y el codigo vive
            //   en Y.
            // - ni uno ni otro -> plugin monolitico (nombre == implementacion).
            val pluginName: String
            val implementationName: String
            when {
                !declaration.implements.isNullOrBlank() -> {
                    pluginName = declaration.implements
                    implementationName = name
                }

                !android.defaultPackage.isNullOrBlank() -> {
                    pluginName = name
                    implementationName = android.defaultPackage!!
                }

                else -> {
                    pluginName = name
                    implementationName = name
                }
            }

            // Deduplicar por implementacion: la fachada y su implementacion son el MISMO plugin
            // Android (si no, se registraria dos veces en `GeneratedPluginRegistrant`).
            if (plugins.containsKey(implementationName)) {
                continue
            }

            val implementationDeclaration = declarations[implementationName]
            val implementationAndroid = if (implementationName == name) {
                android
            } else {
                implementationDeclaration?.android
            }
            if (implementationAndroid == null) {
                Log.w(
                    TAG,
                    "El plugin $name apunta a $implementationName, que no esta resuelto por pub",
                )
                continue
            }
            val directory = implementationDeclaration?.packageDirectory
                ?: declaration.packageDirectory
            plugins[implementationName] = AndroidPlugin(
                pluginName = pluginName,
                implementationName = implementationName,
                androidPackage = implementationAndroid.packageName,
                pluginClass = implementationAndroid.pluginClass,
                dartPluginClass = implementationAndroid.dartPluginClass,
                packageDirectory = directory,
                javaSources = collectSources(directory, "java"),
                kotlinSources = collectSources(directory, "kotlin"),
                androidManifestFiles = collectManifests(directory),
            )
        }
        return plugins.values.toList()
    }

    /** Compatibilidad: detecta los plugins del proyecto usando su `package_config.json`. */
    @JvmStatic
    fun detectAndroidPlugins(flutterRoot: File): List<AndroidPlugin> =
        detectAndroidPlugins(flutterRoot, FlutterToolchainPaths.packageConfigFile(flutterRoot))

    /** Fuentes `.java` (o `.kt`) bajo `<paquete>/android/src/main/<lenguaje>`. */
    @JvmStatic
    fun collectSources(packageDirectory: File, language: String): List<File> {
        val root = File(packageDirectory, "android/src/main/$language")
        if (!root.isDirectory) {
            return emptyList()
        }
        val extension = if (language == "kotlin") "kt" else "java"
        return root.walkTopDown()
            .filter { it.isFile && it.extension == extension }
            .sortedBy { it.absolutePath }
            .toList()
    }

    /** Manifests de los plugins (para el merge de manifests, carril A2). */
    @JvmStatic
    fun collectManifests(packageDirectory: File): List<File> {
        val manifest = File(packageDirectory, "android/src/main/AndroidManifest.xml")
        return if (manifest.isFile) listOf(manifest) else emptyList()
    }

    /* -------------------------------------------------------------------------------------- */
    /* Registrantes                                                                             */
    /* -------------------------------------------------------------------------------------- */

    /**
     * `GeneratedPluginRegistrant.java`, con la misma forma que el que escribe `flutter build`:
     * un `try/catch` por plugin para que un plugin que falla no impida registrar los demas.
     */
    @JvmStatic
    fun generateGeneratedPluginRegistrant(plugins: List<AndroidPlugin>): String {
        val native = plugins.filter { it.fullyQualifiedPluginClass != null }
        val builder = StringBuilder()
        builder.append("package ").append(REGISTRANT_PACKAGE).append(";\n\n")
        builder.append("import androidx.annotation.Keep;\n")
        builder.append("import androidx.annotation.NonNull;\n")
        builder.append("import io.flutter.Log;\n")
        builder.append("import io.flutter.embedding.engine.FlutterEngine;\n\n")
        builder.append("// Generado por Sketchware-Pro (carril P, Fase 8). No editar a mano.\n")
        builder.append("@Keep\n")
        builder.append("public final class ").append(REGISTRANT_CLASS).append(" {\n")
        builder.append("  private static final String TAG = \"").append(REGISTRANT_CLASS).append("\";\n\n")
        builder.append("  private ").append(REGISTRANT_CLASS).append("() {}\n\n")
        builder.append("  public static void registerWith(@NonNull FlutterEngine flutterEngine) {\n")
        for (plugin in native) {
            val className = plugin.fullyQualifiedPluginClass
            val tag = "${plugin.pluginName}, $className"
            builder.append("    try {\n")
            builder.append("      flutterEngine.getPlugins().add(new ").append(className).append("());\n")
            builder.append("    } catch (Exception e) {\n")
            builder.append("      Log.e(TAG, \"Error registering plugin ").append(tag)
                .append("\", e);\n")
            builder.append("    }\n")
        }
        builder.append("  }\n")
        builder.append("}\n")
        return builder.toString()
    }

    /**
     * `dart_plugin_registrant.dart`: llama a `registerWith()` de cada plugin con `dartPluginClass`.
     * Es el mismo mecanismo que usa el tool de Flutter para los plugins federados.
     */
    @JvmStatic
    fun generateDartPluginRegistrant(plugins: List<AndroidPlugin>): String {
        val dartPlugins = plugins.filter { !it.dartPluginClass.isNullOrBlank() }
        val builder = StringBuilder()
        builder.append("// Generado por Sketchware-Pro (carril P, Fase 8). No editar a mano.\n")
        builder.append("// ignore_for_file: type=lint\n")
        builder.append("import 'dart:async';\n")
        for (plugin in dartPlugins) {
            builder.append("import 'package:").append(plugin.implementationName).append('/')
                .append(plugin.implementationName).append(".dart' as ")
                .append(plugin.implementationName).append(";\n")
        }
        builder.append('\n')
        builder.append("@pragma('vm:entry-point')\n")
        builder.append("Future<void> registerPlugins() async {\n")
        for (plugin in dartPlugins) {
            builder.append("  ").append(plugin.implementationName).append('.')
                .append(plugin.dartPluginClass).append(".registerWith();\n")
        }
        builder.append("}\n")
        return builder.toString()
    }

    /**
     * `entrypoint.dart`: registra los plugins Dart y llama al `main()` del usuario.
     *
     * Es lo que compila `gen_kernel` cuando hay plugins con `dartPluginClass`. Se llama a
     * `app_entrypoint.main()` **sin** `await` a proposito: asi vale tanto si el `main()` del usuario
     * devuelve `void` como si devuelve `Future<void>` (con `await` un `main` que devuelve `void` no
     * compilaria).
     */
    @JvmStatic
    fun generateEntrypoint(projectPackageName: String, hasDartPlugins: Boolean): String {
        return buildString {
            append("// Generado por Sketchware-Pro (carril P, Fase 8). No editar a mano.\n")
            append("// ignore_for_file: type=lint\n")
            if (hasDartPlugins) {
                append("import 'dart_plugin_registrant.dart' as plugin_registrant;\n")
            }
            append("import 'package:").append(projectPackageName).append("/main.dart' as app_entrypoint;\n\n")
            append("Future<void> main() async {\n")
            if (hasDartPlugins) {
                append("  await plugin_registrant.registerPlugins();\n")
            }
            append("  app_entrypoint.main();\n")
            append("}\n")
        }
    }

    /**
     * Escribe los registrantes que hagan falta y devuelve el **fichero de entrada** que hay que
     * compilar (`.dart_tool/flutter_build/entrypoint.dart` si hay plugins Dart; si no, `lib/main.dart`).
     *
     * Nunca lanza: si algo falla se registra en [log] y se sigue con `lib/main.dart`, que es el
     * comportamiento previo a esta fase (un proyecto sin plugins no cambia en nada).
     */
    @JvmStatic
    fun prepareEntrypoint(
        context: Context,
        flutterRoot: File,
        progress: (String) -> Unit,
        log: StringBuilder,
    ): File {
        val defaultEntrypoint = File(flutterRoot, "lib/main.dart")
        val plugins = try {
            detectAndroidPlugins(flutterRoot)
        } catch (e: Exception) {
            log.append("[warn] no se pudieron leer los plugins: ").append(e.message).append('\n')
            emptyList()
        }
        if (plugins.isEmpty()) {
            log.append("[ok] sin plugins Android en el proyecto\n")
            return defaultEntrypoint
        }

        log.append("[ok] plugins Android detectados: ").append(plugins.size).append('\n')
        for (plugin in plugins) {
            log.append("     - ").append(plugin.toString())
                .append(" [java=").append(plugin.javaSources.size)
                .append(", kotlin=").append(plugin.kotlinSources.size)
                .append("]")
                .append('\n')
        }

        val buildDir = FlutterToolchainPaths.flutterBuildDir(flutterRoot)
        if (!buildDir.mkdirs() && !buildDir.isDirectory) {
            log.append("[warn] no se pudo crear ").append(buildDir.absolutePath).append('\n')
            return defaultEntrypoint
        }

        val nativePlugins = plugins.filter { it.fullyQualifiedPluginClass != null }
        if (nativePlugins.isNotEmpty()) {
            val registrantDir = File(buildDir, REGISTRANT_PACKAGE_PATH)
            registrantDir.mkdirs()
            val registrantFile = File(registrantDir, "$REGISTRANT_CLASS.java")
            registrantFile.writeText(generateGeneratedPluginRegistrant(plugins))
            log.append("[ok] ").append(registrantFile.absolutePath).append('\n')
            for (line in compilationPlan(plugins)) {
                log.append("     ").append(line).append('\n')
            }
        }

        val dartPlugins = plugins.filter { !it.dartPluginClass.isNullOrBlank() }
        if (dartPlugins.isEmpty()) {
            return defaultEntrypoint
        }

        FlutterToolchainPaths.dartPluginRegistrantFile(flutterRoot)
            .writeText(generateDartPluginRegistrant(plugins))
        val projectName = FlutterDartCompiler.readPubspecName(flutterRoot) ?: "flutter_app"
        val entrypoint = FlutterToolchainPaths.pluginEntrypointFile(flutterRoot)
        entrypoint.writeText(generateEntrypoint(projectName, hasDartPlugins = true))
        progress("Plugins: ${dartPlugins.size} con registro Dart")
        log.append("[ok] entrada de compilacion: ").append(entrypoint.absolutePath).append('\n')
        return entrypoint
    }

    /**
     * Plan de compilacion (una linea por plugin) para el log y para el carril que compile Java/Kotlin.
     *
     * Es un **plan**, no una promesa: hoy el fork compila el Dart y deja el registro listo; la
     * compilacion de las fuentes Java/Kotlin del plugin, sus dependencias (AAR) y el merge de su
     * manifest los tiene que hacer el compilador del fork.
     */
    @JvmStatic
    fun compilationPlan(plugins: List<AndroidPlugin>): List<String> {
        val plan = mutableListOf<String>()
        for (plugin in plugins) {
            if (plugin.fullyQualifiedPluginClass == null) {
                plan.add("${plugin.implementationName}: sin codigo Java/Kotlin (solo registro Dart)")
                continue
            }
            plan.add(
                "${plugin.implementationName}: ${plugin.javaSources.size} fuentes Java + " +
                    "${plugin.kotlinSources.size} Kotlin, ${plugin.androidManifestFiles.size} manifest(s) " +
                    "-> registrar ${plugin.fullyQualifiedPluginClass}"
            )
            if (plugin.kotlinSources.isNotEmpty()) {
                plan.add(
                    "  requiere kotlinc (el fork ya empaqueta `libs.bundles.kotlin.compiler`) y sus " +
                        "dependencias Android del `android/build.gradle` resueltas a AAR"
                )
            }
            for (manifest in plugin.androidManifestFiles) {
                plan.add("  manifest a fusionar: ${manifest.absolutePath}")
            }
        }
        return plan
    }

    /**
     * Dependencias de Gradle declaradas por un plugin (`implementation("grupo:artefacto:version")`).
     *
     * Se leen del `android/build.gradle`/`build.gradle.kts` del paquete de pub: son las que habria que
     * resolver a AAR para compilar su parte Android (p. ej. `androidx.datastore`, `androidx.preference`
     * en `shared_preferences_android`). El fork **no** ejecuta Gradle, asi que la resolucion la haria
     * su resolvedor de dependencias.
     */
    @JvmStatic
    fun readGradleDependencies(packageDirectory: File): List<String> {
        val candidates = listOf("android/build.gradle.kts", "android/build.gradle")
            .map { File(packageDirectory, it) }
            .filter { it.isFile }
        val regex = Regex("(?:implementation|api|compileOnly)\\s*\\(?\\s*[\"']([^\"']+)[\"']")
        val dependencies = LinkedHashSet<String>()
        for (file in candidates) {
            for (match in regex.findAll(file.readText())) {
                dependencies.add(match.groupValues[1])
            }
        }
        return dependencies.toList().sorted()
    }

    /* -------------------------------------------------------------------------------------- */
    /* Parser del bloque `flutter: plugin:` del pubspec                                         */
    /* -------------------------------------------------------------------------------------- */

    /** Bloque `android:` de un plugin en el pubspec. */
    class AndroidPlatformDeclaration(
        val packageName: String?,
        val pluginClass: String?,
        val dartPluginClass: String?,
        val defaultPackage: String?,
    )

    /** Declaracion `flutter: plugin:` de un pubspec (solo lo que usa Android). */
    data class PluginDeclaration(
        val implements: String?,
        val android: AndroidPlatformDeclaration?,
        var packageDirectory: File = File("."),
    )

    /**
     * Lee `flutter: plugin: platforms: android:` del `pubspec.yaml` indicado.
     *
     * Parser minimo (sin dependencias YAML) sobre la estructura real, verificada en el emulador:
     * ```yaml
     * flutter:
     *   plugin:
     *     implements: path_provider        # solo en la implementacion federada
     *     platforms:
     *       android:
     *         package: io.flutter.plugins.pathprovider
     *         pluginClass: PathProviderPlugin
     *         dartPluginClass: PathProviderAndroid
     *         default_package: path_provider_android   # solo en el paquete de fachada
     * ```
     * Devuelve `null` si el paquete no es un plugin o no declara plataforma Android (un plugin solo iOS
     * o solo web no aporta nada al APK Android).
     */
    @JvmStatic
    fun parsePluginDeclaration(pubspec: File): PluginDeclaration? {
        if (!pubspec.isFile) {
            return null
        }
        var state = State.SEARCH_FLUTTER
        var flutterIndent = -1
        var pluginIndent = -1
        var platformsIndent = -1
        var androidIndent = -1
        var implements: String? = null
        var packageName: String? = null
        var pluginClass: String? = null
        var dartPluginClass: String? = null
        var defaultPackage: String? = null
        var sawAndroid = false

        for (raw in pubspec.readLines()) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) {
                continue
            }
            val line = raw.trimEnd()
            val indent = line.length - line.trimStart().length
            val content = line.trimStart()

            when (state) {
                State.SEARCH_FLUTTER -> {
                    if (indent == 0 && content.startsWith("flutter:")) {
                        state = State.IN_FLUTTER
                        flutterIndent = indent
                    }
                }

                State.IN_FLUTTER -> {
                    if (indent <= flutterIndent) {
                        state = State.SEARCH_FLUTTER
                        continue
                    }
                    if (content.startsWith("plugin:")) {
                        state = State.IN_PLUGIN
                        pluginIndent = indent
                    }
                }

                State.IN_PLUGIN -> {
                    if (indent <= pluginIndent) {
                        state = State.IN_FLUTTER
                        continue
                    }
                    if (content.startsWith("implements:")) {
                        implements = content.substringAfter(':').trim().trim('"', '\'')
                    } else if (content.startsWith("platforms:")) {
                        state = State.IN_PLATFORMS
                        platformsIndent = indent
                    }
                }

                State.IN_PLATFORMS -> {
                    if (indent <= platformsIndent) {
                        state = State.IN_PLUGIN
                        continue
                    }
                    if (content.startsWith("android:")) {
                        state = State.IN_ANDROID
                        androidIndent = indent
                        sawAndroid = true
                    }
                }

                State.IN_ANDROID -> {
                    if (indent <= androidIndent) {
                        state = State.IN_PLATFORMS
                        if (indent <= platformsIndent) {
                            state = State.IN_PLUGIN
                        }
                        continue
                    }
                    val value = content.substringAfter(':', "").trim().trim('"', '\'')
                    when (content.substringBefore(':').trim()) {
                        "package" -> packageName = value
                        "pluginClass" -> pluginClass = value
                        "dartPluginClass" -> dartPluginClass = value
                        "default_package" -> defaultPackage = value
                    }
                }
            }
        }

        if (!sawAndroid) {
            return null
        }
        return PluginDeclaration(
            implements = implements,
            android = AndroidPlatformDeclaration(packageName, pluginClass, dartPluginClass, defaultPackage),
        )
    }

    private enum class State { SEARCH_FLUTTER, IN_FLUTTER, IN_PLUGIN, IN_PLATFORMS, IN_ANDROID }
}
