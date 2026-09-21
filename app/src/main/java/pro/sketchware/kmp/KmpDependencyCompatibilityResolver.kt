package pro.sketchware.kmp

import java.util.Locale

enum class KmpDependencyCompatibilitySeverity {
    INFO,
    WARNING,
    ERROR
}

enum class KmpDependencyCompatibilityStatus {
    COMPATIBLE,
    PARTIALLY_COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN
}

class KmpDependencyCompatibilityDiagnostic(
    @JvmField val code: String,
    @JvmField val targetId: String?,
    @JvmField val message: String,
    @JvmField val suggestion: String,
    @JvmField val severity: KmpDependencyCompatibilitySeverity,
    @JvmField val actionId: String?
)

class KmpDependencyTargetCompatibility(
    @JvmField val target: KmpTarget,
    @JvmField val supported: Boolean
)

class KmpDependencyCompatibilityEntry(
    @JvmField val coordinate: String,
    @JvmField val normalizedCoordinate: String,
    @JvmField val status: KmpDependencyCompatibilityStatus,
    @JvmField val targetCompatibility: List<KmpDependencyTargetCompatibility>,
    @JvmField val diagnostics: List<KmpDependencyCompatibilityDiagnostic>
) {
    fun findTarget(target: KmpTarget): KmpDependencyTargetCompatibility? {
        return targetCompatibility.firstOrNull { it.target == target }
    }
}

class KmpDependencyCompatibilityResult(
    @JvmField val entries: List<KmpDependencyCompatibilityEntry>
) {
    fun findByNormalizedCoordinate(normalizedCoordinate: String): KmpDependencyCompatibilityEntry? {
        val normalized = normalizedCoordinate.trim().lowercase(Locale.ROOT)
        return entries.firstOrNull { it.normalizedCoordinate == normalized }
    }
}

object KmpDependencyCompatibilityResolver {
    private const val UNSUPPORTED_TARGET_CODE = "UNSUPPORTED_TARGET"
    private const val UNKNOWN_DEPENDENCY_CODE = "UNKNOWN_DEPENDENCY"

    private class Fixture(
        val supportedTargets: Set<KmpTarget>,
        val suggestion: String
    )

    private val ANDROID_ONLY_TARGETS = linkedSetOf(KmpTarget.ANDROID)

    private val JVM_TARGETS = linkedSetOf(
        KmpTarget.ANDROID,
        KmpTarget.DESKTOP,
        KmpTarget.LINUX_X64
    )

    private val ALL_TARGETS = linkedSetOf(
        KmpTarget.ANDROID,
        KmpTarget.DESKTOP,
        KmpTarget.WASM_JS,
        KmpTarget.IOS_ARM64,
        KmpTarget.IOS_SIMULATOR_ARM64,
        KmpTarget.IOS_X64,
        KmpTarget.LINUX_X64
    )

    private val FIXTURES = linkedMapOf(
        "androidx.appcompat:appcompat" to Fixture(
            ANDROID_ONLY_TARGETS,
            "Move usage to androidMain or replace with a multiplatform UI abstraction."
        ),
        "com.google.android.material:material" to Fixture(
            ANDROID_ONLY_TARGETS,
            "Move usage to androidMain or replace with Compose Multiplatform components."
        ),
        "androidx.room:room-runtime" to Fixture(
            ANDROID_ONLY_TARGETS,
            "Move usage to androidMain or use a multiplatform persistence library."
        ),
        "com.squareup.retrofit2:retrofit" to Fixture(
            JVM_TARGETS,
            "Use a multiplatform networking stack or move usage to JVM targets."
        ),
        "com.google.code.gson:gson" to Fixture(
            JVM_TARGETS,
            "Use a multiplatform serialization stack or move usage to JVM targets."
        ),
        "io.reactivex.rxjava3:rxjava" to Fixture(
            JVM_TARGETS,
            "Use coroutines/Flow for shared async pipelines or move usage to JVM targets."
        ),
        "org.jetbrains.kotlinx:kotlinx-coroutines-core" to Fixture(
            ALL_TARGETS,
            "Compatible across configured KMP targets."
        ),
        "org.jetbrains.kotlinx:kotlinx-serialization-json" to Fixture(
            ALL_TARGETS,
            "Compatible across configured KMP targets."
        ),
        "io.ktor:ktor-client-core" to Fixture(
            ALL_TARGETS,
            "Compatible across configured KMP targets."
        ),
        "io.ktor:ktor-client-okhttp" to Fixture(
            JVM_TARGETS,
            "Use platform-specific Ktor engines for non-JVM targets."
        )
    )

