package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import pro.sketchware.flutter.FlutterToolchainPaths.RemoteArtifact
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Empaquetado del proyecto Flutter dentro del pipeline Android del fork (carril C).
 *
 * Estas funciones son las que llama [FlutterCompilerBridge] (Java) desde
 * `DesignActivity`/`ExportProjectActivity`/`ProjectBuilder`:
 *
 * - [injectFlutterManifest]: reescribe el `AndroidManifest.xml` generado
 *   (`yq.androidManifestPath`, escrito desde `yq.a(String,String)` en `a/a/a/yq.java:466`)
 *   para declarar `io.flutter.embedding.android.FlutterActivity` como launcher y
 *   `flutterEmbedding=2`.
 * - [stageNativeLibraries]: copia `libflutter.so` + `libapp.so` a
 *   `FilePathUtil.getPathNativelibs(sc_id)/<abi>/` (lo que `ProjectBuilder.buildApk()` mete
 *   en el APK vía `apkBuilder.addNativeLibraries`, `a/a/a/ProjectBuilder.java:666`).
 * - [stageFlutterAssets]: copia `flutter_assets/` bajo el dir de assets que AAPT2 enlaza con
 *   `-A` (`yq.assetsPath`, `mod/jbk/build/compiler/resource/ResourceCompiler.java:183`).
 * - [prepareEmbeddingClasses]: extrae `io.flutter.**` del jar del embedding al
 *   `compiledClassesPath`, que es lo que D8 usa como *program files*
 *   (`mod/jbk/build/compiler/dex/DexCompiler.java:33`).
 */
object FlutterPackagingSupport {

    private const val TAG = "FlutterPackaging"

    const val FLUTTER_ACTIVITY = "io.flutter.embedding.android.FlutterActivity"
    private const val FLUTTER_EMBEDDING_META_DATA =
        "<meta-data android:name=\"flutterEmbedding\" android:value=\"2\" />"
    private const val LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"

    private val APPLICATION_OPEN_TAG = Regex("<application\\b[^>]*>")
    private val ACTIVITY_BLOCK = Regex("<activity\\b[^>]*>[\\s\\S]*?</activity>")
    private val ACTIVITY_NAME_ATTR = Regex("android:name=\"[^\"]*\"")

    /* -------------------------------------------------------------------------------------- */
    /* Manifest                                                                                 */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Inyecta la `FlutterActivity` y el meta-data del embedding en el manifest ya generado.
     *
     * Es **idempotente**: si el manifest ya declara [FLUTTER_ACTIVITY] no hace nada.
     *
     * @return `true` si el manifest quedó con la declaración Flutter.
     */
    @JvmStatic
    fun injectFlutterManifest(manifestFile: File): Boolean {
        if (!manifestFile.isFile) {
            Log.w(TAG, "No existe el manifest generado: ${manifestFile.absolutePath}")
            return false
        }

        val original = manifestFile.readText()
        if (original.contains(FLUTTER_ACTIVITY)) {
            return true
        }

        var manifest = original

        // 1) android:extractNativeLibs="true" en <application> (libflutter.so/libapp.so se
        //    cargan desde el APK; sin extracción el linker de Android necesita page-alignment).
        val applicationMatch = APPLICATION_OPEN_TAG.find(manifest)
        if (applicationMatch != null) {
            val openTag = applicationMatch.value
            if (!openTag.contains("extractNativeLibs")) {
                val patched = openTag.dropLast(1) + " android:extractNativeLibs=\"true\">"
                manifest = manifest.replaceRange(applicationMatch.range, patched)
            }
        } else {
            Log.w(TAG, "Manifest sin <application>: no se pudo anadir extractNativeLibs")
        }

        // 2) Reapuntar la activity launcher existente a FlutterActivity: evita DOS iconos de
        //    launcher (dos intent-filter MAIN/LAUNCHER) que es lo que pasaria si simplemente
        //    anadiesemos una activity nueva.
        var repointed = false
        manifest = ACTIVITY_BLOCK.replace(manifest) { match ->
            val block = match.value
            if (!block.contains(LAUNCHER_CATEGORY)) {
                block
            } else {
                repointed = true
                val openTagEnd = block.indexOf('>')
                val openTag = block.substring(0, openTagEnd)
                val patchedOpenTag = openTag.replaceFirst(ACTIVITY_NAME_ATTR, "android:name=\"$FLUTTER_ACTIVITY\"")
                patchedOpenTag + block.substring(openTagEnd)
            }
        }

        // 3) Si el proyecto no tenia launcher (raro), se anade la activity completa.
        val activityDeclaration = if (repointed) {
            ""
        } else {
            """
            <activity android:name="$FLUTTER_ACTIVITY"
                android:exported="true"
                android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|density|uiMode"
                android:hardwareAccelerated="true"
                android:windowSoftInputMode="adjustResize">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            </activity>
            """.trimIndent()
        }

        val applicationClose = manifest.lastIndexOf("</application>")
        if (applicationClose < 0) {
            Log.w(TAG, "Manifest sin </application>: no se pudo inyectar FlutterActivity")
            return false
        }
        val injection = buildString {
            append('\n')
            append(FLUTTER_EMBEDDING_META_DATA)
            append('\n')
            if (activityDeclaration.isNotEmpty()) {
                append(activityDeclaration)
                append('\n')
            }
        }
        manifest = manifest.substring(0, applicationClose) + injection + manifest.substring(applicationClose)

        return try {
            manifestFile.writeText(manifest)
            Log.d(TAG, "Manifest Flutter inyectado (launcher reapuntado=$repointed)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo escribir el manifest", e)
            false
        }
    }

