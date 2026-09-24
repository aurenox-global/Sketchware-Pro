package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Resultado de compilar el código Dart de un proyecto Flutter.
 *
 * SIN `@JvmField` (carril C2): [FlutterCompilerBridge] (Java) lee estos miembros con
 * `result.getSuccess()`, `result.getLog()`, `dartResult.getLibAppSoPath()` y
 * `dartResult.getFlutterAssetsDir()`. Con `@JvmField` Kotlin no genera esos getters y el Java no
 * compila; sin la anotacion, getters y acceso por propiedad conviven.
 */
class FlutterDartCompileResult(
    val success: Boolean,
    val mode: FlutterBuildMode,
    val libAppSoPath: String?,
    val kernelBlobPath: String?,
    val flutterAssetsDir: String?,
    val log: String,
    val durationMs: Long,
)

/**
 * Compilador Dart on-device (carril C): `gen_kernel` + `gen_snapshot` del propio `.deb` de Termux.
 *
 * Pipeline (identico al que se reprodujo en el dispositivo durante el spike):
 * - [FlutterBuildMode.DEBUG_JIT]:
 *   `libdartaotruntime.so gen_kernel_aot.dart.snapshot --target=flutter
 *    --platform=<patched_sdk>/platform_strong.dill --packages=<pkgcfg>
 *    -Ddart.vm.product=false -Ddart.vm.profile=false -o kernel_blob.bin main.dart`
 *   y el APK se arma con el engine **debug** (sin `libapp.so`).
 * - [FlutterBuildMode.RELEASE_AOT]: **desbloqueado en la Fase 8 (carril I)**. Front-end con
 *   `--aot --tfa --target-os=android -Ddart.vm.product=true` y la plataforma
 *   `flutter_patched_sdk_product`, back-end con **nuestro** `gen_snapshot` product + compressed
 *   pointers (`libfluttergensnapshot.so`). Evidencia: informe AOT §4-§5 (libapp.so aceptado por el
 *   engine release, la app arranca sin banner DEBUG). Si esa variante del APK no lleva el binario
 *   (ABI sin backend: `armeabi-v7a`/`x86`, o variante que no lo empaqueta), se falla con un mensaje
 *   claro en vez de generar un `libapp.so` incompatible.
 *
 * Los dos ejecutables se lanzan desde `nativeLibraryDir` (unica ubicacion ejecutable para la app
 * con `targetSdk >= 29`), con respaldo en `filesDir` para el resto de ABIs:
 * [FlutterToolchainPaths.dartAotRuntimeExecutable] / [FlutterToolchainPaths.genSnapshotExecutable].
 *
 * Además monta `flutter_assets/` (delegando los manifiestos en el carril A2) y compila la parte
 * Android de los plugins del proyecto ([FlutterPluginCompiler.kt] + fuentes de
 * `.dart_tool/flutter_build` completo).
 *
 * Las flags de `gen_kernel` son **las exactas** de la prueba E2E (informe §4.2 y §4.6): ese
 * `gen_kernel` no acepta `--sdk-root`, `--output-dill` ni `--component-name` (`Unrecognized
 * flags`), asi que no se le pasan.
 */
object FlutterDartCompiler {

    private const val TAG = "FlutterDartCompiler"

    /** 15 minutos: AOT de un proyecto pequeño en un móvil tarda minutos, no segundos. */
    const val DEFAULT_TIMEOUT_MS = 15L * 60L * 1000L

    /**
     * Mensaje del bloqueo **historico** de AOT (se conserva como referencia/documentacion; ya **no**
     * se usa para fallar el build: el carril K demostro que un `gen_snapshot` propio con compressed
     * pointers si produce un `libapp.so` que el engine release acepta).
     */
    const val RELEASE_AOT_HISTORICAL_NOTE =
        "El `gen_snapshot` del SDK Dart de Termux (${FlutterToolchainPaths.DART_VERSION}) esta " +
            "construido SIN compressed pointers y el engine oficial de Flutter " +
            "${FlutterToolchainPaths.FLUTTER_VERSION} EXIGE el perfil 'arm64 android compressed-pointers'. " +
            "Por eso el AOT se hace con el `gen_snapshot` propio (build `--arch arm64c --mode product` " +
            "en arm64, `--arch x64c --mode product` en x86_64: las dos ABIs usan compressed pointers)."

