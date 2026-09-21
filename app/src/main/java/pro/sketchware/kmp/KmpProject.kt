package pro.sketchware.kmp

import kotlin.math.max

class KmpProject(
    schemaVersion: Int,
    id: String,
    name: String,
    packageName: String,
    kotlinVersion: String,
    composeMultiplatformVersion: String,
    enabledTargets: List<KmpTarget>?,
    sourceSetHierarchy: Map<String, List<String>>?,
    createdAtMs: Long,
    updatedAtMs: Long
) {
    @JvmField
    val schemaVersion: Int = if (schemaVersion <= 0) SUPPORTED_SCHEMA_VERSION else schemaVersion

    @JvmField
    val id: String = id.trim().ifEmpty { "kmp-project" }

    @JvmField
    val name: String = name.trim().ifEmpty { "KmpProject" }

    @JvmField
    val packageName: String = packageName.trim().ifEmpty { "pro.sketchware.kmp" }

    @JvmField
    val kotlinVersion: String = kotlinVersion.trim().ifEmpty { DEFAULT_KOTLIN_VERSION }

    @JvmField
    val composeMultiplatformVersion: String =
        composeMultiplatformVersion.trim().ifEmpty { DEFAULT_COMPOSE_MULTIPLATFORM_VERSION }

    @JvmField
    val enabledTargets: List<KmpTarget> = normalizeTargets(enabledTargets)

    @JvmField
    val sourceSetHierarchy: Map<String, List<String>> = normalizeHierarchy(sourceSetHierarchy)

    @JvmField
    val createdAtMs: Long = max(createdAtMs, 0L)

    @JvmField
    val updatedAtMs: Long = max(updatedAtMs, 0L)

    companion object {
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
        const val DEFAULT_KOTLIN_VERSION: String = "2.0.21"
        const val DEFAULT_COMPOSE_MULTIPLATFORM_VERSION: String = "1.7.0"

        @JvmStatic
        fun createDefault(id: String, name: String, packageName: String): KmpProject {
            val now = System.currentTimeMillis()
            return KmpProject(
                SUPPORTED_SCHEMA_VERSION,
                id,
                name,
                packageName,
                DEFAULT_KOTLIN_VERSION,
                DEFAULT_COMPOSE_MULTIPLATFORM_VERSION,
                KmpProjectDefaults.DEFAULT_TARGETS,
                KmpProjectDefaults.defaultHierarchy(),
                now,
                now
            )
        }

        private fun normalizeTargets(targets: List<KmpTarget>?): List<KmpTarget> {
            if (targets == null || targets.isEmpty()) {
                return KmpProjectDefaults.DEFAULT_TARGETS
            }

            return targets
                .distinct()
                .ifEmpty { KmpProjectDefaults.DEFAULT_TARGETS }
        }

        private fun normalizeHierarchy(hierarchy: Map<String, List<String>>?): Map<String, List<String>> {
            if (hierarchy == null || hierarchy.isEmpty()) {
                return KmpProjectDefaults.defaultHierarchy()
            }

            val normalized = linkedMapOf<String, List<String>>()
            for ((rawSourceSet, rawParents) in hierarchy) {
                val sourceSet = rawSourceSet.trim()
                if (sourceSet.isEmpty()) {
                    continue
                }

                val parents = rawParents
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                normalized[sourceSet] = parents
            }

            if (normalized.isEmpty()) {
                return KmpProjectDefaults.defaultHierarchy()
            }

            return normalized
        }
    }
}
