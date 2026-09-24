package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Resolucion **real** de dependencias `pub` EN EL DISPOSITIVO (carril P, Fase 8).
 *
 * Que sustituye: hasta la Fase 7 `FlutterDartCompiler.writePackageConfig` **sintetizaba a mano**
 * el `.dart_tool/package_config.json` (framework + 5 dependencias con versiones fijas del informe
 * E2E). Eso no soporta ninguna dependencia del usuario (`http`, `intl`, `shared_preferences`…) ni
 * plugins. Este objeto ejecuta el cliente de pub de verdad del SDK Dart instalado y usa el
 * `package_config.json` que **él** genera.
 *
 * Evidencia cruda en el emulador arm64 API 34 `RV_API34` (2026-09-23), con el SDK Dart extraido en
 * `/data/local/tmp/carrilP/dart-sdk`:
 * ```
 * $ HOME=… PUB_CACHE=…/pubcache dart pub get          # proyecto con http/intl/collection
 * Resolving dependencies...
 * Downloading packages...
 * + async 2.13.1 … + http 1.6.0 … + intl 0.19.0 … + collection 1.19.1
 * Changed 13 dependencies!
 * ```
 * y con `flutter: {sdk: flutter}` (necesita [prepareFlutterRoot]) 16 paquetes, incluidos
 * `flutter 0.0.0 from sdk flutter` y `sky_engine 0.0.99 from sdk flutter`.
 *
 * El cliente de pub **ya viene** en el `.deb` de Termux (`bin/snapshots/dartdev_aot.dart.snapshot`,
 * 16,4 MB) y **no** se lanza desde `bin/dart`. Ojo (Fase 8 / carril I): `bin/dart` es un ELF en
 * `filesDir` y SELinux **deniega** ejecutarlo (`execute_no_trans`) en `targetSdk >= 29`, asi que la
 * app ejecuta el snapshot con `dartaotruntime` **empaquetado en `nativeLibraryDir`**
 * (`lib/<abi>/libdartaotruntime.so`):
 * ```
 * <nativeLibraryDir>/libdartaotruntime.so <filesDir>/…/dart-sdk/bin/snapshots/dartdev_aot.dart.snapshot pub get
 * ```
 * (El snapshot es **dato**: se lee, no se ejecuta; por eso puede vivir en `filesDir`.)
 */
object FlutterPubResolver {

    private const val TAG = "FlutterPubResolver"

    /** 10 minutos: en movil con red movil lenta un `pub get` grande puede tardar; en LAN, segundos. */
    const val DEFAULT_TIMEOUT_MS = 10L * 60L * 1000L

    /** `generator` que pub escribe en `package_config.json`; si esta, la resolucion es real. */
    const val GENERATOR_PUB = "pub"

    /** Codigos de salida de pub (`package:pub` usa los de `dart:io` `sysexits.h`). */
    const val EXIT_OK = 0
    const val EXIT_BAD_DATA = 65
    const val EXIT_NO_INPUT = 66
    const val EXIT_UNAVAILABLE = 69 // version solving failed
    const val EXIT_SOFTWARE = 70
    const val EXIT_TEMP_FAIL = 75 // red/timeout
    const val EXIT_NO_PERM = 77

    /** Marca que se escribe en [FlutterToolchainPaths.pubGenerationMarker] tras un `pub get` correcto. */
    private const val MARKER_HEADER = "sketchware-flutter pub get"

    /* -------------------------------------------------------------------------------------- */
    /* Modelo                                                                                   */
    /* -------------------------------------------------------------------------------------- */

    /** Un paquete resuelto por pub (entrada de `package_config.json`). */
    class ResolvedPackage(
        val name: String,
        val version: String,
        val rootDirectory: File,
        val languageVersion: String,
        val fromSdk: Boolean,
    ) {
        override fun toString(): String = if (version.isEmpty()) name else "$name $version"
    }

    /** Como fallo la resolucion; la UI lo traduce a un consejo concreto en espanol. */
    enum class ErrorKind {
        NONE,

        /** El SDK Dart (o su cliente de pub) no esta instalado en el dispositivo. */
        NO_TOOLCHAIN,

        /** No hay `pubspec.yaml` en el proyecto. */
        NO_PUBSPEC,

        /** Sin red: DNS/socket. */
        NO_NETWORK,

        /** Proxy o mirror mal configurado (`PUB_HOSTED_URL`, 407/502 del proxy). */
        PROXY,

        /** pub.dev respondio 429 (rate limit). */
        RATE_LIMITED,

        /** Conflicto de versiones entre las dependencias declaradas. */
        VERSION_SOLVING,