    /**
     * Mensaje cuando el APK instalado no lleva el backend AOT (ABI sin backend, o variante que no
     * empaqueta el binario). Se prefiere fallar claro antes que producir un `libapp.so` que crashea
     * al arrancar.
     */
    const val AOT_BACKEND_MISSING_MESSAGE =
        "RELEASE_AOT no esta disponible en esta instalacion: el `gen_snapshot` propio (product + " +
            "compressed pointers) solo viaja en las variantes arm64-v8a y x86_64 del APK, empaquetado " +
            "como `lib/<abi>/${FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT}` en `jniLibs` (SELinux solo " +
            "permite ejecutar desde nativeLibraryDir). Compila e instala la variante arm64-v8a o " +
            "x86_64, o usa el modo DEBUG_JIT. Detalles: informe AOT (§2, §6, §7)."

    private const val ASSET_MANIFEST_FILE = "AssetManifest.json"
    private const val FONT_MANIFEST_FILE = "FontManifest.json"
    private const val ICU_DATA_FILE = "icudtl.dat"

    @JvmStatic
    @JvmOverloads
    fun compile(
        context: Context,
        flutterRoot: File,
        mode: FlutterBuildMode,
        progress: (String) -> Unit,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): FlutterDartCompileResult {
        val startedAt = System.currentTimeMillis()
        val log = StringBuilder()

        fun fail(message: String): FlutterDartCompileResult {
            log.append("[ERROR] ").append(message).append('\n')
            Log.w(TAG, message)
            return FlutterDartCompileResult(
                false, mode, null, null, null, log.toString(),
                System.currentTimeMillis() - startedAt
            )
        }

        // AOT: solo se puede si el APK instalado trae nuestro `gen_snapshot` (arm64-v8a o x86_64).
        // En cualquier otra ABI se falla ANTES de tocar nada: un libapp.so sin compressed pointers es
        // un APK que se instala y crashea sin explicacion (informe AOT §5.3).
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            val reason = FlutterToolchainPaths.aotBackendUnavailableReason(context)
            if (reason != null) {
                return fail(reason + "\n" + AOT_BACKEND_MISSING_MESSAGE)
            }
        }

        val dartAotRuntime = FlutterToolchainPaths.dartAotRuntimeExecutable(context)
        val genKernelSnapshot = FlutterToolchainPaths.genKernelSnapshot(context)
        val genSnapshot = FlutterToolchainPaths.genSnapshotExecutable(context)
        // En RELEASE_AOT el front-end usa la plataforma **product** (la que probo el carril K); en
        // DEBUG_JIT, la normal.
        val platformDill = FlutterToolchainPaths.patchedSdkPlatformDillForMode(context, mode)
        val sdkRoot = FlutterToolchainPaths.patchedSdkRootForMode(context, mode)

