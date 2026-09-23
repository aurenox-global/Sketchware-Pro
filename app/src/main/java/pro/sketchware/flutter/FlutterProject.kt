package pro.sketchware.flutter

/**
 * Modo de compilación Flutter soportado por el fork.
 *
 * - [DEBUG_JIT]: kernel Blob (JIT) para desarrollo rápido. **Es el unico modo que funciona hoy**
 *   (engine debug + `kernel_blob.bin`), y por eso es el valor por defecto.
 * - [RELEASE_AOT]: snapshot AOT (`libapp.so`) para release. **Bloqueado a proposito** en
 *   `FlutterDartCompiler` (el `gen_snapshot` del SDK de Termux no lleva compressed pointers y el
 *   engine oficial los exige); ver `docs/flutter-fase7.md`.
 */
enum class FlutterBuildMode {
    DEBUG_JIT,
    RELEASE_AOT;

    companion object {
        /** Resolucion tolerante desde texto (`debug_jit`, `RELEASE-AOT`, ...). */
        @JvmStatic
        fun fromId(value: String?): FlutterBuildMode? {
            if (value == null) {
                return null
            }

            val normalized = value
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .uppercase()

            if (normalized.isEmpty()) {
                return null
            }

            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/**
 * Modelo de datos del proyecto Flutter asociado a un proyecto de Sketchware.
 *
 * Contrato Fase 7 (carril B2). Las firmas son congeladas: no añadir ni reordenar
 * parámetros del constructor primario sin actualizar el contrato.
 */
data class FlutterProject(
    val scId: String,
    val projectName: String,
    val packageName: String,
    // Valor por defecto = el de FlutterProjectDefaults: DEBUG_JIT (el unico modo que arranca hoy).
    val mode: FlutterBuildMode = FlutterProjectDefaults.DEFAULT_MODE,
) {

    fun normalized(): FlutterProject {
        return FlutterProject(
            scId = scId.trim(),
            projectName = FlutterProjectDefaults.normalizeProjectName(projectName),
            packageName = FlutterProjectDefaults.normalizePackageName(packageName),
            mode = mode,
        )
    }

    companion object {
        @JvmStatic
        fun createDefault(scId: String, projectName: String, packageName: String): FlutterProject {
            return FlutterProject(
                scId = scId.trim(),
                projectName = FlutterProjectDefaults.normalizeProjectName(projectName),
                packageName = FlutterProjectDefaults.normalizePackageName(packageName),
                mode = FlutterProjectDefaults.DEFAULT_MODE,
            )
        }
    }
}
