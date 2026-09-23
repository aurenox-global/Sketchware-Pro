package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Fachada del toolchain Flutter on-device (contrato Fase 7, carril C).
 *
 * Punto de entrada para la UI (carril B2): [installedDartVersion], [isReady], [ensureInstalled].
 * Todos los métodos bloqueantes deben llamarse en un hilo de fondo.
 */
object FlutterToolchainManager {

    private const val TAG = "FlutterToolchain"

    /** `<filesDir>/flutter-toolchain`. */
    @JvmStatic
    fun toolchainDir(context: Context): File = FlutterToolchainPaths.toolchainDir(context)

    /**
     * Versión de Dart instalada, o `null` si no hay toolchain utilizable.
     *
     * No se confía solo en el marcador: además se comprueba que `bin/dart` existe y es ejecutable.
     */
    @JvmStatic
    fun installedDartVersion(context: Context): String? {
        val dart = FlutterToolchainPaths.dartExecutable(context)
        if (!dart.isFile || !dart.canExecute()) {
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

        // Marcador ausente pero binario presente: se comprueba de verdad.
        return FlutterToolchainInstaller.runVersionCheck(context)
    }

    /** ABI del dispositivo soportada por el toolchain, o `null`. */
    @JvmStatic
    fun deviceAbi(): String? = FlutterToolchainPaths.resolveSupportedAbi()

    /**
     * `true` si dart + engine + framework Dart del [FlutterBuildMode] indicado están completos.
     */
    @JvmStatic
    fun isReady(context: Context, mode: FlutterBuildMode): Boolean {
        if (installedDartVersion(context) == null) {
            return false
        }
        return FlutterEngineArtifacts.areArtifactsReady(context, mode)
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

    /** Resumen legible del estado, para el diálogo de la UI. */
    @JvmStatic
    fun describeStatus(context: Context): String {
        val abi = deviceAbi() ?: "sin-abi-soportada"
        val version = installedDartVersion(context) ?: "no-instalado"
        val mode = FlutterProjectDefaults.DEFAULT_MODE
        val engine = if (FlutterEngineArtifacts.areArtifactsReady(context, mode)) "ok" else "faltan-artefactos"
        val framework = if (FlutterEngineArtifacts.isFrameworkReady(context)) "ok" else "faltan-fuentes"
        return "dart=$version abi=$abi modo=$mode engine=$engine framework=$framework " +
                "dir=${toolchainDir(context).absolutePath}"
    }
}