        /** Dependencia `sdk: flutter` y no hay FLUTTER_ROOT sintetico valido. */
        MISSING_FLUTTER_SDK,

        /** La restriccion `environment: sdk:` no la cumple el Dart 3.13.4 del dispositivo. */
        SDK_CONSTRAINT,

        /** `pubspec.yaml` con sintaxis invalida. */
        PUBSPEC_INVALID,

        /** Se agoto el tiempo. */
        TIMEOUT,

        /** `--offline` y falta algo en el `PUB_CACHE`. */
        OFFLINE_MISSING,

        UNKNOWN,
    }

    /** Resultado de un intento de resolucion. */
    class PubResolveResult(
        val success: Boolean,
        val packageConfigFile: File?,
        val packages: List<ResolvedPackage>,
        val errorKind: ErrorKind,
        /** Mensaje en espanol listo para mostrar en la UI (vacio si [success]). */
        val message: String,
        /** Salida cruda de `dart pub get` (stdout+stderr). */
        val log: String,
        val exitCode: Int,
        val usedNetwork: Boolean,
        val durationMs: Long,
    ) {
        val resolvedCount: Int get() = packages.size
    }

    /* -------------------------------------------------------------------------------------- */
    /* API publica                                                                              */
    /* -------------------------------------------------------------------------------------- */

    /**
     * El SDK instalado trae cliente de pub **ejecutable**?
     *
     * Se exige el cliente en si (`dartdev_aot.dart.snapshot`) **y** un `dartaotruntime` que
     * funcione: con `bin/dart` (filesDir) en un `targetSdk >= 29` la respuesta seria
     * engañosamente `true` y el `pub get` fallaria con `Permission denied`.
     *
     * @param probe si `true` (por defecto) se ejecuta `dartaotruntime --version` de verdad.
     *   El orquestador puede pasar `false` para consultas baratas de UI.
     */
    @JvmStatic
    @JvmOverloads
    fun isPubClientAvailable(context: Context, probe: Boolean = true): Boolean {
        if (!FlutterToolchainPaths.dartDevSnapshot(context).isFile) {
            return false
        }
        val runtime = FlutterToolchainPaths.dartAotRuntimeExecutable(context)
        if (!probe) {
            return runtime.isFile
        }
        return FlutterToolchainInstaller.probeExecutable(runtime) != null
    }

    /**
     * Hay una resolucion **real** (hecha por pub) cacheada en el proyecto?
     *
     * Se exige `generator: "pub"` en el `package_config.json` y la marca
     * [FlutterToolchainPaths.pubGenerationMarker] (que guarda el hash del `pubspec.yaml` con el que
     * se resolvio), para no confundir el `package_config` sintetico de la Fase 7 con uno de pub.
     */
    @JvmStatic
    fun hasRealResolution(flutterRoot: File): Boolean {
        val config = FlutterToolchainPaths.packageConfigFile(flutterRoot)
        if (!config.isFile) {
            return false
        }
        return readGenerator(config) == GENERATOR_PUB
    }

    /** `generator` del `package_config.json`, o `null` si no se puede leer. */
    @JvmStatic
    fun readGenerator(packageConfigFile: File): String? {
        return try {
            val root = JsonParser.parseString(packageConfigFile.readText()).asJsonObject
            if (root.has("generator")) root.get("generator").asString else null
        } catch (e: Exception) {
            Log.w(TAG, "package_config.json ilegible: ${e.message}")
            null
        }
    }

