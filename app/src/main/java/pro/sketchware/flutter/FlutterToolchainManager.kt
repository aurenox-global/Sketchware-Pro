package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Estado real de un ejecutable del toolchain (lo que ve [FlutterToolchainManager.isReady]).
 *
 * [runnable] NO se deduce de los permisos del fichero: se obtiene **ejecutandolo**
 * ([FlutterToolchainInstaller.probeExecutable]). Es la unica forma de saber si SELinux deja
 * ejecutar ese ELF desde donde esta (informe AOT §6.1).
 */
class ToolchainExecutableStatus(
    val name: String,
    val path: String,
    /** `true` si esta en `nativeLibraryDir` (unica ubicacion ejecutable para la app). */
    val packaged: Boolean,
    val exists: Boolean,
    val runnable: Boolean,
    /** Primera linea de `--version`, si se pudo ejecutar. */
    val versionLine: String?,
    /** Motivo exacto del fallo de ejecucion (null si [runnable] o si ni existe). */
    val failureReason: String? = null,
) {
    override fun toString(): String {
        val state = when {
            !exists -> "ausente"
            runnable -> "ejecutable"
            else -> "NO-ejecutable" + (failureReason?.let { " {$it}" } ?: "")
        }
        return "$name=$state(${if (packaged) "nativeLibraryDir" else "filesDir"})"
    }
}

/**
 * Fachada del toolchain Flutter on-device (contrato Fase 7, carril C; ejecucion desde
 * `nativeLibraryDir` en la Fase 8, carril I).
 *
 * Punto de entrada para la UI (carril B2): [installedDartVersion], [isReady], [ensureInstalled].
 * Todos los métodos bloqueantes deben llamarse en un hilo de fondo.
 */
object FlutterToolchainManager {

    private const val TAG = "FlutterToolchain"

    /**
     * Cache de la ultima sonda de ejecutables, clave = rutas+tamanos. Evita ejecutar procesos en
     * cada llamada a [isReady] (la UI la consulta al pintar), pero sigue siendo una comprobacion
     * real: si el binario cambia de tamano (reinstalacion, otro modo), se vuelve a sondear.
     */
    @Volatile
    private var probeCache: Pair<String, List<ToolchainExecutableStatus>>? = null

    /** `<filesDir>/flutter-toolchain`. */
    @JvmStatic
    fun toolchainDir(context: Context): File = FlutterToolchainPaths.toolchainDir(context)

    /**
     * Version de Dart instalada (**datos**), o `null` si el SDK Dart no esta extraido.
     *
     * Criterio de datos, que es lo que deja el `.deb`: marcador + los snapshots AOT que la app lanza
     * (`gen_kernel_aot.dart.snapshot`, `dartdev_aot.dart.snapshot`) + `lib/_internal/vm_platform.dill`.
     *
     * NO se exige poder ejecutar `bin/dart`: es el CLI del `.deb`, vive en `filesDir` y SELinux
     * prohibe ejecutarlo (W^X), ademas de que la app no lo usa. La version sale del marcador (dato
     * verificado del paquete) y, si faltara, de la sonda real
     * ([FlutterToolchainInstaller.probeDartRuntime], que ejecuta `libdartaotruntime.so --version`
     * desde `nativeLibraryDir`).
     *
     * Si los datos estan pero los ejecutables no arrancan, lo dice [executableBlockerReason]; este
     * metodo nunca reporta "no instalado" por eso.
     */
    @JvmStatic
    fun installedDartVersion(context: Context): String? {
        if (missingDartData(context).isNotEmpty()) {
            return null
        }

        val marker = FlutterToolchainPaths.dartMarkerFile(context)
        if (marker.isFile) {
            val version = marker.readLines()
                .firstOrNull { it.startsWith("version=") }
                ?.substringAfter('=')
                ?.trim()
            if (!version.isNullOrEmpty()) {
                return version
            }
        }

        // Marcador sin version: la del runtime que de verdad se ejecuta, o la del paquete esperado.
        return FlutterToolchainInstaller.probeDartRuntime(context).versionLine
            ?: FlutterToolchainPaths.DART_VERSION
    }