        val required = mutableListOf(dartAotRuntime, genKernelSnapshot, platformDill, sdkRoot)
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            required.add(genSnapshot)
        }
        val missing = required.filter { !it.exists() }
        if (missing.isNotEmpty()) {
            return fail("Toolchain incompleto, faltan: " + missing.joinToString { it.absolutePath })
        }

        log.append("[info] dartaotruntime: ").append(dartAotRuntime.absolutePath)
            .append(if (FlutterToolchainPaths.isDartAotRuntimePackaged(context)) " (empaquetado)" else " (filesDir)")
            .append('\n')
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            log.append("[info] gen_snapshot: ").append(genSnapshot.absolutePath)
                .append(if (FlutterToolchainPaths.isGenSnapshotPackaged(context)) " (empaquetado, product+compressed-pointers)" else " (filesDir)")
                .append('\n')
        }

        val entryPoint = File(flutterRoot, "lib/main.dart")
        if (!entryPoint.isFile) {
            return fail("No hay punto de entrada Dart en ${entryPoint.absolutePath}")
        }

        val usesFlutterFramework = projectUsesFlutterFramework(File(flutterRoot, "lib"))
        val frameworkDir = FlutterToolchainPaths.frameworkDir(context)
        if (usesFlutterFramework && !frameworkDir.isDirectory) {
            return fail(
                "El proyecto importa package:flutter/* pero no hay fuentes del framework en " +
                        frameworkDir.absolutePath + ". Descargalas con " +
                        "FlutterToolchainManager.ensureInstalled (menu Flutter del editor): los " +
                        "artefactos del engine (jar + patched_sdk) no incluyen el framework Dart."
            )
        }

        val staging = FlutterToolchainPaths.stagingDir(flutterRoot)
        if (!staging.mkdirs() && !staging.isDirectory) {
            return fail("No se pudo crear ${staging.absolutePath}")
        }

        val assetsDir = FlutterToolchainPaths.flutterAssetsDir(flutterRoot)
        FlutterToolchainInstaller.deleteRecursively(assetsDir)
        assetsDir.mkdirs()

        try {
            val source = ensurePackageConfig(context, flutterRoot, frameworkDir, progress, log)
            log.append("[ok] package_config.json (").append(source.name).append(") en ")
                .append(FlutterToolchainPaths.packageConfigFile(flutterRoot).absolutePath).append('\n')
            if (source == PackageConfigSource.SYNTHETIC) {
                log.append(
                    "[warn] resolucion SINTETICA (carril C, Fase 7): solo vale para proyectos sin " +
                        "dependencias externas. Ejecuta 'dart pub get' para resolver de verdad.\n"
                )
            }
        } catch (e: Exception) {
            return fail("No se pudo escribir package_config.json: ${e.message}")
        }

        // flutter_assets/: comun a ambos modos.
        val icuSource = FlutterToolchainPaths.icuDataFile(context)
        if (icuSource.isFile) {
            icuSource.copyTo(File(assetsDir, ICU_DATA_FILE), overwrite = true)
        } else {
            log.append("[warn] falta icudtl.dat en el toolchain\n")
        }
        // Manifiestos + fuentes + shaders: los escribe el carril A2 ([FlutterBundleAssets]);
        // el manifiesto del carril P es solo respaldo (ver [writeBundleAssets]).
        writeBundleAssets(context, flutterRoot, assetsDir, progress, log)

        // Plugins (carril P): registrantes Dart/Java y wrapper de entrada si hacen falta.
        val entrypoint = FlutterPluginSupport.prepareEntrypoint(context, flutterRoot, progress, log)

        val outputDill = if (mode == FlutterBuildMode.DEBUG_JIT) {
            FlutterToolchainPaths.kernelBlobFile(flutterRoot)
        } else {
            FlutterToolchainPaths.aotDillFile(flutterRoot)
        }

        val genKernelArgs = mutableListOf<String>()
        genKernelArgs.add("--target=flutter")
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            // Flags exactas del informe E2E §4.2 (front-end AOT con TFA).
            genKernelArgs.add("--aot")
            genKernelArgs.add("--tfa")
            genKernelArgs.add("--target-os=android")
        }
        genKernelArgs.add("--platform=${platformDill.absolutePath}")
        genKernelArgs.add("--packages=${FlutterToolchainPaths.packageConfigFile(flutterRoot).absolutePath}")
        // Defines de VM del informe E2E §4.2/§4.6: sin ellos el kernel no lleva el mismo perfil que
        // el engine (product=true solo en release; en JIT tiene que ir product=false).
        genKernelArgs.add(if (mode == FlutterBuildMode.RELEASE_AOT) "-Ddart.vm.product=true" else "-Ddart.vm.product=false")
        genKernelArgs.add("-Ddart.vm.profile=false")
        genKernelArgs.add("-o")
        genKernelArgs.add(outputDill.absolutePath)
        genKernelArgs.add(entrypoint.absolutePath)

        progress("Compilando Dart (${mode.name}): gen_kernel...")
        val genKernelResult = runCommand(
            command = listOf(dartAotRuntime.absolutePath, genKernelSnapshot.absolutePath) + genKernelArgs,
            workingDirectory = flutterRoot,
            timeoutMs = timeoutMs,
        )
        log.append("$ gen_kernel ").append(genKernelArgs.joinToString(" ")).append('\n')
        log.append(genKernelResult.output).append('\n')
        if (genKernelResult.timedOut) {
            return fail("gen_kernel agoto el timeout de ${timeoutMs} ms")
        }
        if (genKernelResult.exitCode != 0 || !outputDill.isFile) {
            return fail("gen_kernel fallo (exit=${genKernelResult.exitCode})")
        }
        log.append("[ok] ${outputDill.name} = ${outputDill.length()} bytes\n")

        if (mode == FlutterBuildMode.DEBUG_JIT) {
            // En debug el kernel_blob.bin va DENTRO de flutter_assets.
            val kernelBlob = FlutterToolchainPaths.kernelBlobFile(flutterRoot)
            if (!kernelBlob.isFile) {
                return fail("No se genero kernel_blob.bin")
            }
            return FlutterDartCompileResult(
                true, mode, null, kernelBlob.absolutePath, assetsDir.absolutePath,
                log.toString(), System.currentTimeMillis() - startedAt
            )
        }

        val libAppSo = FlutterToolchainPaths.libAppSo(flutterRoot)
        if (libAppSo.exists() && !libAppSo.delete()) {
            Log.w(TAG, "No se pudo borrar ${libAppSo.absolutePath}")
        }
        val genSnapshotArgs = listOf(
            "--deterministic",
            "--snapshot_kind=app-aot-elf",
            "--elf=${libAppSo.absolutePath}",
            "--strip",
            outputDill.absolutePath,
        )

        progress("Enlazando AOT: gen_snapshot...")
        val genSnapshotResult = runCommand(
            command = listOf(genSnapshot.absolutePath) + genSnapshotArgs,
            workingDirectory = flutterRoot,
            timeoutMs = timeoutMs,
        )
        log.append("$ gen_snapshot ").append(genSnapshotArgs.joinToString(" ")).append('\n')
        log.append(genSnapshotResult.output).append('\n')
        if (genSnapshotResult.timedOut) {
            return fail("gen_snapshot agoto el timeout de ${timeoutMs} ms")
        }
        if (!libAppSo.isFile) {
            return fail("gen_snapshot no produjo libapp.so (exit=${genSnapshotResult.exitCode})")
        }
        log.append("[ok] libapp.so = ${libAppSo.length()} bytes\n")

        return FlutterDartCompileResult(
            true, mode, libAppSo.absolutePath, null, assetsDir.absolutePath,
            log.toString(), System.currentTimeMillis() - startedAt
        )
    }

    /* -------------------------------------------------------------------------------------- */
    /* Auxiliares                                                                               */
    /* -------------------------------------------------------------------------------------- */

    /**
     * `package_config.json` del proyecto, coherente con la estructura del toolchain.
     *
     * Estructura (ver [FlutterToolchainPaths.pubDepsDir]):
     * - el propio proyecto -> `<flutterRoot>/lib`;
     * - `flutter` -> `frameworkDir` (`<engine>/flutter-framework`, que ES `packages/flutter`);
     * - sus dependencias de pub -> `<engine>/pub-deps/<name>-<version>` con las versiones exactas
     *   del informe E2E. `sky_engine` NO se declara: `dart:ui` viaja dentro de
     *   `platform_strong.dill` (el `--platform` de `gen_kernel`).
     *
     * `languageVersion` sale del `environment: sdk:` de cada pubspec (el framework declara 3.11);
     * el del proyecto se lee de su `pubspec.yaml` con [dartLanguageVersion].
     *
     * @param pubDepsDir carpeta `pub-deps`; por defecto la de al lado de `frameworkDir`.
     */
    @JvmStatic
    fun writePackageConfig(flutterRoot: File, frameworkDir: File, pubDepsDir: File) {
        val packageConfigFile = FlutterToolchainPaths.packageConfigFile(flutterRoot)
        packageConfigFile.parentFile?.mkdirs()

        val name = readPubspecName(flutterRoot) ?: "flutter_app"

        val packages = JsonArray()
        packages.add(packageEntry(name, flutterRoot.toURI().toString(), "lib/", dartLanguageVersion(flutterRoot)))
        if (frameworkDir.isDirectory) {
            packages.add(
                packageEntry(
                    "flutter",
                    ensureTrailingSlash(frameworkDir.toURI().toString()),
                    "lib/",
                    FlutterToolchainPaths.FLUTTER_FRAMEWORK_LANGUAGE_VERSION,
                )
            )
            for (dependency in FlutterToolchainPaths.frameworkPubDependencies()) {
                val dependencyDir = File(pubDepsDir, dependency.directoryName)
                if (!dependencyDir.isDirectory) {
                    continue
                }
                packages.add(
                    packageEntry(
                        dependency.name,
                        ensureTrailingSlash(dependencyDir.toURI().toString()),
                        "lib/",
                        dependency.languageVersion,
                    )
                )
            }
        }

        val root = JsonObject()
        root.addProperty("configVersion", 2)
        root.add("packages", packages)
        root.addProperty("generated", System.currentTimeMillis().toString())
        root.addProperty("generator", "sketchware-flutter")
        root.addProperty("generatorVersion", FlutterToolchainPaths.DART_VERSION)

        packageConfigFile.writeText(root.toString())
    }

    /** Compatibilidad: asume `pub-deps` al lado de `frameworkDir`. */
    @JvmStatic
    fun writePackageConfig(flutterRoot: File, frameworkDir: File) {
        writePackageConfig(flutterRoot, frameworkDir, File(frameworkDir.parentFile, "pub-deps"))
    }

    /**
     * `languageVersion` del proyecto a partir de `environment: sdk:` del `pubspec.yaml`
     * (`>=3.1.0 <4.0.0` -> `3.1`). Si no se puede leer, se usa el valor por defecto.
     */
    @JvmStatic
    fun dartLanguageVersion(flutterRoot: File): String {
        val pubspec = File(flutterRoot, "pubspec.yaml")
        if (!pubspec.isFile) {
            return FlutterToolchainPaths.DEFAULT_DART_LANGUAGE_VERSION
        }
        for (line in pubspec.readLines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("sdk:") || line.startsWith("\t")) {
                continue
            }
            val value = trimmed.substringAfter(':').trim().trim('"', '\'')
            val match = Regex("(\\d+)\\.(\\d+)").find(value)
            if (match != null) {
                return "${match.groupValues[1]}.${match.groupValues[2]}"
            }
        }
        return FlutterToolchainPaths.DEFAULT_DART_LANGUAGE_VERSION
    }

    private fun packageEntry(
        name: String,
        rootUri: String,
        packageUri: String,
        languageVersion: String,
    ): JsonObject {
        val entry = JsonObject()
        entry.addProperty("name", name)
        entry.addProperty("rootUri", rootUri)
        entry.addProperty("packageUri", packageUri)
        entry.addProperty("languageVersion", languageVersion)
        return entry
    }

    private fun ensureTrailingSlash(uri: String): String = if (uri.endsWith("/")) uri else "$uri/"

    /** `name:` del `pubspec.yaml` (parseo mínimo, sin dependencias YAML). */
    @JvmStatic
    fun readPubspecName(flutterRoot: File): String? {
        val pubspec = File(flutterRoot, "pubspec.yaml")
        if (!pubspec.isFile) return null
        for (line in pubspec.readLines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("name:") || line.startsWith(" ")) continue
            val value = trimmed.substringAfter(':').trim().trim('"', '\'')
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /**
     * Monta `flutter_assets/` delegando **en el carril A2** ([FlutterBundleAssets.populateFlutterAssets],
     * dueno de los manifiestos: `AssetManifest.bin` + `.json`, `FontManifest.json`, fuentes Material,
     * shaders y `NOTICES.Z`) y dejando [writeAssetManifest] **solo como respaldo** para cuando A2 no
     * esta disponible en esa build o no puede montar el bundle (p. ej. sin fuentes Material
     * descargadas). Asi no hay dos escritores del mismo manifiesto.
     *
     * @return claves de asset del bundle (las que A2 puso en el manifiesto).
     */
    @JvmStatic
    fun writeBundleAssets(
        context: Context,
        flutterRoot: File,
        assetsDir: File,
        progress: (String) -> Unit,
        log: StringBuilder,
    ): List<String> {
        val declared = readDeclaredAssets(File(flutterRoot, "pubspec.yaml"))
        try {
            val result = FlutterBundleAssets.populateFlutterAssets(context, assetsDir, flutterRoot, progress)
            log.append("[ok] bundle de assets (carril A2): ").append(result).append('\n')
            if (result.success) {
                // A2 resolvio los assets declarados en el pubspec; se devuelven sus claves.
                return FlutterBundleAssets.collectAssetEntries(flutterRoot, declared).keys.toList()
            }
            log.append("[warn] A2 no pudo montar flutter_assets (")
                .append(result.assetEntryCount).append(" assets); respaldo del carril P\n")
            log.append(result.log)
        } catch (e: Throwable) {
            log.append("[warn] FlutterBundleAssets no disponible en esta build: ")
                .append(e.javaClass.simpleName).append(": ").append(e.message).append('\n')
        }
        val keys = writeAssetManifest(flutterRoot, File(assetsDir, ASSET_MANIFEST_FILE), log)
        File(assetsDir, FONT_MANIFEST_FILE).writeText("[]\n")
        return keys
    }

    /**
     * `AssetManifest.json` a partir de los `assets:` **declarados en el pubspec.yaml** (carril P).
     *
     * Es lo que hace el tool de Flutter: solo entran los assets declarados, con la ruta tal cual
     * (`assets/img/logo.png`). Ademas de escribir el manifiesto, **copia cada fichero a
     * `flutter_assets/<ruta>`**, que es donde el engine los busca en tiempo de ejecucion (antes solo
     * se copiaban los que ya estuvieran en `flutter_assets`, y el scaffold no copiaba ninguno).
     *
     * Soporta las tres formas de declararlo que acepta Flutter:
     * - fichero suelto: `assets/logo.png`
     * - carpeta completa (recursiva): `assets/` o `assets/img/`
     * - comodin de un nivel: la carpeta mas un asterisco y la extension (por ejemplo
     *   `assets/img/` + `*.png`); el doble asterisco de Flutter se trata como carpeta completa, no se
     *   implementa el matching completo de `package:glob`
     *
     * Si el pubspec no declara `assets:`, se mantiene el comportamiento de la Fase 7 (recorrer
     * `<flutterRoot>/assets`) para no romper proyectos hechos antes.
     *
     * @return lista de rutas de asset escritas en el manifiesto (claves del JSON).
     */
    @JvmStatic
    fun writeAssetManifest(flutterRoot: File, destination: File, log: StringBuilder): List<String> {
        val declared = readDeclaredAssets(File(flutterRoot, "pubspec.yaml"))
        val manifest = JsonObject()
        val written = mutableListOf<String>()
        val assetsOutputDir = destination.parentFile

        fun add(relativePath: String, source: File) {
            val key = relativePath.removePrefix("/")
            if (manifest.has(key)) {
                return
            }
            val variants = JsonArray()
            variants.add(key)
            manifest.add(key, variants)
            written.add(key)
            if (assetsOutputDir != null) {
                val target = File(assetsOutputDir, key)
                try {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                } catch (e: Exception) {
                    log.append("[warn] no se pudo copiar el asset ").append(key)
                        .append(": ").append(e.message).append('\n')
                }
            }
        }

        if (declared.isEmpty()) {
            val assetsRoot = File(flutterRoot, "assets")
            if (assetsRoot.isDirectory) {
                assetsRoot.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.absolutePath }
                    .forEach { file ->
                        val relative = file.relativeTo(assetsRoot).invariantSeparatorsPath
                        add("assets/$relative", file)
                    }
            }
            log.append("[warn] pubspec.yaml sin `flutter: assets:`; se recorrio assets/ ")
                .append("(").append(written.size).append(" ficheros)\n")
        } else {
            for (entry in declared) {
                val trimmed = entry.trim().trim('"', '\'')
                if (trimmed.isEmpty()) {
                    continue
                }
                val normalized = trimmed.removePrefix("./")
                val source = File(flutterRoot, normalized)
                when {
                    // Carpeta completa (`assets/`, `assets/img/`).
                    source.isDirectory -> {
                        source.walkTopDown()
                            .filter { it.isFile }
                            .sortedBy { it.absolutePath }
                            .forEach { file ->
                                val relative = file.relativeTo(flutterRoot).invariantSeparatorsPath
                                add(relative, file)
                            }
                    }
                    // Comodin de un nivel (carpeta + un unico asterisco).
                    normalized.contains('*') -> {
                        val parent = File(flutterRoot, normalized.substringBeforeLast('/'))
                        val pattern = normalized.substringAfterLast('/')
                        val regex = Regex("^" + globToRegexPattern(pattern) + "$")
                        parent.listFiles()
                            ?.filter { it.isFile && regex.matches(it.name) }
                            ?.sortedBy { it.name }
                            ?.forEach { file ->
                                val relative = file.relativeTo(flutterRoot).invariantSeparatorsPath
                                add(relative, file)
                            }
                    }
                    source.isFile -> add(normalized, source)
                    else -> log.append("[warn] asset declarado pero inexistente: ").append(trimmed)
                        .append('\n')
                }
            }
        }

        destination.writeText(manifest.toString())
        log.append("[ok] AssetManifest.json con ").append(written.size).append(" assets\n")
        return written
    }

    /**
     * Convierte un comodin de un nivel a expresion regular: el asterisco es "cualquier cosa menos
     * una barra" y el interrogante un caracter; el resto se escapa. Se hace a mano porque
     * `Regex.escape` envuelve el texto en `\Q...\E` y no deja sustituir los comodines.
     */
    @JvmStatic
    fun globToRegexPattern(pattern: String): String {
        val builder = StringBuilder(pattern.length * 4)
        for (character in pattern) {
            when (character) {
                '*' -> builder.append("[^/]*")
                '?' -> builder.append("[^/]")
                else -> builder.append(Regex.escape(character.toString()))
            }
        }
        return builder.toString()
    }

    /**
     * Entradas de `flutter: assets:` del `pubspec.yaml` (parser minimo de YAML, sin dependencias).
     *
     * Solo se lee el bloque `assets:` que cuelga de `flutter:`, que es donde Flutter las declara:
     * ```yaml
     * flutter:
     *   uses-material-design: true
     *   assets:
     *     - assets/
     *     - assets/logo.png
     * ```
     * Se acepta indentacion con espacios (el YAML prohibe tabuladores) y varios niveles de sangria
     * dentro de `flutter:`. Las lineas de comentario se ignoran. Devuelve las entradas sin el `-`.
     */
    @JvmStatic
    fun readDeclaredAssets(pubspec: File): List<String> {
        if (!pubspec.isFile) {
            return emptyList()
        }
        val assets = mutableListOf<String>()
        var inFlutterBlock = false
        var flutterIndent = -1
        var assetsIndent = -1
        for (raw in pubspec.readLines()) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) {
                continue
            }
            val trimmed = raw.trimEnd()
            val indent = trimmed.length - trimmed.trimStart().length
            val content = trimmed.trimStart()

            if (indent == 0) {
                inFlutterBlock = content.startsWith("flutter:")
                flutterIndent = if (inFlutterBlock) 0 else -1
                assetsIndent = -1
                continue
            }
            if (!inFlutterBlock || indent <= flutterIndent) {
                continue
            }
            if (assetsIndent >= 0) {
                if (indent > assetsIndent) {
                    if (content.startsWith("-")) {
                        val value = content.removePrefix("-").trim().trim('"', '\'')
                        if (value.isNotEmpty()) {
                            assets.add(value)
                        }
                    }
                    continue
                }
                assetsIndent = -1
            }
            if (content.startsWith("assets:")) {
                assetsIndent = indent
                // Forma en linea: `assets: [a, b]`.
                val inline = content.removePrefix("assets:").trim()
                if (inline.startsWith("[")) {
                    inline.trim('[', ']').split(',')
                        .map { it.trim().trim('"', '\'') }
                        .filter { it.isNotEmpty() }
                        .forEach { assets.add(it) }
                    assetsIndent = -1
                }
            }
        }
        return assets
    }

    /**
     * Deja listo el `package_config.json` del proyecto y devuelve **de donde** salio.
     *
     * Orden de preferencia (carril P, Fase 8):
     * 1. [PackageConfigSource.PUB_RESOLVED_CACHED]: ya hay una resolucion de pub vigente (hash del
     *    pubspec sin cambios) -> no se toca la red;
     * 2. [PackageConfigSource.PUB_RESOLVED]: se ejecuta `dart pub get` de verdad en el dispositivo;
     * 3. [PackageConfigSource.SYNTHETIC]: ultimo recurso, el `package_config` a mano de la Fase 7
     *    (solo framework + 5 dependencias). Se usa si pub no esta instalado o si falla la resolucion
     *    (tipicamente sin red) **y** el proyecto no declara dependencias externas.
     */
    @JvmStatic
    fun ensurePackageConfig(
        context: Context,
        flutterRoot: File,
        frameworkDir: File,
        progress: (String) -> Unit,
        log: StringBuilder,
    ): PackageConfigSource {
        if (FlutterPubResolver.isResolutionFresh(flutterRoot)) {
            log.append("[ok] resolucion de pub vigente en cache\n")
            return PackageConfigSource.PUB_RESOLVED_CACHED
        }
        val resolution = FlutterPubResolver.resolve(context, flutterRoot, progress)
        log.append(resolution.log)
        if (resolution.success) {
            log.append("[ok] pub resolvio ").append(resolution.resolvedCount).append(" paquetes\n")
            return PackageConfigSource.PUB_RESOLVED
        }
        log.append("[warn] pub no pudo resolver: ").append(resolution.message).append('\n')
        if (declaresExternalDependencies(File(flutterRoot, "pubspec.yaml"))) {
            // Con dependencias externas el package_config sintetico no sirve de nada: mejor fallar
            // claro que producir un kernel sin los paquetes del usuario.
            throw IllegalStateException(resolution.message)
        }
        writePackageConfig(flutterRoot, frameworkDir, FlutterToolchainPaths.pubDepsDir(context))
        return PackageConfigSource.SYNTHETIC
    }

    /**
     * `true` si el `pubspec.yaml` declara alguna dependencia que no sea del SDK (`sdk: flutter`) ni
     * de desarrollo. Con dependencias externas, un `package_config` sintetico no compila el proyecto.
     */
    @JvmStatic
    fun declaresExternalDependencies(pubspec: File): Boolean {
        if (!pubspec.isFile) {
            return false
        }
        var inDependencies = false
        var pendingSdkCheck = false
        for (raw in pubspec.readLines()) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            if (!raw.startsWith(" ")) {
                inDependencies = trimmed.startsWith("dependencies:")
                pendingSdkCheck = false
                continue
            }
            if (!inDependencies) {
                continue
            }
            if (pendingSdkCheck) {
                pendingSdkCheck = false
                if (trimmed.startsWith("sdk:")) {
                    continue
                }
            }
            if (trimmed.startsWith("sdk:")) {
                continue
            }
            if (trimmed.endsWith(":")) {
                // Cabecera de dependencia: hay que ver si la siguiente linea dice `sdk: …`.
                pendingSdkCheck = true
                continue
            }
            return true
        }
        return false
    }

    /** De donde salio el `package_config.json` que se usa para compilar. */
    enum class PackageConfigSource {
        /** Resuelto por `dart pub get` en este build. */
        PUB_RESOLVED,

        /** Ya habia una resolucion de pub valida en `.dart_tool` (sin red). */
        PUB_RESOLVED_CACHED,

        /** Sintetico de la Fase 7 (solo framework + dependencias fijas). */
        SYNTHETIC,
    }

    /** Resultado de un proceso externo, con log completo. */

    /**
     * Busca imports `package:flutter/` en los `.dart` del proyecto. Si aparecen y no hay fuentes
     * del framework, la compilación no puede funcionar (ver limitación en la cabecera).
     */
    @JvmStatic
    fun projectUsesFlutterFramework(libDir: File): Boolean {
        if (!libDir.isDirectory) return false
        return libDir.walkTopDown()
            .filter { it.isFile && it.extension == "dart" }
            .any { file ->
                file.useLines { lines ->
                    lines.any { it.contains("package:flutter/") }
                }
            }
    }

    /** Resultado de un proceso externo, con log completo. */
    class ProcessResult(
        @JvmField val exitCode: Int,
        @JvmField val output: String,
        @JvmField val timedOut: Boolean,
        @JvmField val durationMs: Long,
    )

    /**
     * Ejecuta [command] en [workingDirectory] capturando **toda** la salida (stdout+stderr) y
     * aplicando [timeoutMs]. No lanza: devuelve el motivo del fallo dentro del resultado.
     */
    @JvmStatic
    fun runCommand(command: List<String>, workingDirectory: File, timeoutMs: Long): ProcessResult {
        return runCommand(command, workingDirectory, timeoutMs, null)
    }

    /**
     * Igual que [runCommand] pero **fijando variables de entorno** ([environment]).
     *
     * Lo usa [FlutterPubResolver] para pasar `PUB_CACHE`, `HOME`, `TMPDIR` y `FLUTTER_ROOT` al
     * cliente de pub sin tocar el entorno del proceso de la app. Las variables indicadas tienen
     * prioridad sobre las heredadas.
     */
    @JvmStatic
    fun runCommand(
        command: List<String>,
        workingDirectory: File,
        timeoutMs: Long,
        environment: Map<String, String>?,
    ): ProcessResult {
        val startedAt = System.currentTimeMillis()
        val output = StringBuilder()
        return try {
            val builder = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
            if (environment != null) {
                builder.environment().putAll(environment)
            }
            val process = builder.start()

            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> output.append(line).append('\n') }
                    }
                } catch (_: Exception) {
                    // el proceso murió: el exit code y el log parcial bastan
                }
            }
            reader.isDaemon = true
            reader.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
                reader.join(1000)
                return ProcessResult(-1, output.toString(), true, System.currentTimeMillis() - startedAt)
            }
            reader.join(2000)
            ProcessResult(
                process.exitValue(),
                output.toString(),
                false,
                System.currentTimeMillis() - startedAt,
            )
        } catch (e: Exception) {
            output.append("[exception] ").append(e.javaClass.name).append(": ").append(e.message).append('\n')
            ProcessResult(-1, output.toString(), false, System.currentTimeMillis() - startedAt)
        }
    }
}