    /* -------------------------------------------------------------------------------------- */
    /* Nativas y assets                                                                          */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Copia `libflutter.so` + `libapp.so` a `<nativeLibsDir>/<abi>/`.
     *
     * El `libflutter.so` que se copia depende del [mode]: el engine **debug** (395 MB) es el que
     * carga `kernel_blob.bin`; el release (165 MB) solo sirve con `libapp.so`.
     *
     * @return `true` si ambas quedaron colocadas.
     */
    @JvmStatic
    fun stageNativeLibraries(
        context: Context,
        nativeLibrariesDirectory: File,
        libAppSo: File?,
        mode: FlutterBuildMode,
    ): Boolean {
        // Fallback: la ABI real del dispositivo (nunca "arm64-v8a" por defecto: en un APK x86_64
        // eso colocaria `libflutter.so` en el directorio equivocado).
        val abi = FlutterToolchainPaths.resolveSupportedAbi() ?: FlutterToolchainPaths.deviceAbiName()
        val abiDir = File(nativeLibrariesDirectory, abi)
        if (!abiDir.mkdirs() && !abiDir.isDirectory) {
            Log.w(TAG, "No se pudo crear ${abiDir.absolutePath}")
            return false
        }

        val libFlutter = FlutterToolchainPaths.libFlutterSo(context, mode)
        if (!libFlutter.isFile) {
            Log.w(TAG, "Falta ${libFlutter.name} en el toolchain (modo $mode)")
            return false
        }
        libFlutter.copyTo(File(abiDir, "libflutter.so"), overwrite = true)

        if (libAppSo != null) {
            if (!libAppSo.isFile) {
                Log.w(TAG, "No existe ${libAppSo.absolutePath}")
                return false
            }
            libAppSo.copyTo(File(abiDir, "libapp.so"), overwrite = true)
        }

        Log.d(TAG, "Nativas Flutter colocadas en ${abiDir.absolutePath} (modo $mode)")
        return true
    }

    /**
     * Compatibilidad: deduce el modo del resultado (`libapp.so` presente -> AOT, si no -> JIT).
     * Mejor pasar el modo explicito ([FlutterDartCompileResult.getMode]).
     */
    @JvmStatic
    fun stageNativeLibraries(
        context: Context,
        nativeLibrariesDirectory: File,
        libAppSo: File?,
    ): Boolean {
        val mode = if (libAppSo != null) FlutterBuildMode.RELEASE_AOT else FlutterBuildMode.DEBUG_JIT
        return stageNativeLibraries(context, nativeLibrariesDirectory, libAppSo, mode)
    }