    /**
     * Datos del SDK Dart que faltan en disco (lista vacia si estan todos).
     *
     * Son los ficheros que el instalador extrae del `.deb` y que la app necesita en tiempo de build:
     * sin ellos el SDK no esta instalado, con ellos si (independientemente de si `bin/dart` arranca o
     * no, que es otra pregunta: [executableBlockerReason]).
     */
    @JvmStatic
    fun missingDartData(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!FlutterToolchainPaths.dartMarkerFile(context).isFile) {
            missing.add("installed.properties (marcador de la extraccion)")
        }
        if (!FlutterToolchainPaths.genKernelSnapshot(context).isFile) {
            missing.add("bin/snapshots/gen_kernel_aot.dart.snapshot")
        }
        if (!FlutterToolchainPaths.dartDevSnapshot(context).isFile) {
            missing.add("bin/snapshots/dartdev_aot.dart.snapshot")
        }
        if (!FlutterToolchainPaths.vmPlatformDill(context).isFile) {
            missing.add("lib/_internal/vm_platform.dill")
        }
        return missing
    }

    /** ABI del dispositivo soportada por el toolchain, o `null`. */
    @JvmStatic
    fun deviceAbi(): String? = FlutterToolchainPaths.resolveSupportedAbi()

    /**
     * Sondas **ejecutadas** de los dos ejecutables (y de sus copias en `filesDir`, para poder
     * explicar en el log por que fallan). Resultado memoizado por ruta+tamano.
     */
    @JvmStatic
    fun probeExecutables(context: Context): List<ToolchainExecutableStatus> {
        val candidates = mutableListOf<Pair<String, File>>()
        val packagedRuntime = FlutterToolchainPaths.packagedExecutable(
            context, FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME
        )
        if (packagedRuntime != null) {
            candidates.add(FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME to packagedRuntime)
        } else {
            candidates.add(
                FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME to FlutterToolchainPaths.dartAotRuntime(context)
            )
        }
        val packagedGenSnapshot = FlutterToolchainPaths.packagedExecutable(
            context, FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT
        )
        if (packagedGenSnapshot != null) {
            candidates.add(FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT to packagedGenSnapshot)
        } else if (FlutterToolchainPaths.genSnapshot(context).isFile) {
            candidates.add(
                FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT to FlutterToolchainPaths.genSnapshot(context)
            )
        }

        val key = candidates.joinToString("|") { "${it.second.absolutePath}:${it.second.length()}" }
        probeCache?.let { (cachedKey, cachedValue) -> if (cachedKey == key) return cachedValue }

        val results = candidates.map { (name, file) ->
            val probe = if (file.isFile) {
                FlutterToolchainInstaller.probeExecutableResult(file)
            } else {
                FlutterToolchainInstaller.ExecProbeResult(null, null)
            }
            val version = probe.output
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
            ToolchainExecutableStatus(
                name = name,
                path = file.absolutePath,
                packaged = FlutterToolchainPaths.nativeLibraryDir(context)?.let { dir ->
                    file.absolutePath.startsWith(dir.absolutePath)
                } == true,
                exists = file.isFile,
                runnable = version != null,
                versionLine = version,
                failureReason = if (file.isFile) probe.failure else null,
            )
        }
        probeCache = key to results
        return results
    }

    /** Sonda de un nombre concreto (`libdartaotruntime.so` / `libfluttergensnapshot.so`). */
    @JvmStatic
    fun probeExecutable(context: Context, name: String): ToolchainExecutableStatus? =
        probeExecutables(context).firstOrNull { it.name == name }

    /**
     * `true` si dart + engine + framework Dart del [FlutterBuildMode] indicado están completos **y**
     * los ejecutables que ese modo necesita se pueden ejecutar de verdad.
     *
     * - los datos del SDK Dart tienen que estar extraidos ([installedDartVersion]);
     * - los dos modos necesitan que `dartaotruntime` **arranque** (lanza `gen_kernel` y `pub`);
     * - [FlutterBuildMode.RELEASE_AOT] necesita ademas nuestro `gen_snapshot` y la plataforma
     *   `flutter_patched_sdk_product`.
     *
     * La sonda es una ejecucion real (no permisos): el APK puede traer todos los datos y aun asi no
     * poder compilar si no empaqueta los binarios de su ABI (ver [executableBlockerReason]).
     */
    @JvmStatic
    fun isReady(context: Context, mode: FlutterBuildMode): Boolean {
        if (installedDartVersion(context) == null) {
            return false
        }
        if (!FlutterEngineArtifacts.areArtifactsReady(context, mode)) {
            return false
        }
        return executableBlockerReason(context, mode) == null
    }

    /**
     * Motivo (**que pieza** y **por que**) por el que los ejecutables del toolchain no se pueden
     * lanzar para [mode], o `null` si todo lo que ese modo necesita se ejecuta de verdad.
     *
     * Distingue el caso "no esta" del caso "esta pero SELinux no lo deja ejecutar" (W^X), y nombra
     * siempre la ruta concreta, para que el mensaje sea accionable.
     */
    @JvmStatic
    fun executableBlockerReason(context: Context, mode: FlutterBuildMode): String? {
        val runtime = probeExecutable(context, FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME)
        if (runtime == null || !runtime.exists) {
            return "Falta el ejecutable `dartaotruntime` (ni " +
                "nativeLibraryDir/${FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME} ni " +
                "${FlutterToolchainPaths.dartAotRuntime(context).absolutePath}): instala el toolchain " +
                "desde el menu Flutter del editor."
        }
        if (!runtime.runnable) {
            return "`dartaotruntime` esta en ${runtime.path} pero NO se puede ejecutar " +
                "(${runtime.failureReason ?: "motivo desconocido"}). SELinux solo deja ejecutar " +
                "binarios de nativeLibraryDir con targetSdk >= 29, y este APK (ABI " +
                "'${FlutterToolchainPaths.deviceAbiName()}') no empaqueta " +
                "lib/<abi>/${FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME}. " +
                "Instala una variante con ejecutables (arm64-v8a o x86_64): el SDK Dart ya esta en " +
                "disco, no se vuelve a descargar."
        }
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            FlutterToolchainPaths.aotBackendUnavailableReason(context)?.let { return it }
            val genSnapshot = probeExecutable(context, FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT)
            if (genSnapshot == null || !genSnapshot.exists) {
                return "Falta nativeLibraryDir/${FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT} (nuestro " +
                    "`gen_snapshot` product, el unico que produce un `libapp.so` que el engine release " +
                    "acepta): usa el modo DEBUG_JIT o instala una variante con ejecutables " +
                    "(arm64-v8a o x86_64)."
            }
            if (!genSnapshot.runnable) {
                return "`gen_snapshot` esta en ${genSnapshot.path} pero NO se puede ejecutar " +
                    "(${genSnapshot.failureReason ?: "motivo desconocido"}): SELinux solo permite " +
                    "ejecutar desde nativeLibraryDir (W^X)."
            }
        }
        return null
    }

    /** Igual que [isReady] pero con el modo por defecto (el unico que compila hoy: DEBUG_JIT). */
    @JvmStatic
    fun isReady(context: Context): Boolean = isReady(context, FlutterProjectDefaults.DEFAULT_MODE)

    /**
     * Descarga/extrae lo que falte (SDK Dart + artefactos del engine del [mode] + framework
     * Dart + dependencias de pub). Bloqueante.
     *
     * El modo importa: el engine debug (JIT) y el release (AOT) son artefactos distintos, y el
     * jar del embedding trae el `BuildConfig` que decide cual de los dos caminos toma el runtime.
     *
     * @param progress callback de progreso, se invoca desde el hilo llamante.
     *
     * NOTA (consentimiento): esta variante **no descarga**: si falta algo registra
     * [MESSAGE_TOOLCHAIN_REQUIRES_CONSENT] y devuelve `false`. Para descargar de verdad hay que usar
     * [ensureInstalled] con `allowDownload = true` desde la UI, despues del dialogo de
     * consentimiento.
     */
    @JvmStatic
    fun ensureInstalled(context: Context, mode: FlutterBuildMode, progress: (String) -> Unit): Boolean =
        ensureInstalled(context, mode, allowDownload = false, progress = progress)

    /**
     * Igual que [ensureInstalled] pero decidiendo **explicitamente** si se puede descargar.
     *
     * El toolchain de Dart son cientos de MB: la descarga **nunca** puede ocurrir sin que el usuario
     * lo haya visto y aceptado antes ([MESSAGE_TOOLCHAIN_REQUIRES_CONSENT]). Por eso el camino de
     * compilacion entra aqui con `allowDownload = false` y solo la UI, despues del dialogo de
     * consentimiento, repite la operacion con `true`.
     *
     * @param allowDownload `false` -> si falta algo, no se toca la red: se registra el motivo y se
     *   devuelve `false`.
     */
    @JvmStatic
    fun ensureInstalled(
        context: Context,
        mode: FlutterBuildMode,
        allowDownload: Boolean,
        progress: (String) -> Unit,
    ): Boolean {
        if (isReady(context, mode)) {
            progress("Toolchain Flutter ya instalado (Dart ${installedDartVersion(context)}, modo $mode)")
            return true
        }

        if (!allowDownload) {
            progress(MESSAGE_TOOLCHAIN_REQUIRES_CONSENT)
            return false
        }

        if (installedDartVersion(context) == null) {
            val missing = missingDartData(context)
            progress("Instalando SDK Dart on-device... (faltan: ${missing.joinToString(", ")})")
            if (!FlutterToolchainInstaller.install(context, progress)) {
                return false
            }
        }

        // El SDK Dart ya esta en disco. Antes de descargar cientos de MB de artefactos del engine: si
        // los ejecutables que necesita el modo NO se pueden lanzar (p. ej. esta ABI no empaqueta
        // `libdartaotruntime.so`), se para aqui con la pieza y la causa exactas, en vez de bajar todo
        // para acabar fallando en la compilacion.
        executableBlockerReason(context, mode)?.let { reason ->
            progress("Toolchain Flutter instalado a medias: $reason")
            return false
        }

        if (!FlutterEngineArtifacts.ensureArtifacts(context, mode, progress)) {
            return false
        }

        if (isReady(context, mode)) {
            progress("Toolchain Flutter listo (Dart ${installedDartVersion(context)}, modo $mode)")
            return true
        }
        progress("Toolchain Flutter incompleto: ${describeNotReady(context, mode)}")
        return false
    }

    /**
     * Variante compatible con el contrato previo (carril B2): usa
     * [FlutterProjectDefaults.DEFAULT_MODE].
     */
    @JvmStatic
    fun ensureInstalled(context: Context, progress: (String) -> Unit): Boolean =
        ensureInstalled(context, FlutterProjectDefaults.DEFAULT_MODE, progress)

    /* ---------------------------------------------------------------------------------------- */
    /* Estado visible + espacio en disco (consentimiento de descarga)                            */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * Mensaje que se registra cuando hace falta el toolchain y **no** hay consentimiento de
     * descarga. Es el texto que ve el usuario en el log del build; coincide con la accion que debe
     * tomar a mano (el dialogo de consentimiento vive en ese menu).
     */
    const val MESSAGE_TOOLCHAIN_REQUIRES_CONSENT =
        "Para compilar Flutter hace falta el toolchain de Dart: instalalo desde Flutter > Estado del toolchain"

    /**
     * Un componente del toolchain con lo que ocupa su descarga y si ya esta en disco.
     *
     * [downloadBytes] es el tamaño del artefacto remoto (constante verificada con `curl -sI` /
     * `content-length`); en el framework Dart es la medida real del tarball (GitHub no publica
     * `content-length`, ver `FlutterToolchainPaths.FRAMEWORK_TARBALL_ESTIMATED_SIZE`).
     */
    class ToolchainComponent(
        /** Texto para la UI, p. ej. `SDK Dart 3.13.4 (dart_3.13.4_aarch64.deb)`. */
        @JvmField val label: String,
        @JvmField val downloadBytes: Long,
        @JvmField val installed: Boolean,
    )

    /**
     * Inventario de lo que compone el toolchain para [mode]: un elemento por artefacto descargable,
     * con su tamaño y si ya esta instalado.
     *
     * Las comprobaciones son **de fichero** (rapidas, sin lanzar procesos): es la lista que se pinta
     * en el dialogo de consentimiento, no el veredicto de [isReady] (que ejecuta binarios). Para el
     * veredicto real, [isReady] / [describeNotReady].
     */
    @JvmStatic
    fun componentInventory(context: Context, mode: FlutterBuildMode): List<ToolchainComponent> {
        val abi = FlutterToolchainPaths.resolveSupportedAbi() ?: FlutterToolchainPaths.deviceAbiName()
        val dartSpec = FlutterToolchainPaths.dartPackageSpec(abi)
        val patchedSdkSpec = if (mode == FlutterBuildMode.RELEASE_AOT) {
            FlutterToolchainPaths.patchedSdkProductArtifact()
        } else {
            FlutterToolchainPaths.patchedSdkArtifact()
        }
        val components = mutableListOf<ToolchainComponent>()

        // 1) SDK Dart on-device (el .deb de Termux de la ABI del dispositivo).
        components.add(
            ToolchainComponent(
                "SDK Dart ${FlutterToolchainPaths.DART_VERSION} (${dartSpec?.fileName ?: "sin paquete para $abi"})",
                dartSpec?.sizeBytes ?: 0L,
                isDartSdkInstalled(context),
            )
        )

        // 2) Embedding (jar de clases del runtime; debug/release segun el modo).
        val embeddingSpec = FlutterToolchainPaths.embeddingJarArtifact(mode)
        components.add(
            ToolchainComponent(
                "Embedding del engine ($mode)",
                embeddingSpec.sizeBytes,
                FlutterToolchainPaths.embeddingJar(context, mode).isFile,
            )
        )

        // 3) Nativas de la ABI (libflutter.so del engine debug o release).
        val nativeSpec = FlutterToolchainPaths.nativeJarArtifact(abi, mode)
        components.add(
            ToolchainComponent(
                "Engine $mode para $abi (libflutter.so)",
                nativeSpec?.sizeBytes ?: 0L,
                FlutterToolchainPaths.libFlutterSo(context, mode).isFile,
            )
        )

        // 4) Patched SDK (plataforma Dart de `gen_kernel`; product en AOT).
        components.add(
            ToolchainComponent(
                "Patched SDK (platform_strong.dill, $mode)",
                patchedSdkSpec.sizeBytes,
                FlutterToolchainPaths.patchedSdkPlatformDillForMode(context, mode).isFile,
            )
        )

        // 5) Framework Dart (fuentes de `package:flutter…`; no lo trae ningun artefacto del engine).
        val frameworkDir = FlutterToolchainPaths.frameworkDir(context)
        val frameworkReady = File(frameworkDir, "pubspec.yaml").isFile && File(frameworkDir, "lib").isDirectory
        components.add(
            ToolchainComponent(
                "Framework Dart de Flutter ${FlutterToolchainPaths.FLUTTER_VERSION}",
                FlutterToolchainPaths.FRAMEWORK_TARBALL_ESTIMATED_SIZE,
                frameworkReady,
            )
        )

        // 6) Dependencias de pub del framework (una entrada por paquete).
        for (dependency in FlutterToolchainPaths.frameworkPubDependencies()) {
            val target = FlutterToolchainPaths.pubDependencyDir(context, dependency)
            components.add(
                ToolchainComponent(
                    "pub: ${dependency.name} ${dependency.version}",
                    dependency.sizeBytes,
                    File(target, "lib").isDirectory,
                )
            )
        }

        // 7) Fuentes de assets (fonts.zip del SDK; los shaders van embebidos en el APK).
        components.add(
            ToolchainComponent(
                "Assets: MaterialIcons (material_fonts)",
                FlutterBundleAssets.MATERIAL_FONTS_SIZE,
                FlutterBundleAssets.areAssetSourcesReady(context),
            )
        )

        return components
    }

    /** Variante con el modo por defecto ([FlutterProjectDefaults.DEFAULT_MODE]). */
    @JvmStatic
    fun componentInventory(context: Context): List<ToolchainComponent> =
        componentInventory(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** Etiquetas legibles de lo que falta para [mode] (vacia si no falta nada). */
    @JvmStatic
    fun missingComponents(context: Context, mode: FlutterBuildMode): List<String> =
        componentInventory(context, mode).filter { !it.installed }.map { it.label }

    /** Variante con el modo por defecto. */
    @JvmStatic
    fun missingComponents(context: Context): List<String> =
        missingComponents(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** Bytes que hay que descargar para completar el toolchain de [mode] (0 si ya esta completo). */
    @JvmStatic
    fun estimatedDownloadBytes(context: Context, mode: FlutterBuildMode): Long =
        componentInventory(context, mode).filter { !it.installed }.sumOf { it.downloadBytes }

    /** Variante con el modo por defecto. */
    @JvmStatic
    fun estimatedDownloadBytes(context: Context): Long =
        estimatedDownloadBytes(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** Bytes de la descarga **completa** (toolchain desde cero) para [mode]. */
    @JvmStatic
    fun totalDownloadBytes(context: Context, mode: FlutterBuildMode): Long =
        componentInventory(context, mode).sumOf { it.downloadBytes }

    /** Variante con el modo por defecto. */
    @JvmStatic
    fun totalDownloadBytes(context: Context): Long =
        totalDownloadBytes(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** Espacio que ocupa hoy el toolchain en el almacenamiento privado de la app. */
    @JvmStatic
    fun installedBytes(context: Context): Long = directorySize(toolchainDir(context))

    /** Suma recursiva de tamaños; nunca lanza (devuelve 0 si el directorio no existe). */
    private fun directorySize(dir: File): Long {
        if (!dir.exists()) {
            return 0L
        }
        if (dir.isFile) {
            return dir.length()
        }
        var total = 0L
        dir.listFiles()?.forEach { child -> total += directorySize(child) }
        return total
    }

    /** `true` si los **datos** del SDK Dart estan extraidos (marcador + snapshots + vm_platform.dill). */
    private fun isDartSdkInstalled(context: Context): Boolean = missingDartData(context).isEmpty()

    /**
     * Resumen de una linea del estado del toolchain (para el dialogo de la UI).
     *
     * Ejemplo: `Toolchain Flutter listo (Dart 3.13.4, 307.4 MB en disco, modo DEBUG_JIT)` o el mismo
     * texto con `NO instalado … faltan 307.4 MB de descarga` cuando no lo esta.
     */
    @JvmStatic
    fun toolchainStatusSummary(context: Context): String =
        toolchainStatusSummary(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** Igual que [toolchainStatusSummary] pero para [mode]. */
    @JvmStatic
    fun toolchainStatusSummary(context: Context, mode: FlutterBuildMode): String {
        val version = installedDartVersion(context)
        val installed = isReady(context, mode)
        val occupied = formatBytes(installedBytes(context))
        return when {
            installed -> "Toolchain Flutter listo (Dart $version, $occupied en disco, modo $mode)"
            // Estado intermedio honesto: los datos del SDK Dart estan, lo que falta es otra cosa
            // (ejecutables que no arrancan, artefactos del engine...). Nunca "no instalado".
            version != null -> {
                val blocker = executableBlockerReason(context, mode)
                "Toolchain Flutter: SDK Dart $version extraido ($occupied en disco) pero NO listo para " +
                    "compilar, modo $mode" + (blocker?.let { ": $it" } ?: "")
            }
            else -> {
                val pending = formatBytes(estimatedDownloadBytes(context, mode))
                "Toolchain Flutter NO instalado (Dart sin instalar, $occupied en disco; " +
                    "faltan $pending de descarga, modo $mode)"
            }
        }
    }

    /** Formatea bytes como `KB`/`MB`/`GB` con un decimal (locale fijo, para logs y dialogos). */
    @JvmStatic
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "0 MB"
        }
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024.0) {
            return String.format(Locale.US, "%.1f MB", mb)
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** Borra todo el toolchain (diagnóstico/reinstalación). */
    @JvmStatic
    fun reset(context: Context) {
        val dir = toolchainDir(context)
        FlutterToolchainInstaller.deleteRecursively(dir)
        Log.d(TAG, "Toolchain borrado: ${dir.absolutePath}")
    }

    /** Resumen legible del estado, para el dialogo de la UI. */
    @JvmStatic
    fun describeStatus(context: Context): String {
        val abi = deviceAbi() ?: "sin-abi-soportada"
        val version = installedDartVersion(context) ?: "no-instalado"
        val mode = FlutterProjectDefaults.DEFAULT_MODE
        val engine = if (FlutterEngineArtifacts.areArtifactsReady(context, mode)) "ok" else "faltan-artefactos"
        val framework = if (FlutterEngineArtifacts.isFrameworkReady(context)) "ok" else "faltan-fuentes"
        val aot = FlutterToolchainPaths.aotBackendUnavailableReason(context) ?: "ok"
        return "dart=$version abi=$abi modo=$mode engine=$engine framework=$framework " +
                "aot=$aot ejecutables=[${probeExecutables(context).joinToString(", ")}] " +
                "dir=${toolchainDir(context).absolutePath}"
    }

    /**
     * Explicacion en espanol de por que [isReady] devuelve `false` (para el dialogo de la UI).
     * Vacio si el toolchain esta listo para [mode].
     */
    @JvmStatic
    fun describeNotReady(context: Context, mode: FlutterBuildMode): String {
        // 1) Datos del SDK Dart (lo que trae el .deb).
        val missingData = missingDartData(context)
        if (missingData.isNotEmpty()) {
            return "Falta el SDK Dart on-device (${missingData.joinToString(", ")}): instalalo desde el " +
                "menu Flutter del editor (descarga consentida del .deb de Termux)."
        }
        // 2) Ejecutables: pieza concreta + causa concreta (SELinux/W^X o ausente).
        executableBlockerReason(context, mode)?.let { return it }
        // 3) Artefactos del engine / framework Dart.
        if (!FlutterEngineArtifacts.areArtifactsReady(context, mode)) {
            return "Faltan artefactos del engine para $mode (embedding, libflutter.so, patched sdk, " +
                "framework Dart de pub o fuentes/shaders de assets): pulsa \"Instalar toolchain\"."
        }
        return ""
    }
}
