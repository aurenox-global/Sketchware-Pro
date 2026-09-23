package pro.sketchware.flutter

import android.util.Log
import java.io.File

/**
 * Persistencia del proyecto Flutter dentro del arbol de ficheros de un proyecto Sketchware.
 *
 * Contrato Fase 7 (carril B2):
 * ```
 * object FlutterProjectStore {
 *     fun rootDirectory(projectFilesDir: File): File   // <filesDir del proyecto>/flutter
 *     fun load(projectFilesDir: File): FlutterProject?
 *     fun save(projectFilesDir: File, project: FlutterProject)
 *     fun isFlutterProject(projectFilesDir: File): Boolean
 * }
 * ```
 */
object FlutterProjectStore {

    private const val TAG = "FlutterProjectStore"

    private const val FLUTTER_DIRECTORY_NAME = "flutter"
    private const val PUBSPEC_FILE_NAME = "pubspec.yaml"
    private const val PROJECT_JSON_FILE_NAME = "project.json"

    /** `<filesDir del proyecto>/flutter` */
    @JvmStatic
    fun rootDirectory(projectFilesDir: File): File {
        return File(projectFilesDir, FLUTTER_DIRECTORY_NAME)
    }

    @JvmStatic
    fun load(projectFilesDir: File): FlutterProject? {
        val metadataFile = projectJsonFile(projectFilesDir)
        if (!metadataFile.exists() || !metadataFile.isFile) {
            return null
        }

        return try {
            val parseResult = FlutterProjectSerializer.parse(metadataFile.readText())
            if (parseResult.project == null || parseResult.hasErrors()) {
                Log.d(TAG, "Flutter project metadata could not be parsed: ${metadataFile.absolutePath}")
                null
            } else {
                parseResult.project
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to read Flutter project metadata: ${metadataFile.absolutePath}", e)
            null
        }
    }

    @JvmStatic
    fun save(projectFilesDir: File, project: FlutterProject) {
        val root = rootDirectory(projectFilesDir)
        if (!root.exists() && !root.mkdirs()) {
            Log.w(TAG, "Could not create Flutter root directory: ${root.absolutePath}")
            return
        }

        try {
            projectJsonFile(projectFilesDir).writeText(FlutterProjectSerializer.toJson(project))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write Flutter project metadata", e)
        }
    }

    /** Existe `pubspec.yaml` en [rootDirectory]. */
    @JvmStatic
    fun isFlutterProject(projectFilesDir: File): Boolean {
        return pubspecFile(projectFilesDir).exists()
    }

    /** `flutter/project.json`. */
    @JvmStatic
    fun projectJsonFile(projectFilesDir: File): File {
        return File(rootDirectory(projectFilesDir), PROJECT_JSON_FILE_NAME)
    }

    /** `flutter/pubspec.yaml`. */
    @JvmStatic
    fun pubspecFile(projectFilesDir: File): File {
        return File(rootDirectory(projectFilesDir), PUBSPEC_FILE_NAME)
    }
}
