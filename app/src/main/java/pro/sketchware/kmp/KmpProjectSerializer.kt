package pro.sketchware.kmp

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

enum class KmpProjectIssueSeverity {
    ERROR,
    WARNING
}

class KmpProjectIssue(
    @JvmField val code: String,
    @JvmField val severity: KmpProjectIssueSeverity,
    @JvmField val path: String,
    @JvmField val message: String
)

class KmpProjectParseResult(
    @JvmField val project: KmpProject?,
    @JvmField val parsedAtMs: Long,
    @JvmField val issues: List<KmpProjectIssue>
) {
    fun hasErrors(): Boolean {
        return issues.any { it.severity == KmpProjectIssueSeverity.ERROR }
    }
}

object KmpProjectSerializer {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @JvmStatic
    fun parse(projectJson: String?): KmpProjectParseResult {
        val issues = mutableListOf<KmpProjectIssue>()
        val now = System.currentTimeMillis()
        val source = projectJson?.trim() ?: ""

        if (source.isEmpty()) {
            issues.add(KmpProjectIssue(
                "INVALID_JSON",
                KmpProjectIssueSeverity.ERROR,
                "$",
                "Project JSON content is empty"
            ))
            return KmpProjectParseResult(null, now, issues)
        }

        val root: JsonElement = try {
            JsonParser.parseString(source)
        } catch (e: Exception) {
            issues.add(KmpProjectIssue(
                "INVALID_JSON",
                KmpProjectIssueSeverity.ERROR,
                "$",
                "Invalid JSON: ${safeMessage(e)}"
            ))
            return KmpProjectParseResult(null, now, issues)
        }

        if (!root.isJsonObject) {
            issues.add(KmpProjectIssue(
                "ROOT_NOT_OBJECT",
                KmpProjectIssueSeverity.ERROR,
                "$",
                "Project JSON root must be an object"
            ))
            return KmpProjectParseResult(null, now, issues)
        }

        val jsonObject = root.asJsonObject
        val schemaVersion = readInt(jsonObject, "schemaVersion", KmpProject.SUPPORTED_SCHEMA_VERSION)
        val id = readRequiredString(jsonObject, "id", issues)
        val name = readRequiredString(jsonObject, "name", issues)
        val packageName = readRequiredString(jsonObject, "packageName", issues)

        val kotlinVersion = readOptionalString(
            jsonObject,
            "kotlinVersion",
            KmpProject.DEFAULT_KOTLIN_VERSION
        )
        val composeVersion = readOptionalString(
            jsonObject,
            "composeMultiplatformVersion",
            KmpProject.DEFAULT_COMPOSE_MULTIPLATFORM_VERSION
        )

        val targets = readTargets(jsonObject, issues)
        val hierarchy = readHierarchy(jsonObject, issues)

        val hierarchyResult = KmpSourceSetHierarchyValidator.validate(hierarchy)
        for (diagnostic in hierarchyResult.diagnostics) {
            issues.add(KmpProjectIssue(
                diagnostic.code,
                KmpProjectIssueSeverity.ERROR,
                diagnostic.path,
                diagnostic.message
            ))
        }

        val project = KmpProject(
            schemaVersion,
            id,
            name,
            packageName,
            kotlinVersion,
            composeVersion,
            targets,
            hierarchy,
            readLong(jsonObject, "createdAtMs", now),
            readLong(jsonObject, "updatedAtMs", now)
        )

        return KmpProjectParseResult(project, now, issues)
    }

    @JvmStatic
    fun toJson(project: KmpProject): String {
        val root = JsonObject()
        root.addProperty("schemaVersion", project.schemaVersion)
        root.addProperty("id", project.id)
        root.addProperty("name", project.name)
        root.addProperty("packageName", project.packageName)
        root.addProperty("kotlinVersion", project.kotlinVersion)
        root.addProperty("composeMultiplatformVersion", project.composeMultiplatformVersion)
        root.addProperty("createdAtMs", project.createdAtMs)
        root.addProperty("updatedAtMs", project.updatedAtMs)

        val targetArray = JsonArray()
        for (target in project.enabledTargets) {
            targetArray.add(target.name)
        }
        root.add("enabledTargets", targetArray)

        val hierarchyObject = JsonObject()
        for ((sourceSet, parents) in project.sourceSetHierarchy) {
            val parentArray = JsonArray()
            for (parent in parents) {
                parentArray.add(parent)
            }
            hierarchyObject.add(sourceSet, parentArray)
        }
        root.add("sourceSetHierarchy", hierarchyObject)

        return gson.toJson(root)
    }

    private fun readTargets(
        objectRoot: JsonObject,
        issues: MutableList<KmpProjectIssue>
    ): List<KmpTarget> {
        if (!objectRoot.has("enabledTargets") || objectRoot["enabledTargets"].isJsonNull) {
            issues.add(KmpProjectIssue(
                "MISSING_ENABLED_TARGETS",
                KmpProjectIssueSeverity.WARNING,
                "enabledTargets",
                "enabledTargets is missing, defaults will be used"
            ))
            return KmpProjectDefaults.DEFAULT_TARGETS
        }

        val targetElement = objectRoot["enabledTargets"]
        if (!targetElement.isJsonArray) {
            issues.add(KmpProjectIssue(
                "INVALID_ENABLED_TARGETS",
                KmpProjectIssueSeverity.ERROR,
                "enabledTargets",
                "enabledTargets must be a JSON array"
            ))
            return KmpProjectDefaults.DEFAULT_TARGETS
        }

        val parsedTargets = mutableListOf<KmpTarget>()
        for ((index, element) in targetElement.asJsonArray.withIndex()) {
            if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                issues.add(KmpProjectIssue(
                    "INVALID_TARGET_ENTRY",
                    KmpProjectIssueSeverity.ERROR,
                    "enabledTargets[$index]",
                    "Target entry must be a string"
                ))
                continue
            }

            val target = KmpTarget.fromId(element.asString)
            if (target == null) {
                issues.add(KmpProjectIssue(
                    "UNKNOWN_TARGET",
                    KmpProjectIssueSeverity.WARNING,
                    "enabledTargets[$index]",
                    "Unknown target '${element.asString}'"
                ))
                continue
            }
            parsedTargets.add(target)
        }