    @JvmStatic
    fun resolve(
        dependencyCoordinates: List<String>?,
        enabledTargets: List<KmpTarget>?
    ): KmpDependencyCompatibilityResult {
        val effectiveTargets = if (enabledTargets.isNullOrEmpty()) {
            KmpProjectDefaults.DEFAULT_TARGETS
        } else {
            enabledTargets.distinct()
        }

        if (dependencyCoordinates.isNullOrEmpty()) {
            return KmpDependencyCompatibilityResult(emptyList())
        }

        val normalizedDependencies = linkedMapOf<String, String>()
        for (raw in dependencyCoordinates) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            val normalized = normalizeCoordinate(trimmed)
            if (normalized.isNotEmpty() && !normalizedDependencies.containsKey(normalized)) {
                normalizedDependencies[normalized] = trimmed
            }
        }

        val entries = normalizedDependencies.map { (normalizedCoordinate, originalCoordinate) ->
            val fixture = FIXTURES[normalizedCoordinate]
            if (fixture == null) {
                return@map buildUnknownEntry(originalCoordinate, normalizedCoordinate, effectiveTargets)
            }

            val targetCompatibility = effectiveTargets.map { target ->
                KmpDependencyTargetCompatibility(target, fixture.supportedTargets.contains(target))
            }

            val unsupportedTargets = targetCompatibility.filter { !it.supported }
            val status = when {
                unsupportedTargets.isEmpty() -> KmpDependencyCompatibilityStatus.COMPATIBLE
                unsupportedTargets.size == targetCompatibility.size -> KmpDependencyCompatibilityStatus.INCOMPATIBLE
                else -> KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE
            }

            val diagnostics = unsupportedTargets.map {
                val suggestionText = KmpDependencyAlternativeSuggestionCatalog
                    .buildSuggestionText(normalizedCoordinate)
                    ?: fixture.suggestion
                val actionId = KmpDependencyAlternativeSuggestionCatalog
                    .findSuggestion(normalizedCoordinate)
                    ?.actionId

                KmpDependencyCompatibilityDiagnostic(
                    UNSUPPORTED_TARGET_CODE,
                    it.target.name,
                    "Dependency $normalizedCoordinate is not supported on ${it.target.name}.",
                    suggestionText,
                    if (status == KmpDependencyCompatibilityStatus.INCOMPATIBLE) {
                        KmpDependencyCompatibilitySeverity.ERROR
                    } else {
                        KmpDependencyCompatibilitySeverity.WARNING
                    },
                    actionId
                )
            }

            KmpDependencyCompatibilityEntry(
                originalCoordinate,
                normalizedCoordinate,
                status,
                targetCompatibility,
                diagnostics
            )
        }

        return KmpDependencyCompatibilityResult(entries)
    }

    private fun normalizeCoordinate(rawCoordinate: String): String {
        val withoutArtifactType = rawCoordinate.substringBefore('@').trim()
        if (withoutArtifactType.isEmpty()) {
            return ""
        }

        val parts = withoutArtifactType
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.size < 2) {
            return withoutArtifactType.lowercase(Locale.ROOT)
        }

        return (parts[0] + ":" + parts[1]).lowercase(Locale.ROOT)
    }

    private fun buildUnknownEntry(
        coordinate: String,
        normalizedCoordinate: String,
        enabledTargets: List<KmpTarget>
    ): KmpDependencyCompatibilityEntry {
        val targetCompatibility = enabledTargets.map { target ->
            KmpDependencyTargetCompatibility(target, false)
        }

        val diagnostics = listOf(
            KmpDependencyCompatibilityDiagnostic(
                UNKNOWN_DEPENDENCY_CODE,
                null,
                "Dependency $normalizedCoordinate is not present in the compatibility fixture matrix.",
                "Verify support in upstream docs and add a compatibility fixture before migration.",
                KmpDependencyCompatibilitySeverity.WARNING,
                null
            )
        )

        return KmpDependencyCompatibilityEntry(
            coordinate,
            normalizedCoordinate,
            KmpDependencyCompatibilityStatus.UNKNOWN,
            targetCompatibility,
            diagnostics
        )
    }
}