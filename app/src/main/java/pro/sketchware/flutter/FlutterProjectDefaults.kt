package pro.sketchware.flutter

/**
 * Defaults y normalización del modelo Flutter (analogo a [pro.sketchware.kmp.KmpProjectDefaults]).
 */
object FlutterProjectDefaults {

    const val DEFAULT_PROJECT_NAME: String = "FlutterProject"
    const val DEFAULT_PACKAGE_NAME: String = "pro.sketchware.flutter"

    /** Comentarios en espanol: restriccion de SDK Dart para `pubspec.yaml`. */
    const val DEFAULT_DART_SDK_CONSTRAINT: String = ">=3.1.0 <4.0.0"

    /** Restriccion del SDK de Flutter para `pubspec.yaml`. */
    const val DEFAULT_FLUTTER_SDK_CONSTRAINT: String = ">=3.13.0 <4.0.0"

    /**
     * Modo por defecto: **DEBUG_JIT**.
     *
     * Es el modo con mas camino recorrido (informe E2E: engine debug + `kernel_blob.bin` compilado
     * en el movil -> la app arranca y responde a los toques). `RELEASE_AOT` esta **desbloqueado**
     * desde la Fase 8 (carril I) para las variantes del APK que empaquetan el binario
     * (`arm64-v8a` y `x86_64`, cada una con su `gen_snapshot`), pero se sigue dejando como
     * opcion explicita del proyecto: el AOT on-device tarda mas y solo esta probado en arm64.
     */
    @JvmField
    val DEFAULT_MODE: FlutterBuildMode = FlutterBuildMode.DEBUG_JIT

    @JvmStatic
    fun normalizeProjectName(projectName: String?): String {
        val trimmed = projectName?.trim().orEmpty()
        return trimmed.ifEmpty { DEFAULT_PROJECT_NAME }
    }

    @JvmStatic
    fun normalizePackageName(packageName: String?): String {
        val trimmed = packageName?.trim().orEmpty()
        return trimmed.ifEmpty { DEFAULT_PACKAGE_NAME }
    }

    /** Ruta de paquete estilo Java (`a.b.c` -> `a/b/c`). */
    @JvmStatic
    fun packagePath(packageName: String?): String {
        return normalizePackageName(packageName).replace('.', '/')
    }

    /**
     * Nombre valido para `pubspec.yaml` / paquete Dart: minusculas, digitos y guion bajo,
     * empezando por letra o guion bajo. Si no queda nada utilizable se usa `flutter_app`.
     */
    @JvmStatic
    fun pubspecPackageName(projectName: String?): String {
        val raw = projectName?.trim().orEmpty()
        if (raw.isEmpty()) {
            return "flutter_app"
        }

        val builder = StringBuilder(raw.length)
        for (character in raw) {
            when {
                character.isLetterOrDigit() -> builder.append(character.lowercaseChar())
                character == '_' -> builder.append('_')
                else -> builder.append('_')
            }
        }

        var candidate = builder.toString().trim('_')
        while (candidate.contains("__")) {
            candidate = candidate.replace("__", "_")
        }
        if (candidate.isEmpty()) {
            return "flutter_app"
        }
        if (!candidate[0].isLetter()) {
            candidate = "app_$candidate"
        }

        return candidate
    }
}
