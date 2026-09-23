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
 * Pipeline (idéntico al que se reprodujo en el dispositivo durante el spike):
 * - [FlutterBuildMode.DEBUG_JIT] (**único modo que funciona hoy**):
 *   `dartaotruntime gen_kernel_aot.dart.snapshot --target=flutter
 *    --platform=<patched_sdk>/platform_strong.dill --packages=<pkgcfg>
 *    -Ddart.vm.product=false -Ddart.vm.profile=false -o kernel_blob.bin main.dart`
 *   y el APK se arma con el engine **debug** (sin `libapp.so`).
 * - [FlutterBuildMode.RELEASE_AOT] (**bloqueado a proposito**, ver [RELEASE_AOT_BLOCKED_MESSAGE]).
 *
 * Además monta `flutter_assets/` con `AssetManifest.json`, `FontManifest.json`, `icudtl.dat`
 * (si existiera) y (`DEBUG_JIT`) el `kernel_blob.bin`.
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
     * Escotilla de salida consciente para reintentar AOT on-device en el futuro.
     *
     * Con esto en `true` se genera un `libapp.so` con el `gen_snapshot` **del SDK de Termux**, que
     * esta compilado sin compressed pointers: el engine oficial aborta al arrancar
     * (`CreateRootIsolate failed: Snapshot not compatible`). Riesgo explicito: se produce un APK
     * que compila pero crashea al abrir. No activar sin antes cambiar el `gen_snapshot`.
     */
    const val ALLOW_EXPERIMENTAL_RELEASE_AOT = false

    /** Mensaje honesto del bloqueo de AOT (se usa como fallo, nunca para generar un `libapp.so`). */
    const val RELEASE_AOT_BLOCKED_MESSAGE =
        "RELEASE_AOT no es viable compilando en el dispositivo: el `gen_snapshot` del SDK Dart de " +
            "Termux (${FlutterToolchainPaths.DART_VERSION}) esta construido SIN compressed pointers " +
            "(ni acepta la flag: `Unrecognized flags: compressed_pointers`) y el engine oficial de " +
            "Flutter ${FlutterToolchainPaths.FLUTTER_VERSION} EXIGE el perfil 'arm64 android " +
            "compressed-pointers', asi que aborta al crear el isolate raiz con " +
            "`CreateRootIsolate failed: Snapshot not compatible ...`. El unico gen_snapshot valido " +
            "es el del propio engine, que solo se publica para host linux-x64/darwin-x64/windows-x64 " +
            "(no hay binario android/arm64). Generar un libapp.so con el gen_snapshot del SDK (lo que " +
            "se hacia antes) solo produce un APK que crashea al arrancar: peor que este error. " +
            "Usa el modo DEBUG_JIT (engine debug + kernel_blob.bin), que esta demostrado que arranca " +
            "y responde a los toques. Detalles y vias de solucion: docs/flutter-fase7.md."

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

        // Bloqueo honesto del AOT on-device (ver RELEASE_AOT_BLOCKED_MESSAGE y docs/flutter-fase7.md).
        // Se falla ANTES de tocar `gen_snapshot`: generar un libapp.so incompatible es peor que un
        // error claro, porque el APK se instala y crashea sin explicacion.
        if (mode == FlutterBuildMode.RELEASE_AOT && !ALLOW_EXPERIMENTAL_RELEASE_AOT) {
            return fail(RELEASE_AOT_BLOCKED_MESSAGE)
        }

        val dartAotRuntime = FlutterToolchainPaths.dartAotRuntime(context)
        val genKernelSnapshot = FlutterToolchainPaths.genKernelSnapshot(context)
        val genSnapshot = FlutterToolchainPaths.genSnapshot(context)
        val platformDill = FlutterToolchainPaths.patchedSdkPlatformDill(context)
        val sdkRoot = FlutterToolchainPaths.patchedSdkRoot(context)

        val missing = listOf(dartAotRuntime, genKernelSnapshot, genSnapshot, platformDill, sdkRoot)
            .filter { !it.exists() }
        if (missing.isNotEmpty()) {
            return fail("Toolchain incompleto, faltan: " + missing.joinToString { it.absolutePath })
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
            writePackageConfig(flutterRoot, frameworkDir, FlutterToolchainPaths.pubDepsDir(context))
            log.append("[ok] package_config.json generado en ")
                .append(FlutterToolchainPaths.packageConfigFile(flutterRoot).absolutePath).append('\n')
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
        writeAssetManifest(flutterRoot, File(assetsDir, ASSET_MANIFEST_FILE), log)
        File(assetsDir, FONT_MANIFEST_FILE).writeText("[]\n")

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
        genKernelArgs.add(entryPoint.absolutePath)

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

    /** `AssetManifest.json` a partir de `<flutterRoot>/assets…*`. */
    @JvmStatic
    fun writeAssetManifest(flutterRoot: File, destination: File, log: StringBuilder) {
        val manifest = JsonObject()
        val assetsRoot = File(flutterRoot, "assets")
        var count = 0
        if (assetsRoot.isDirectory) {
            assetsRoot.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.absolutePath }
                .forEach { file ->
                    val relative = file.relativeTo(assetsRoot).invariantSeparatorsPath
                    val key = "assets/$relative"
                    val variants = JsonArray()
                    variants.add(key)
                    manifest.add(key, variants)
                    count++
                }
        }
        destination.writeText(manifest.toString())
        log.append("[ok] AssetManifest.json con ").append(count).append(" assets\n")
    }

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
        val startedAt = System.currentTimeMillis()
        val output = StringBuilder()
        return try {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()

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
