package pro.sketchware.flutter

import android.util.Log
import java.io.File

/**
 * Genera el arbol de un proyecto Flutter bajo `<files>/flutter/`.
 *
 * Contrato Fase 7 (carril B2):
 * ```
 * object FlutterScaffoldInitializer {
 *     fun initialize(rootDirectory: File, scId: String, projectName: String, packageName: String): Boolean
 * }
 * ```
 *
 * El scaffold es **idempotente**: nunca sobrescribe un fichero que ya exista, de modo que
 * reabrir y guardar un proyecto no destruye el codigo Dart del usuario.
 */
object FlutterScaffoldInitializer {

    private const val TAG = "FlutterScaffold"

    private const val PUBSPEC_FILE_NAME = "pubspec.yaml"
    private const val GITIGNORE_FILE_NAME = ".gitignore"
    private const val PROJECT_JSON_FILE_NAME = "project.json"
    private const val MAIN_DART_RELATIVE_PATH = "lib/main.dart"
    private const val ASSETS_README_RELATIVE_PATH = "assets/README.md"
    private const val MANIFEST_RELATIVE_PATH = "android/app/src/main/AndroidManifest.xml"
    private const val STYLES_RELATIVE_PATH = "android/app/src/main/res/values/styles.xml"
    private const val GRADLE_PROPERTIES_RELATIVE_PATH = "android/gradle.properties"
    private const val ANDROID_README_RELATIVE_PATH = "android/README.md"

    @JvmStatic
    fun initialize(rootDirectory: File, scId: String, projectName: String, packageName: String): Boolean {
        return try {
            val normalizedProjectName = FlutterProjectDefaults.normalizeProjectName(projectName)
            val normalizedPackageName = FlutterProjectDefaults.normalizePackageName(packageName)

            if (!ensureDirectory(rootDirectory)) {
                Log.w(TAG, "Could not create Flutter root directory: ${rootDirectory.absolutePath}")
                return false
            }

            writeIfAbsent(
                File(rootDirectory, PUBSPEC_FILE_NAME),
                FlutterScaffoldTemplates.pubspecYaml(normalizedProjectName)
            )
            writeIfAbsent(File(rootDirectory, MAIN_DART_RELATIVE_PATH), FlutterScaffoldTemplates.mainDart())
            writeIfAbsent(File(rootDirectory, GITIGNORE_FILE_NAME), FlutterScaffoldTemplates.gitignore())
            writeIfAbsent(
                File(rootDirectory, ASSETS_README_RELATIVE_PATH),
                FlutterScaffoldTemplates.assetsReadme()
            )
            writeIfAbsent(
                File(rootDirectory, MANIFEST_RELATIVE_PATH),
                FlutterScaffoldTemplates.androidManifest(normalizedPackageName, normalizedProjectName)
            )
            writeIfAbsent(File(rootDirectory, STYLES_RELATIVE_PATH), FlutterScaffoldTemplates.stylesXml())
            writeIfAbsent(
                File(rootDirectory, GRADLE_PROPERTIES_RELATIVE_PATH),
                FlutterScaffoldTemplates.gradleProperties()
            )
            writeIfAbsent(
                File(rootDirectory, ANDROID_README_RELATIVE_PATH),
                FlutterScaffoldTemplates.androidReadme(normalizedPackageName)
            )

            val project = FlutterProject.createDefault(scId, normalizedProjectName, normalizedPackageName)
            writeIfAbsent(
                File(rootDirectory, PROJECT_JSON_FILE_NAME),
                FlutterProjectSerializer.toJson(project)
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Flutter scaffold initialization failed: ${rootDirectory.absolutePath}", e)
            false
        }
    }

    private fun ensureDirectory(directory: File): Boolean {
        return directory.exists() || directory.mkdirs()
    }

    /** Escribe [content] solo si [file] no existe todavia (idempotencia del scaffold). */
    private fun writeIfAbsent(file: File, content: String) {
        if (file.exists()) {
            return
        }

        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content)
    }
}