    /**
     * Copia el arbol `flutter_assets/` a `<targetAssetsDir>/flutter_assets`.
     *
     * Debe ejecutarse **antes** de `ProjectBuilder.compileResources()`, porque AAPT2 enlaza los
     * assets existentes en el momento del `-A` (`ResourceCompiler.java:183`).
     */
    @JvmStatic
    fun stageFlutterAssets(flutterAssetsDir: File, targetAssetsDir: File): Boolean {
        if (!flutterAssetsDir.isDirectory) {
            Log.w(TAG, "No existe ${flutterAssetsDir.absolutePath}")
            return false
        }
        val destination = File(targetAssetsDir, "flutter_assets")
        FlutterToolchainInstaller.deleteRecursively(destination)
        if (!destination.mkdirs() && !destination.isDirectory) {
            Log.w(TAG, "No se pudo crear ${destination.absolutePath}")
            return false
        }
        flutterAssetsDir.copyRecursively(destination, overwrite = true)
        Log.d(TAG, "flutter_assets copiados a ${destination.absolutePath}")
        return true
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun androidArtifactsArtifact(abi: String?): RemoteArtifact? =
        FlutterToolchainPaths.androidArtifactsArtifact(abi, FlutterBuildMode.RELEASE_AOT)
    // NOTA (carril I): el import y la calificacion faltaban. `RemoteArtifact` es una clase anidada
    // de `FlutterToolchainPaths` y `androidArtifactsArtifact(abi, mode)` es miembro **de ella**:
    // sin `FlutterToolchainPaths.` la llamada se resolvia sobre la propia funcion de 1 argumento y
    // el fichero no compilaba (`too many arguments`). Lo detecto el type-check del carril I (el
    // carril que lo escribio no incluia este fichero en el suyo).

    /* -------------------------------------------------------------------------------------- */
    /* Manifests de plugins (carril I, Fase 8)                                                  */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Fusiona los manifests de los plugins en el manifest ya generado del proyecto.
     *
     * Copia **solo** lo que un plugin Android aporta de verdad y no rompe nada:
     * - fuera de `<application>`: `<uses-permission>`, `<permission>`, `<uses-feature>`;
     * - dentro de `<application>`: `<provider>`, `<service>`, `<receiver>`, `<meta-data>`;
     *
     * deduplicando por `android:name` (y, si no lo hubiera, por el texto del bloque). Las
     * `<activity>` de los plugins **no** se copian a proposito: las activities de un plugin se
     * invocan por su propio `Intent` y anadir una al launcher crearia un segundo icono.
     *
     * Es una fusion **minima** y honesta (no un merger real de AGP): no resuelve `tools:node`, ni
     * placeholders `${applicationId}`, ni herencia de atributos de `<application>`. Para el caso que
     * probo el carril P (`shared_preferences_android`: un manifest vacio) es suficiente.
     *
     * @return numero de entradas nuevas anadidas (0 si no habia nada que fusionar).
     */
    @JvmStatic
    fun mergePluginManifests(manifestFile: File, pluginManifests: List<File>): Int {
        if (!manifestFile.isFile || pluginManifests.isEmpty()) {
            return 0
        }
        val original = manifestFile.readText()
        var manifest = original
        var added = 0

        val applicationOpen = Regex("<application\\b[^>]*>").find(manifest)
        val applicationClose = manifest.lastIndexOf("</application>")

        for (pluginManifest in pluginManifests) {
            if (!pluginManifest.isFile) {
                continue
            }
            val content = pluginManifest.readText()

            // 1) Elementos que van al nivel raiz (hermanos de <application>).
            for (tag in ROOT_LEVEL_TAGS) {
                for (block in extractSelfClosingOrBlocks(content, tag)) {
                    if (manifest.contains(block)) {
                        continue
                    }
                    val anchor = applicationOpen?.range?.first ?: -1
                    if (anchor < 0) {
                        continue
                    }
                    manifest = manifest.substring(0, anchor) + block + "\n" + manifest.substring(anchor)
                    added++
                }
            }

            // 2) Elementos que van dentro de <application>.
            for (tag in APPLICATION_LEVEL_TAGS) {
                for (block in extractSelfClosingOrBlocks(content, tag)) {
                    if (manifest.contains(block)) {
                        continue
                    }
                    val close = manifest.lastIndexOf("</application>")
                    if (close < 0) {
                        continue
                    }
                    manifest = manifest.substring(0, close) + block + "\n" + manifest.substring(close)
                    added++
                }
            }
        }

        if (added == 0 || manifest == original) {
            return 0
        }
        return try {
            manifestFile.writeText(manifest)
            Log.d(TAG, "Manifests de plugins fusionados ($added entradas)")
            added
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo fusionar el manifest de los plugins", e)
            0
        }
    }

    /** Tags que cuelgan de `<manifest>` (hermanos de `<application>`). */
    private val ROOT_LEVEL_TAGS = listOf("uses-permission", "permission", "uses-feature")

    /** Tags que cuelgan de `<application>`. */
    private val APPLICATION_LEVEL_TAGS = listOf("provider", "service", "receiver", "meta-data")

    /**
     * Extrae bloques `<tag …/>` o `<tag …>…</tag>` de un manifest (regex tolerante, sin parser XML:
     * los manifests de plugins son generados a maquina y no llevan CDATA ni comentarios raros).
     */
    private fun extractSelfClosingOrBlocks(content: String, tag: String): List<String> {
        val selfClosing = Regex("<$tag\\b[^>]*/>").findAll(content).map { it.value }
        val withBody = Regex("<$tag\\b[^>]*>[\\s\\S]*?</$tag>").findAll(content).map { it.value }
        return (selfClosing + withBody).map { it.trim() }.distinct().toList()
    }

    /* -------------------------------------------------------------------------------------- */
    /* Embedding                                                                                 */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Ruta donde se deja el jar del embedding para el classpath del proyecto.
     *
     * Nombre **independiente del modo** a proposito: [maybeAddFlutterEmbeddingToClasspath] se llama
     * desde `ProjectBuilder.getClasspath()`, donde ya no se conoce el modo; da igual cual sea el
     * origen porque las clases `io.flutter.**` son las mismas (lo que cambia es el `BuildConfig`
     * interno, que solo importa para dexar el embedding, no para compilar).
     */
    @JvmStatic
    fun embeddingJarForClasspath(binDirectoryPath: String): File {
        return File(File(binDirectoryPath, "flutter"), "flutter_embedding.jar")
    }

    /**
     * Copia el jar del embedding **del [mode]** a `<bin>/flutter/` (para
     * [ProjectBuilder.getClasspath]) y extrae sus `.class` al [compiledClassesPath] para que D8 las
     * dexee.
     *
     * El jar correcto es critico: su `BuildConfig` hace que `FlutterLoader` cargue
     * `kernel_blob.bin` (jar debug) o `libapp.so` (jar release).
     *
     * @return número de clases extraídas, o -1 si falló.
     */
    @JvmStatic
    fun prepareEmbeddingClasses(
        context: Context,
        binDirectoryPath: String,
        compiledClassesPath: String,
        mode: FlutterBuildMode,
    ): Int {
        val source = FlutterToolchainPaths.embeddingJar(context, mode)
        if (!source.isFile) {
            Log.w(TAG, "Falta el jar del embedding ($mode): ${source.absolutePath}")
            return -1
        }

        val classpathJar = embeddingJarForClasspath(binDirectoryPath)
        classpathJar.parentFile?.mkdirs()
        source.copyTo(classpathJar, overwrite = true)

        val classesDir = File(compiledClassesPath)
        if (!classesDir.mkdirs() && !classesDir.isDirectory) {
            Log.w(TAG, "No se pudo crear $compiledClassesPath")
            return -1
        }

        var extracted = 0
        try {
            ZipInputStream(source.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(".class") && !name.startsWith("META-INF/")) {
                        val destination = File(classesDir, name)
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output -> zip.copyTo(output, 256 * 1024) }
                        extracted++
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo extrayendo el embedding", e)
            return -1
        }

        Log.d(TAG, "Embedding: $extracted clases extraidas a $compiledClassesPath")
        return extracted
    }

    /** Compatibilidad: usa [FlutterProjectDefaults.DEFAULT_MODE] (el unico modo que compila hoy). */
    @JvmStatic
    fun prepareEmbeddingClasses(
        context: Context,
        binDirectoryPath: String,
        compiledClassesPath: String,
    ): Int = prepareEmbeddingClasses(
        context, binDirectoryPath, compiledClassesPath, FlutterProjectDefaults.DEFAULT_MODE
    )

    /**
     * Añade las reglas `-keep` de Flutter al fichero de reglas ProGuard del proyecto.
     *
     * R8 (cuando el proyecto tiene shrinking activado) borraría las clases del embedding: todas
     * entran por reflexión/JNI (`FlutterJNI`, `FlutterInjector`, ...). El fichero de reglas es
     * **dato del proyecto** (`.sketchware/data/<sc_id>/proguard-rules.pro`), no un fichero del repo.
     */
    @JvmStatic
    fun appendFlutterKeepRules(proguardRulesFile: File): Boolean {
        val marker = "# --- Flutter (sketchware-pro, carril C) ---"
        val rules = """
            $marker
            -keep class io.flutter.** { *; }
            -keep class io.flutter.plugin.** { *; }
            -dontwarn io.flutter.**
        """.trimIndent() + "\n"

        return try {
            val existing = if (proguardRulesFile.isFile) proguardRulesFile.readText() else ""
            if (existing.contains(marker)) {
                true
            } else {
                proguardRulesFile.parentFile?.mkdirs()
                proguardRulesFile.writeText(existing + (if (existing.endsWith("\n") || existing.isEmpty()) "" else "\n") + rules)
                Log.d(TAG, "Reglas ProGuard de Flutter anadidas a ${proguardRulesFile.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron anadir las reglas ProGuard de Flutter", e)
            false
        }
    }
}
