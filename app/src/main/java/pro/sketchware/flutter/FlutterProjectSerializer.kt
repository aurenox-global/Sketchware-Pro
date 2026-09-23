package pro.sketchware.flutter

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class FlutterProjectIssueSeverity {
    ERROR,
    WARNING
}

class FlutterProjectIssue(
    @JvmField val code: String,
    @JvmField val severity: FlutterProjectIssueSeverity,
    @JvmField val path: String,
    @JvmField val message: String
)

class FlutterProjectParseResult(
    @JvmField val project: FlutterProject?,
    @JvmField val parsedAtMs: Long,
    @JvmField val issues: List<FlutterProjectIssue>
) {
    fun hasErrors(): Boolean {
        return issues.any { it.severity == FlutterProjectIssueSeverity.ERROR }
    }
}

/**
 * (De)serializacion de `flutter/project.json` (analogo a [pro.sketchware.kmp.KmpProjectSerializer]).
 */
object FlutterProjectSerializer {

    const val SCHEMA_VERSION: Int = 1

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @JvmStatic
    fun parse(projectJson: String?): FlutterProjectParseResult {
        val issues = mutableListOf<FlutterProjectIssue>()
        val now = System.currentTimeMillis()
        val source = projectJson?.trim().orEmpty()

        if (source.isEmpty()) {
            issues.add(
                FlutterProjectIssue(
                    "INVALID_JSON",
                    FlutterProjectIssueSeverity.ERROR,
                    "$",
                    "Project JSON content is empty"
                )
            )
            return FlutterProjectParseResult(null, now, issues)
        }

        val root: JsonElement = try {
            JsonParser.parseString(source)
        } catch (e: Exception) {
            issues.add(
                FlutterProjectIssue(
                    "INVALID_JSON",
                    FlutterProjectIssueSeverity.ERROR,
                    "$",
                    "Invalid JSON: ${safeMessage(e)}"
                )
            )
            return FlutterProjectParseResult(null, now, issues)
        }

        if (!root.isJsonObject) {
            issues.add(
                FlutterProjectIssue(
                    "ROOT_NOT_OBJECT",
                    FlutterProjectIssueSeverity.ERROR,
                    "$",
                    "Project JSON root must be an object"
                )
            )
            return FlutterProjectParseResult(null, now, issues)
        }

        val jsonObject = root.asJsonObject
        val schemaVersion = readInt(jsonObject, "schemaVersion", SCHEMA_VERSION)
        if (schemaVersion > SCHEMA_VERSION) {
            issues.add(
                FlutterProjectIssue(
                    "UNSUPPORTED_SCHEMA_VERSION",
                    FlutterProjectIssueSeverity.WARNING,
                    "schemaVersion",
                    "Project JSON uses schema version $schemaVersion but this build only knows $SCHEMA_VERSION"
                )
            )
        }

        val scId = readRequiredString(jsonObject, "scId", issues)
        val projectName = readRequiredString(jsonObject, "projectName", issues)
        val packageName = readRequiredString(jsonObject, "packageName", issues)
        // Fallback = FlutterProjectDefaults.DEFAULT_MODE (DEBUG_JIT): un project.json sin `mode`
        // (o con un modo desconocido) no debe caer en RELEASE_AOT, que hoy esta bloqueado.
        val mode = FlutterBuildMode.fromId(
            readOptionalString(jsonObject, "mode", FlutterProjectDefaults.DEFAULT_MODE.name)
        )

        if (mode == null) {
            issues.add(
                FlutterProjectIssue(
                    "UNKNOWN_BUILD_MODE",
                    FlutterProjectIssueSeverity.WARNING,
                    "mode",
                    "Unknown build mode, defaulting to ${FlutterProjectDefaults.DEFAULT_MODE.name}"
                )
            )
        }

        val project = FlutterProject(
            scId = scId,
            projectName = projectName.ifEmpty { FlutterProjectDefaults.DEFAULT_PROJECT_NAME },
            packageName = packageName.ifEmpty { FlutterProjectDefaults.DEFAULT_PACKAGE_NAME },
            mode = mode ?: FlutterProjectDefaults.DEFAULT_MODE
        )

        return FlutterProjectParseResult(project, now, issues)
    }

    @JvmStatic
    fun toJson(project: FlutterProject): String {
        val normalized = project.normalized()
        val root = JsonObject()
        root.addProperty("schemaVersion", SCHEMA_VERSION)
        root.addProperty("scId", normalized.scId)
        root.addProperty("projectName", normalized.projectName)
        root.addProperty("packageName", normalized.packageName)
        root.addProperty("mode", normalized.mode.name)
        return gson.toJson(root)
    }

    private fun readRequiredString(
        objectRoot: JsonObject,
        key: String,
        issues: MutableList<FlutterProjectIssue>
    ): String {
        if (!objectRoot.has(key) || objectRoot[key].isJsonNull) {
            issues.add(
                FlutterProjectIssue(
                    "MISSING_REQUIRED_FIELD",
                    FlutterProjectIssueSeverity.ERROR,
                    key,
                    "Missing required field: $key"
                )
            )
            return ""
        }

        val element = objectRoot[key]
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            issues.add(
                FlutterProjectIssue(
                    "INVALID_FIELD_TYPE",
                    FlutterProjectIssueSeverity.ERROR,
                    key,
                    "Field '$key' must be a string"
                )
            )
            return ""
        }

        val value = element.asString.trim()
        if (value.isEmpty()) {
            issues.add(
                FlutterProjectIssue(
                    "EMPTY_REQUIRED_FIELD",
                    FlutterProjectIssueSeverity.ERROR,
                    key,
                    "Required field '$key' must not be empty"
                )
            )
        }
        return value
    }

    private fun readOptionalString(objectRoot: JsonObject, key: String, fallback: String): String {
        if (!objectRoot.has(key) || objectRoot[key].isJsonNull) {
            return fallback
        }

        val element = objectRoot[key]
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            return fallback
        }

        return element.asString.trim().ifEmpty { fallback }
    }

    private fun readInt(objectRoot: JsonObject, key: String, fallback: Int): Int {
        if (!objectRoot.has(key) || objectRoot[key].isJsonNull) {
            return fallback
        }

        val element = objectRoot[key]
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return fallback
        }

        return try {
            element.asInt
        } catch (_: Exception) {
            fallback
        }
    }

    private fun safeMessage(throwable: Throwable?): String {
        if (throwable == null || throwable.message == null) {
            return "unknown error"
        }
        return throwable.message as String
    }
}
