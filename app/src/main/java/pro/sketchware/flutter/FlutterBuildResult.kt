package pro.sketchware.flutter

/**
 * Resultado de un build Flutter completo (contrato Fase 7, carril C).
 *
 * Firmas congeladas: la UI del carril B2 construye este tipo y lo consume tal cual.
 *
 * SIN `@JvmField` a proposito (carril C2): con `@JvmField` Kotlin **no** genera getters y Java no
 * puede escribir `result.getSuccess()` / `getDurationMs()` / `getApkPath()` / `getLog()`, que es
 * justo lo que hace `LogicEditorActivity` (carril B2). Sin la anotacion, Kotlin genera los getters,
 * el codigo Kotlin sigue usando las propiedades y el Java compila sin cambios.
 */
class FlutterBuildResult(
    val success: Boolean,
    val apkPath: String?,
    val log: String,
    val durationMs: Long,
) {

    override fun toString(): String {
        return "FlutterBuildResult(success=$success, apkPath=$apkPath, durationMs=$durationMs, log=${log.length}ch)"
    }

    companion object {
        @JvmStatic
        fun success(apkPath: String?, log: String, durationMs: Long): FlutterBuildResult {
            return FlutterBuildResult(true, apkPath, log, durationMs)
        }

        @JvmStatic
        fun failure(log: String, durationMs: Long): FlutterBuildResult {
            return FlutterBuildResult(false, null, log, durationMs)
        }
    }
}
