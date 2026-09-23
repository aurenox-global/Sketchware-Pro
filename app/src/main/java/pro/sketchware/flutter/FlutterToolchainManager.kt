package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import java.io.File

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
) {
    override fun toString(): String =
        "$name=${if (!exists) "ausente" else if (runnable) "ejecutable" else "NO-ejecutable"}" +
            "(${if (packaged) "nativeLibraryDir" else "filesDir"})"
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
     * Version de Dart instalada, o `null` si no hay toolchain **ejecutable**.
     *
     * No se confia solo en el marcador: exige que `dartaotruntime` (el empaquetado en
     * `nativeLibraryDir` si existe, o el extraido del `.deb`) **se ejecute de verdad** y que este el
     * `gen_kernel_aot.dart.snapshot` (es el front-end que la app lanza con el).
     */
    @JvmStatic
    fun installedDartVersion(context: Context): String? {
        val runtimeStatus = probeExecutables(context)
            .firstOrNull { it.name == FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME }
            ?: return null
        if (!runtimeStatus.runnable) {
            return null
        }
        if (!FlutterToolchainPaths.genKernelSnapshot(context).isFile) {
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

        // Marcador ausente pero runtime ejecutable: se deduce de la salida real.
        return runtimeStatus.versionLine
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
            val version = if (file.isFile) FlutterToolchainInstaller.executableVersion(file) else null
            ToolchainExecutableStatus(
                name = name,
                path = file.absolutePath,
                packaged = FlutterToolchainPaths.nativeLibraryDir(context)?.let { dir ->
                    file.absolutePath.startsWith(dir.absolutePath)
                } == true,
                exists = file.isFile,
                runnable = version != null,
                versionLine = version,
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
     * - los dos modos necesitan `dartaotruntime` ejecutable (lanza `gen_kernel` y `pub`);
     * - [FlutterBuildMode.RELEASE_AOT] necesita ademas nuestro `gen_snapshot` y la plataforma
     *   `flutter_patched_sdk_product`.
     */
    @JvmStatic
    fun isReady(context: Context, mode: FlutterBuildMode): Boolean {
        if (installedDartVersion(context) == null) {
            return false
        }
        if (!FlutterEngineArtifacts.areArtifactsReady(context, mode)) {
            return false
        }
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            if (FlutterToolchainPaths.aotBackendUnavailableReason(context) != null) {
                return false
            }
            val genSnapshot = probeExecutable(context, FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT)
            if (genSnapshot == null || !genSnapshot.runnable) {
                return false
            }
        }
        return true
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
     */
    @JvmStatic
    fun ensureInstalled(context: Context, mode: FlutterBuildMode, progress: (String) -> Unit): Boolean {
        if (isReady(context, mode)) {
            progress("Toolchain Flutter ya instalado (Dart ${installedDartVersion(context)}, modo $mode)")
            return true
        }

        if (installedDartVersion(context) == null) {
            progress("Instalando SDK Dart on-device...")
            if (!FlutterToolchainInstaller.install(context, progress)) {
                return false
            }
        }

        if (!FlutterEngineArtifacts.ensureArtifacts(context, mode, progress)) {
            return false
        }

        val ready = isReady(context, mode)
        progress(if (ready) "Toolchain Flutter listo" else "Toolchain Flutter incompleto")
        return ready
    }

    /**
     * Variante compatible con el contrato previo (carril B2): usa
     * [FlutterProjectDefaults.DEFAULT_MODE].
     */
    @JvmStatic
    fun ensureInstalled(context: Context, progress: (String) -> Unit): Boolean =
        ensureInstalled(context, FlutterProjectDefaults.DEFAULT_MODE, progress)

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
        val runtime = probeExecutable(context, FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME)
        if (runtime == null || !runtime.exists) {
            return "Falta `dartaotruntime` (ni empaquetado en nativeLibraryDir ni extraido del .deb): " +
                "instala el toolchain Flutter desde el menu Flutter del editor."
        }
        if (!runtime.runnable) {
            return "`dartaotruntime` esta en ${runtime.path} pero NO se puede ejecutar (SELinux: " +
                "`execute_no_trans` solo se permite en nativeLibraryDir). En la practica: estas usando " +
                "un APK de la ABI '${FlutterToolchainPaths.deviceAbiName()}', que no empaqueta los " +
                "ejecutables (solo la variante arm64-v8a lo hace)."
        }
        if (!FlutterToolchainPaths.genKernelSnapshot(context).isFile) {
            return "Falta `bin/snapshots/gen_kernel_aot.dart.snapshot` en el toolchain (se extrae del .deb)."
        }
        if (!FlutterEngineArtifacts.areArtifactsReady(context, mode)) {
            return "Faltan artefactos del engine para $mode (embedding, libflutter.so, patched sdk, " +
                "framework Dart de pub o fuentes/shaders de assets): pulsa \"Instalar toolchain\"."
        }
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            FlutterToolchainPaths.aotBackendUnavailableReason(context)?.let { return it }
            val genSnapshot = probeExecutable(context, FlutterToolchainPaths.PACKAGED_GEN_SNAPSHOT)
            if (genSnapshot == null || !genSnapshot.runnable) {
                return "El `gen_snapshot` propio no se puede ejecutar desde " +
                    "${genSnapshot?.path ?: "(ninguna ruta)"}."
            }
        }
        return ""
    }
}