    /** Paquetes de un `package_config.json` (real o sintetico; el formato es el mismo). */
    @JvmStatic
    fun readResolvedPackages(packageConfigFile: File): List<ResolvedPackage> {
        if (!packageConfigFile.isFile) {
            return emptyList()
        }
        return try {
            val root = JsonParser.parseString(packageConfigFile.readText()).asJsonObject
            val packages = root.getAsJsonArray("packages") ?: return emptyList()
            packages.mapNotNull { element ->
                val entry = element.asJsonObject
                val name = entry.get("name")?.asString ?: return@mapNotNull null
                val rootUri = entry.get("rootUri")?.asString ?: return@mapNotNull null
                val directory = File(rootUri.removePrefix("file://"))
                val version = readPackageVersion(directory)
                ResolvedPackage(
                    name = name,
                    version = version,
                    rootDirectory = directory,
                    languageVersion = entry.get("languageVersion")?.asString ?: "",
                    // Los paquetes de SDK (flutter, sky_engine, flutter_test) no viven en el
                    // PubCache sino en el Flutter SDK sintetico.
                    fromSdk = !directory.path.contains("/pub-cache/"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los paquetes de ${packageConfigFile.absolutePath}: ${e.message}")
            emptyList()
        }
    }

    /**
     * `version:` del `pubspec.yaml` de un paquete resuelto (los paquetes del SDK no lo declaran).
     */
    @JvmStatic
    fun readPackageVersion(directory: File): String {
        val pubspec = File(directory, "pubspec.yaml")
        if (!pubspec.isFile) {
            return ""
        }
        for (line in pubspec.readLines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("version:") && !line.startsWith(" ")) {
                return trimmed.substringAfter(':').trim().trim('"', '\'')
            }
        }
        return ""
    }

    /**
     * Prepara el Flutter SDK **sintetico** (`FLUTTER_ROOT`) que pub exige para resolver
     * `flutter: {sdk: flutter}`. Idempotente y barato si ya esta (solo escribe ficheros pequenos).
     *
     * @param progress mensajes de progreso en espanol.
     * @return el directorio `flutter-root`, o `null` si falta el framework Dart (el padre debe
     *   descargarlo: `FlutterEngineArtifacts`, carril A2).
     */
    @JvmStatic
    fun prepareFlutterRoot(context: Context, progress: (String) -> Unit): File? {
        val root = FlutterToolchainPaths.syntheticFlutterRoot(context)
        val frameworkSource = FlutterToolchainPaths.frameworkDir(context)
        if (!File(frameworkSource, "lib").isDirectory) {
            progress(
                "Falta el framework Dart en ${frameworkSource.absolutePath}: sin el no se pueden " +
                    "resolver las dependencias de Flutter (descarga los artefactos del engine)."
            )
            return null
        }

        val packagesDir = FlutterToolchainPaths.syntheticFlutterPackagesDir(context)
        if (!packagesDir.mkdirs() && !packagesDir.isDirectory) {
            progress("No se pudo crear ${packagesDir.absolutePath}")
            return null
        }

        // 1) `packages/flutter`: copia del framework descargado (fuentes + pubspec limpiado).
        val flutterPackage = FlutterToolchainPaths.syntheticFlutterFrameworkDir(context)
        val flutterLib = File(flutterPackage, "lib")
        if (!File(flutterLib, "material.dart").isFile) {
            progress("Copiando el framework Dart a ${flutterPackage.absolutePath}...")
            if (!flutterLib.mkdirs() && !flutterLib.isDirectory) {
                progress("No se pudo crear ${flutterLib.absolutePath}")
                return null
            }
            frameworkSource.copyRecursively(flutterPackage, overwrite = true)
        }
        val frameworkPubspec = File(frameworkSource, "pubspec.yaml")
        val cleanedFrameworkPubspec = if (frameworkPubspec.isFile) {
            cleanSdkPubspec(frameworkPubspec.readText())
        } else {
            null
        }
        if (cleanedFrameworkPubspec != null) {
            File(flutterPackage, "pubspec.yaml").writeText(cleanedFrameworkPubspec)
        }

        // 2) Resto de paquetes `sdk: flutter` (flutter_test, flutter_web_plugins…).
        for (name in SDK_PACKAGES_WITH_TEMPLATE) {
            val target = File(packagesDir, name)
            target.mkdirs()
            val fromTemplate = FlutterToolchainPaths.flutterSdkPackagePubspec(name) ?: continue
            val targetPubspec = File(target, "pubspec.yaml")
            if (!targetPubspec.isFile) {
                targetPubspec.writeText(fromTemplate)
            }
        }

        // 3) `sky_engine` + version.json + version + engine.version.
        val skyEngine = FlutterToolchainPaths.syntheticSkyEngineDir(context)
        val skyEngineUi = File(File(skyEngine, "lib/ui"), "ui.dart")
        if (!skyEngineUi.isFile) {
            File(File(skyEngine, "lib/ui"), "").mkdirs()
            val fromTemplate = FlutterToolchainPaths.flutterSdkPackagePubspec("sky_engine")
            if (fromTemplate != null) {
                File(skyEngine, "pubspec.yaml").writeText(fromTemplate)
            }
            skyEngineUi.writeText(FlutterToolchainPaths.skyEngineStubDart())
        }

        val versionJson = FlutterToolchainPaths.flutterVersionJsonFile(context)
        if (!versionJson.isFile) {
            versionJson.parentFile?.mkdirs()
            versionJson.writeText(FlutterToolchainPaths.flutterVersionJson())
        }
        val versionFile = FlutterToolchainPaths.syntheticFlutterVersionFile(context)
        if (!versionFile.isFile) {
            versionFile.writeText(FlutterToolchainPaths.FLUTTER_VERSION + "\n")
        }
        val engineVersion = FlutterToolchainPaths.syntheticEngineVersionFile(context)
        if (!engineVersion.isFile) {
            engineVersion.parentFile?.mkdirs()
            engineVersion.writeText(FlutterToolchainPaths.ENGINE_VERSION + "\n")
        }

        return root
    }

    /**
     * Ejecuta `dart pub get` en el proyecto del usuario y devuelve el resultado.
     *
     * Entorno que se fija al proceso (nunca el HOME real del usuario):
     * - `PUB_CACHE` -> [FlutterToolchainPaths.pubCacheDir] (almacenamiento privado de la app),
     * - `HOME`      -> [FlutterToolchainPaths.pubHomeDir],
     * - `TMPDIR`    -> [FlutterToolchainPaths.pubTempDir],
     * - `FLUTTER_ROOT` -> [prepareFlutterRoot] (solo si el pubspec declara alguna dependencia
     *   `sdk: flutter` o `dev_dependencies` con `flutter_test`),
     * - `PUB_HOSTED_URL` -> [hostedUrl] si no es nulo (mirror/proxy corporativo).
     *
     * @param offline si es `true` se pasa `--offline` (usa solo el `PUB_CACHE`, sin red).
     * @param force si es `false` y la resolucion cacheada sigue vigente, no se ejecuta pub.
     */
    @JvmStatic
    @JvmOverloads
    fun resolve(
        context: Context,
        flutterRoot: File,
        progress: (String) -> Unit,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        offline: Boolean = false,
        force: Boolean = false,
        hostedUrl: String? = null,
    ): PubResolveResult {
        val startedAt = System.currentTimeMillis()
        val log = StringBuilder()

        fun fail(kind: ErrorKind, message: String, exitCode: Int = -1): PubResolveResult {
            Log.w(TAG, message)
            log.append("[error] ").append(message).append('\n')
            appendPubLog(flutterRoot, log.toString())
            return PubResolveResult(
                false, null, emptyList(), kind, message, log.toString(), exitCode,
                false, System.currentTimeMillis() - startedAt,
            )
        }

        val dartAotRuntime = FlutterToolchainPaths.dartAotRuntimeExecutable(context)
        val dartDevSnapshot = FlutterToolchainPaths.dartDevSnapshot(context)
        if (!dartAotRuntime.isFile || !dartDevSnapshot.isFile) {
            return fail(
                ErrorKind.NO_TOOLCHAIN,
                "El SDK Dart no esta instalado en el dispositivo (o le falta el cliente de pub). " +
                    "Abre el menu Flutter del editor y pulsa \"Instalar toolchain\": sin el no se " +
                    "pueden resolver las dependencias ni compilar. Se esperaba " +
                    "${dartAotRuntime.absolutePath} y ${dartDevSnapshot.absolutePath}.",
            )
        }

        val pubspec = File(flutterRoot, "pubspec.yaml")
        if (!pubspec.isFile) {
            return fail(ErrorKind.NO_PUBSPEC, "No hay pubspec.yaml en ${flutterRoot.absolutePath}")
        }

        if (!force && isResolutionFresh(flutterRoot)) {
            val config = FlutterToolchainPaths.packageConfigFile(flutterRoot)
            val packages = readResolvedPackages(config)
            log.append("[cache] package_config.json de pub vigente (").append(packages.size)
                .append(" paquetes); no se ejecuta pub get\n")
            return PubResolveResult(
                true, config, packages, ErrorKind.NONE, "", log.toString(), EXIT_OK,
                usedNetwork = false, durationMs = System.currentTimeMillis() - startedAt,
            )
        }

        // FLUTTER_ROOT: solo hace falta si el proyecto (o su lock) menciona el SDK de Flutter.
        var needsFlutterRoot = pubspecNeedsFlutterSdk(pubspec)
        if (!needsFlutterRoot) {
            needsFlutterRoot = FlutterToolchainPaths.pubLockFile(flutterRoot).isFile &&
                FlutterToolchainPaths.pubLockFile(flutterRoot).readText().contains("sdk: flutter")
        }

        val environment = HashMap<String, String>()
        val pubCache = FlutterToolchainPaths.pubCacheDir(context)
        pubCache.mkdirs()
        val pubHome = FlutterToolchainPaths.pubHomeDir(context)
        pubHome.mkdirs()
        val pubTemp = FlutterToolchainPaths.pubTempDir(context)
        pubTemp.mkdirs()
        environment["PUB_CACHE"] = pubCache.absolutePath
        environment["HOME"] = pubHome.absolutePath
        environment["TMPDIR"] = pubTemp.absolutePath
        if (!hostedUrl.isNullOrBlank()) {
            environment["PUB_HOSTED_URL"] = hostedUrl
        }
        if (needsFlutterRoot) {
            progress("Preparando el Flutter SDK sintetico (FLUTTER_ROOT)...")
            val flutterRootDir = prepareFlutterRoot(context, progress)
                ?: return fail(
                    ErrorKind.MISSING_FLUTTER_SDK,
                    "El proyecto depende del SDK de Flutter (`flutter: {sdk: flutter}`) y no hay " +
                        "framework Dart instalado, asi que pub no puede resolver. Instala los " +
                        "artefactos del engine (menu Flutter) y vuelve a intentarlo.",
                )
            environment["FLUTTER_ROOT"] = flutterRootDir.absolutePath
        }

        // El snapshot de `dartdev` (el cliente de pub) se lanza con el `dartaotruntime` de
        // nativeLibraryDir; el snapshot va como PRIMER argumento (es dato, no ejecutable).
        val command = mutableListOf(dartAotRuntime.absolutePath, dartDevSnapshot.absolutePath, "pub", "get")
        if (offline) {
            command.add("--offline")
        }
        if (!hostedUrl.isNullOrBlank()) {
            log.append("[info] PUB_HOSTED_URL=").append(hostedUrl).append('\n')
        }

        progress(
            if (offline) "Resolviendo dependencias (pub get --offline)..." else
                "Resolviendo dependencias (dart pub get; necesita red)..."
        )
        log.append("$ ").append(command.joinToString(" ")).append('\n')
        log.append("[env] PUB_CACHE=").append(environment["PUB_CACHE"])
            .append(" HOME=").append(environment["HOME"])
            .append(" FLUTTER_ROOT=").append(environment["FLUTTER_ROOT"] ?: "(sin SDK de Flutter)")
            .append('\n')

        val processLogs = StringBuilder()
        var process = FlutterDartCompiler.runCommand(
            command = command,
            workingDirectory = flutterRoot,
            timeoutMs = timeoutMs,
            environment = environment,
        )
        processLogs.append(process.output)

        // Fallo conocido de pub cuando dos procesos comparten el mismo PUB_CACHE: dejan
        // `<PUB_CACHE>/_temp/dirXXXX` a medias y el rename falla con
        // `Rename failed, path = '…/_temp/dirCYHRSO' (OS Error: Directory not empty, errno = 39)`.
        // Reproducido en el emulador ejecutando dos `pub get` a la vez. Se limpia el temporal y se
        // reintenta UNA vez (el fork serializa las compilaciones, asi que en produccion no deberia
        // darse; esto lo hace inofensivo si se da).
        if (!process.timedOut && process.exitCode != EXIT_OK && isCacheCorruption(process.output)) {
            progress("La cache de pub quedo a medias; limpiando y reintentando...")
            processLogs.append("[warn] PUB_CACHE inconsistente: se limpia _temp y se reintenta\n")
            cleanPubTemp(context, pubCache)
            process = FlutterDartCompiler.runCommand(
                command = command,
                workingDirectory = flutterRoot,
                timeoutMs = timeoutMs,
                environment = environment,
            )
            processLogs.append(process.output)
        }
        log.append(processLogs)
        if (process.timedOut) {
            return fail(
                ErrorKind.TIMEOUT,
                "`dart pub get` supero el tiempo maximo (${timeoutMs / 1000} s). Comprueba la " +
                    "conexion y vuelve a intentarlo.",
            )
        }

        if (process.exitCode != EXIT_OK) {
            // Caso tipico cuando el `dartaotruntime` resuelto esta en filesDir (APK de otra ABI):
            // java.io.IOException: error=13, Permission denied (SELinux execute_no_trans).
            if (looksLikeExecDenied(processLogs.toString()) &&
                !FlutterToolchainPaths.isDartAotRuntimePackaged(context)
            ) {
                return fail(
                    ErrorKind.NO_TOOLCHAIN,
                    "No se puede ejecutar `dartaotruntime` desde ${dartAotRuntime.absolutePath}: SELinux " +
                        "solo permite ejecutar binarios de `nativeLibraryDir`, y esta instalacion " +
                        "(ABI '${FlutterToolchainPaths.deviceAbiName()}') no empaqueta los ejecutables " +
                        "de Flutter. Instala una variante con ejecutables (arm64-v8a o x86_64).",
                    process.exitCode,
                )
            }
            val kind = classifyFailure(process.exitCode, process.output, offline)
            return fail(kind, describeFailure(kind), process.exitCode)
        }

        val config = FlutterToolchainPaths.packageConfigFile(flutterRoot)
        if (!config.isFile) {
            return fail(
                ErrorKind.UNKNOWN,
                "pub termino bien pero no genero .dart_tool/package_config.json. Log:\n" +
                    process.output.takeLast(1500),
                process.exitCode,
            )
        }
        if (readGenerator(config) != GENERATOR_PUB) {
            log.append("[warn] el package_config.json no declara generator=pub; se regenerara.\n")
        }

        val packages = readResolvedPackages(config)
        writeGenerationMarker(flutterRoot, pubspec)
        appendPubLog(flutterRoot, log.toString())
        progress("pub resolvio ${packages.size} paquetes")
        Log.i(TAG, "pub get OK: ${packages.size} paquetes en ${process.durationMs} ms")

        return PubResolveResult(
            true, config, packages, ErrorKind.NONE, "", log.toString(), process.exitCode,
            usedNetwork = !offline, durationMs = System.currentTimeMillis() - startedAt,
        )
    }

    /**
     * `true` si la resolucion cacheada sigue siendo valida: existe `package_config.json` de pub y el
     * `pubspec.yaml` no ha cambiado (hash guardado en la marca) y el `pubspec.lock` esta.
     */
    @JvmStatic
    fun isResolutionFresh(flutterRoot: File): Boolean {
        if (!hasRealResolution(flutterRoot)) {
            return false
        }
        val marker = FlutterToolchainPaths.pubGenerationMarker(flutterRoot)
        val pubspec = File(flutterRoot, "pubspec.yaml")
        if (!marker.isFile || !pubspec.isFile) {
            return false
        }
        val savedHash = marker.readLines().firstOrNull { it.startsWith("pubspec.yaml=") }
            ?.substringAfter('=') ?: return false
        return savedHash == sha256(pubspec.readText())
    }

    /**
     * `true` si el texto del fallo es un `Permission denied` de `execve` (SELinux
     * `execute_no_trans`), que es lo que ocurre al lanzar un ELF de `filesDir` en `targetSdk >= 29`.
     */
    @JvmStatic
    fun looksLikeExecDenied(output: String): Boolean {
        val text = output.lowercase()
        return text.contains("permission denied") ||
            text.contains("error=13") ||
            text.contains("cannot run program")
    }

    /**
     * `true` si la salida de pub indica una cache a medias (`_temp` a medio renombrar).
     *
     * Evidencia cruda del emulador (dos `pub get` simultaneos sobre el mismo `PUB_CACHE`):
     * `Rename failed, path = '/data/local/tmp/puev/cache/_temp/dirCYHRSO'
     * (OS Error: Directory not empty, errno = 39)`.
     */
    @JvmStatic
    fun isCacheCorruption(output: String): Boolean {
        val text = output.lowercase()
        return text.contains("rename failed") ||
            (text.contains("_temp") && text.contains("directory not empty"))
    }

    /** Borra `<PUB_CACHE>/_temp` (descargas a medias) sin tocar los paquetes ya instalados. */
    @JvmStatic
    fun cleanPubTemp(context: Context, pubCache: File) {
        val tempDir = File(pubCache, "_temp")
        try {
            FlutterToolchainInstaller.deleteRecursively(tempDir)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo limpiar ${tempDir.absolutePath}: ${e.message}")
        }
    }

    /** `true` si el `pubspec.yaml` declara `sdk: flutter` en dependencies o dev_dependencies. */
    @JvmStatic
    fun pubspecNeedsFlutterSdk(pubspec: File): Boolean {
        if (!pubspec.isFile) {
            return false
        }
        var inDependencies = false
        for (line in pubspec.readLines()) {
            val trimmed = line.trim()
            if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("#")) {
                inDependencies = trimmed.startsWith("dependencies:") ||
                    trimmed.startsWith("dev_dependencies:") ||
                    trimmed.startsWith("dependency_overrides:")
                continue
            }
            if (inDependencies && trimmed == "sdk: flutter") {
                return true
            }
        }
        return false
    }

    /**
     * Limpia un `pubspec.yaml` del SDK de Flutter para que pub lo acepte fuera del monorepo:
     * quita `resolution: workspace` (necesita el pubspec raiz con `workspace:`) y el bloque
     * `dev_dependencies:` (no participa en la resolucion del proyecto del usuario).
     */
    @JvmStatic
    fun cleanSdkPubspec(content: String): String {
        val result = StringBuilder()
        var skip = false
        for (line in content.lineSequence()) {
            if (line.startsWith("dev_dependencies:")) {
                skip = true
                continue
            }
            if (skip) {
                if (line.isEmpty() || !line[0].isWhitespace()) {
                    skip = false
                } else {
                    continue
                }
            }
            if (line.trim().startsWith("resolution:")) {
                continue
            }
            if (line.trim().startsWith("# PUBSPEC CHECKSUM")) {
                continue
            }
            result.append(line).append('\n')
        }
        return result.toString()
    }

    /* -------------------------------------------------------------------------------------- */
    /* Diagnostico de errores                                                                   */
    /* -------------------------------------------------------------------------------------- */

    /**
     * Traduce el fallo de pub a [ErrorKind]. Se apoya en los mensajes que pub imprime de verdad
     * (comprobados en el dispositivo: version solving, "Flutter SDK is not available", etc.), no en
     * suposiciones.
     */
    @JvmStatic
    fun classifyFailure(exitCode: Int, output: String, offline: Boolean): ErrorKind {
        val text = output.lowercase()
        return when {
            text.contains("failed host lookup") ||
                text.contains("socketexception") ||
                // Mensaje real de pub sin salida a red (comprobado en el emulador con
                // PUB_HOSTED_URL=http://127.0.0.1:1): "Got socket error trying to find package
                // collection at http://127.0.0.1:1."
                text.contains("socket error") ||
                text.contains("connection refused") ||
                text.contains("connection closed") ||
                text.contains("network is unreachable") ||
                text.contains("software caused connection abort") ||
                text.contains("handshakeexception") ->
                if (offline) ErrorKind.OFFLINE_MISSING else ErrorKind.NO_NETWORK

            // Mensaje real de `--offline` sin el paquete en cache (comprobado en el emulador):
            // "Because prueba_pub depends on collection any which doesn't exist (could not find
            //  package collection in cache), version solving failed. Try again without --offline!"
            // Va ANTES de la rama de "version solving failed" porque ese mensaje tambien la contiene.
            text.contains("without --offline") ||
                (text.contains("could not find package") && text.contains("in cache")) ->
                ErrorKind.OFFLINE_MISSING

            text.contains("429") || text.contains("too many requests") -> ErrorKind.RATE_LIMITED

            text.contains("proxy") || text.contains("407") || text.contains("502 bad gateway") ->
                ErrorKind.PROXY

            text.contains("version solving failed") -> ErrorKind.VERSION_SOLVING

            text.contains("flutter sdk is not available") ||
                (text.contains("from sdk which doesn't exist") &&
                    text.contains("flutter")) -> ErrorKind.MISSING_FLUTTER_SDK

            text.contains("requires sdk version") || text.contains("the current dart sdk") ->
                ErrorKind.SDK_CONSTRAINT

            text.contains("could not find package") && text.contains("in the flutter sdk") ->
                ErrorKind.MISSING_FLUTTER_SDK

            (text.contains("error on line") || text.contains("expected a key") ||
                text.contains("invalid yaml") ||
                (text.contains("pubspec.yaml") && text.contains("parsing"))) ->
                ErrorKind.PUBSPEC_INVALID

            exitCode == EXIT_UNAVAILABLE -> ErrorKind.VERSION_SOLVING
            exitCode == EXIT_BAD_DATA -> ErrorKind.PUBSPEC_INVALID
            exitCode == EXIT_NO_INPUT -> ErrorKind.NO_PUBSPEC
            exitCode == EXIT_TEMP_FAIL -> ErrorKind.NO_NETWORK
            exitCode == EXIT_NO_PERM -> ErrorKind.UNKNOWN
            else -> ErrorKind.UNKNOWN
        }
    }

    /** Mensaje en espanol para [kind] (lo que ve el usuario). */
    @JvmStatic
    fun describeFailure(kind: ErrorKind): String = when (kind) {
        ErrorKind.NONE ->
            ""

        ErrorKind.NO_TOOLCHAIN ->
            "El SDK Dart no esta instalado en el dispositivo: instala el toolchain Flutter desde el " +
                "menu Flutter del editor."

        ErrorKind.NO_PUBSPEC ->
            "No se encontro `pubspec.yaml` en el proyecto. Crea o abre un proyecto Flutter."

        ErrorKind.NO_NETWORK ->
            "Sin conexion con pub.dev: `dart pub get` necesita red para Bajar los paquetes que no " +
                "estan en la cache. Comprueba el WiFi/datos y vuelve a intentarlo; si ya los " +
                "descargaste antes, puedes usar la resolucion en modo sin conexion."

        ErrorKind.PROXY ->
            "El proxy o mirror de pub no responde correctamente. Si usas un mirror corporativo, " +
                "configura PUB_HOSTED_URL; si estas detras de un proxy que corta HTTPS, pub no puede " +
                "descargar los paquetes."

        ErrorKind.RATE_LIMITED ->
            "pub.dev ha respondido 429 (demasiadas peticiones). Espera un par de minutos y vuelve a " +
                "intentarlo."

        ErrorKind.VERSION_SOLVING ->
            "No hay una combinacion de versiones compatible entre tus dependencias (conflicto de " +
                "versiones). Revisa los `^x.y.z` de `pubspec.yaml`; la salida de pub indica que " +
                "paquetes chocan."

        ErrorKind.MISSING_FLUTTER_SDK ->
            "El proyecto depende del SDK de Flutter (`flutter: {sdk: flutter}` o `flutter_test`) y el " +
                "Flutter SDK instalado no incluye ese paquete. Instala/actualiza los artefactos del " +
                "engine desde el menu Flutter."

        ErrorKind.SDK_CONSTRAINT ->
            "La restriccion `environment: sdk:` de `pubspec.yaml` no la cumple el Dart " +
                "${FlutterToolchainPaths.DART_VERSION} del dispositivo. Ajusta el rango (por ejemplo " +
                "`>=3.1.0 <4.0.0`)."

        ErrorKind.PUBSPEC_INVALID ->
            "`pubspec.yaml` tiene un error de sintaxis o un campo invalido. La salida de pub indica " +
                "la linea exacta."

        ErrorKind.TIMEOUT ->
            "`dart pub get` tardo demasiado. Vuelve a intentarlo con mejor conexion."

        ErrorKind.OFFLINE_MISSING ->
            "Modo sin conexion: falta algun paquete en la cache de pub. Conectate a internet y " +
                "ejecuta la resolucion normal una vez."

        ErrorKind.UNKNOWN ->
            "`dart pub get` fallo. Revisa el log completo (build/pub_get.log)."
    }

    /* -------------------------------------------------------------------------------------- */
    /* Auxiliares                                                                               */
    /* -------------------------------------------------------------------------------------- */

    /** Paquetes `sdk: flutter` con plantilla propia en [FlutterToolchainPaths]. */
    private val SDK_PACKAGES_WITH_TEMPLATE = listOf(
        "flutter_test",
        "flutter_web_plugins",
        "flutter_localizations",
    )

    /** Escribe `build/pub_get.log` con la salida cruda (para el usuario y para depurar). */
    private fun appendPubLog(flutterRoot: File, content: String) {
        try {
            val logFile = FlutterToolchainPaths.pubLogFile(flutterRoot)
            logFile.parentFile?.mkdirs()
            logFile.writeText(content)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir ${FlutterToolchainPaths.pubLogFile(flutterRoot)}: ${e.message}")
        }
    }

    /**
     * Marca de resolucion (hash del pubspec + resumen). Se usa para no re-ejecutar pub si nada cambia.
     */
    private fun writeGenerationMarker(flutterRoot: File, pubspec: File) {
        try {
            val marker = FlutterToolchainPaths.pubGenerationMarker(flutterRoot)
            marker.parentFile?.mkdirs()
            val lock = FlutterToolchainPaths.pubLockFile(flutterRoot)
            marker.writeText(
                buildString {
                    append(MARKER_HEADER).append('\n')
                    append("dart=").append(FlutterToolchainPaths.DART_VERSION).append('\n')
                    append("pubspec.yaml=").append(sha256(pubspec.readText())).append('\n')
                    append("pubspec.lock=").append(if (lock.isFile) sha256(lock.readText()) else "-")
                        .append('\n')
                    append("generated=").append(System.currentTimeMillis()).append('\n')
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir la marca de pub: ${e.message}")
        }
    }

    /** sha256 en hexadecimal (mismo algoritmo que [FlutterToolchainInstaller]). */
    @JvmStatic
    fun sha256(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            builder.append(Character.forDigit((byte.toInt() shr 4) and 0xF, 16))
            builder.append(Character.forDigit(byte.toInt() and 0xF, 16))
        }
        return builder.toString()
    }

    /** `package_config.json` como `JsonObject` (utilidad para el carril C/plugins). */
    @JvmStatic
    fun readPackageConfig(packageConfigFile: File): JsonObject? {
        return try {
            JsonParser.parseString(packageConfigFile.readText()).asJsonObject
        } catch (e: Exception) {
            null
        }
    }
}