        val distinctTargets = parsedTargets.distinct()
        if (distinctTargets.isEmpty()) {
            issues.add(KmpProjectIssue(
                "NO_TARGETS_SELECTED",
                KmpProjectIssueSeverity.WARNING,
                "enabledTargets",
                "No valid targets were found, defaults will be used"
            ))
            return KmpProjectDefaults.DEFAULT_TARGETS
        }

        return distinctTargets
    }

    private fun readHierarchy(
        objectRoot: JsonObject,
        issues: MutableList<KmpProjectIssue>
    ): Map<String, List<String>> {
        if (!objectRoot.has("sourceSetHierarchy") || objectRoot["sourceSetHierarchy"].isJsonNull) {
            issues.add(KmpProjectIssue(
                "MISSING_SOURCE_SET_HIERARCHY",
                KmpProjectIssueSeverity.WARNING,
                "sourceSetHierarchy",
                "sourceSetHierarchy is missing, defaults will be used"
            ))
            return KmpProjectDefaults.defaultHierarchy()
        }

        val hierarchyElement = objectRoot["sourceSetHierarchy"]
        if (!hierarchyElement.isJsonObject) {
            issues.add(KmpProjectIssue(
                "INVALID_SOURCE_SET_HIERARCHY",
                KmpProjectIssueSeverity.ERROR,
                "sourceSetHierarchy",
                "sourceSetHierarchy must be a JSON object"
            ))
            return KmpProjectDefaults.defaultHierarchy()
        }

        val hierarchy = linkedMapOf<String, List<String>>()
        for ((sourceSetName, parentsElement) in hierarchyElement.asJsonObject.entrySet()) {
            val trimmedSourceSetName = sourceSetName.trim()
            if (trimmedSourceSetName.isEmpty()) {
                issues.add(KmpProjectIssue(
                    "INVALID_SOURCE_SET_NAME",
                    KmpProjectIssueSeverity.ERROR,
                    "sourceSetHierarchy",
                    "Source set name must not be blank"
                ))
                continue
            }

            if (!parentsElement.isJsonArray) {
                issues.add(KmpProjectIssue(
                    "INVALID_SOURCE_SET_PARENTS",
                    KmpProjectIssueSeverity.ERROR,
                    "sourceSetHierarchy.$trimmedSourceSetName",
                    "Parent list must be a JSON array"
                ))
                continue
            }

            val parents = mutableListOf<String>()
            for ((index, parentElement) in parentsElement.asJsonArray.withIndex()) {
                if (!parentElement.isJsonPrimitive || !parentElement.asJsonPrimitive.isString) {
                    issues.add(KmpProjectIssue(
                        "INVALID_PARENT_ENTRY",
                        KmpProjectIssueSeverity.ERROR,
                        "sourceSetHierarchy.$trimmedSourceSetName[$index]",
                        "Parent entry must be a string"
                    ))
                    continue
                }
                parents.add(parentElement.asString.trim())
            }

            hierarchy[trimmedSourceSetName] = parents.filter { it.isNotEmpty() }.distinct()
        }

        if (hierarchy.isEmpty()) {
            issues.add(KmpProjectIssue(
                "EMPTY_SOURCE_SET_HIERARCHY",
                KmpProjectIssueSeverity.WARNING,
                "sourceSetHierarchy",
                "No valid hierarchy entries were found, defaults will be used"
            ))
            return KmpProjectDefaults.defaultHierarchy()
        }

        return hierarchy
    }

    private fun readRequiredString(
        objectRoot: JsonObject,
        key: String,
        issues: MutableList<KmpProjectIssue>
    ): String {
        if (!objectRoot.has(key) || objectRoot[key].isJsonNull) {
            issues.add(KmpProjectIssue(
                "MISSING_REQUIRED_FIELD",
                KmpProjectIssueSeverity.ERROR,
                key,
                "Missing required field: $key"
            ))
            return ""
        }

        val element = objectRoot[key]
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            issues.add(KmpProjectIssue(
                "INVALID_FIELD_TYPE",
                KmpProjectIssueSeverity.ERROR,
                key,
                "Field '$key' must be a string"
            ))
            return ""
        }

        val value = element.asString.trim()
        if (value.isEmpty()) {
            issues.add(KmpProjectIssue(
                "EMPTY_REQUIRED_FIELD",
                KmpProjectIssueSeverity.ERROR,
                key,
                "Required field '$key' must not be empty"
            ))
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

    private fun readLong(objectRoot: JsonObject, key: String, fallback: Long): Long {
        if (!objectRoot.has(key) || objectRoot[key].isJsonNull) {
            return fallback
        }

        val element = objectRoot[key]
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) {
            return fallback
        }

        return try {
            element.asLong
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
