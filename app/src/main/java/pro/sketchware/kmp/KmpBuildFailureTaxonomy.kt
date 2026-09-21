package pro.sketchware.kmp

import java.util.Locale

enum class KmpBuildFailureCategory {
    ENVIRONMENT,
    TOOLCHAIN,
    CONFIGURATION,
    DEPENDENCY,
    COMPILATION,
    TIMEOUT,
    UNSUPPORTED,
    UNKNOWN
}

class KmpBuildFailureTaxonomyEntry(
    @JvmField val code: String,
    @JvmField val category: KmpBuildFailureCategory,
    @JvmField val summary: String,
    @JvmField val remediation: String,
    @JvmField val patterns: List<String>
)

class KmpBuildFailureDiagnostic(
    @JvmField val target: KmpTarget,
    @JvmField val status: KmpTargetBuildStatus,
    @JvmField val code: String,
    @JvmField val category: KmpBuildFailureCategory,
    @JvmField val summary: String,
    @JvmField val originalMessage: String,
    @JvmField val remediation: String,
    @JvmField val artifactPath: String?
)

class KmpBuildDiagnosticsReport(
    @JvmField val generatedAtMs: Long,
    @JvmField val totalFailures: Int,
    @JvmField val matchedFailures: Int,
    @JvmField val diagnostics: List<KmpBuildFailureDiagnostic>
)

object KmpBuildFailureTaxonomy {
    private val UNKNOWN_ENTRY = KmpBuildFailureTaxonomyEntry(
        "KMP_ERR_UNKNOWN",
        KmpBuildFailureCategory.UNKNOWN,
        "Build failed with an unknown reason.",
        "Open the Gradle output for the failed target and inspect the first explicit error line. Then rerun build after fixing that error.",
        emptyList()
    )

    private val UNSUPPORTED_ENTRY = KmpBuildFailureTaxonomyEntry(
        "KMP_ERR_UNSUPPORTED_TARGET",
        KmpBuildFailureCategory.UNSUPPORTED,
        "Selected target is not yet supported by orchestrator execution.",
        "Disable unsupported targets for now or switch to Android/Desktop until runner support is added for this target.",
        listOf("execution stub pending", "not supported")
    )

    private val TOP_COMMON_FAILURES = listOf(
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_GRADLE_WRAPPER_MISSING",
            KmpBuildFailureCategory.ENVIRONMENT,
            "Gradle wrapper is missing in the KMP project.",
            "Ensure gradlew and gradle/wrapper/gradle-wrapper.jar exist in the KMP root. Regenerate scaffold or run Gradle wrapper task.",
            listOf("gradle wrapper script not found")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_BUILD_TIMEOUT",
            KmpBuildFailureCategory.TIMEOUT,
            "KMP target build timed out before completion.",
            "Increase KMP bridge timeout in Build Settings or reduce build load (clean stale outputs, avoid parallel heavy tasks).",
            listOf("timed out while executing")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_JAVA_VERSION",
            KmpBuildFailureCategory.TOOLCHAIN,
            "JDK version is incompatible with current Gradle/Kotlin toolchain.",
            "Use the supported JDK (Java 17 for this project baseline) and rerun sync/build.",
            listOf("unsupported class file major version", "requires java", "invalid source release")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_ANDROID_SDK_MISSING",
            KmpBuildFailureCategory.ENVIRONMENT,
            "Android SDK path is missing or invalid.",
            "Configure sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT, then install required platform/build-tools.",
            listOf("sdk location not found", "failed to find target with hash string", "android sdk")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_ANDROID_LICENSE",
            KmpBuildFailureCategory.ENVIRONMENT,
            "Android SDK licenses are not accepted for required packages.",
            "Run sdkmanager --licenses with the same SDK root used by Gradle and accept all pending licenses.",
            listOf("licenses have not been accepted", "license for package")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_DEPENDENCY_RESOLUTION",
            KmpBuildFailureCategory.DEPENDENCY,
            "Gradle dependency resolution failed.",
            "Verify repositories, dependency coordinates, and network/proxy access. Pin versions compatible with selected KMP targets.",
            listOf("could not resolve", "failed to resolve", "dependency resolution")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_DUPLICATE_CLASSES",
            KmpBuildFailureCategory.DEPENDENCY,
            "Duplicate classes detected in dependency graph.",
            "Exclude conflicting transitive artifacts or align dependency versions to a single class provider.",
            listOf("duplicate class", "checkduplicateclasses")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_KOTLIN_UNRESOLVED",
            KmpBuildFailureCategory.COMPILATION,
            "Kotlin compilation has unresolved references.",
            "Check expect/actual declarations, imports, and source-set visibility for the failing target.",
            listOf("unresolved reference")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_COMPILATION_SYMBOL",
            KmpBuildFailureCategory.COMPILATION,
            "Compilation failed due to missing symbols or API mismatch.",
            "Fix reported symbol/type errors and ensure code generated for selected target matches available APIs.",
            listOf("cannot find symbol", "compilation failed", "type mismatch")
        ),
        KmpBuildFailureTaxonomyEntry(
            "KMP_ERR_PLUGIN_CONFIGURATION",
            KmpBuildFailureCategory.CONFIGURATION,
            "Gradle plugin configuration is invalid or incompatible.",
            "Verify plugin versions (AGP/Kotlin/Compose), plugin blocks, and required project properties in gradle.properties/local.properties.",
            listOf("plugin with id", "is not compatible", "plugin requires")
        )
    )

    @JvmStatic
    fun topFailureCodes(): List<String> {
        return TOP_COMMON_FAILURES.map { it.code }
    }

    @JvmStatic
    fun diagnose(report: KmpBuildOrchestrationReport): KmpBuildDiagnosticsReport {
        val relevant = report.results.filter {
            it.status == KmpTargetBuildStatus.FAILED || it.status == KmpTargetBuildStatus.NOT_SUPPORTED
        }

        val diagnostics = relevant.map { classify(it) }
        val matched = diagnostics.count { it.code != UNKNOWN_ENTRY.code }

        return KmpBuildDiagnosticsReport(
            System.currentTimeMillis(),
            diagnostics.size,
            matched,
            diagnostics
        )
    }

    @JvmStatic
    fun classify(result: KmpTargetBuildResult): KmpBuildFailureDiagnostic {
        val selectedEntry = when {
            result.status == KmpTargetBuildStatus.NOT_SUPPORTED -> UNSUPPORTED_ENTRY
            else -> findMatch(result.message) ?: UNKNOWN_ENTRY
        }

        return KmpBuildFailureDiagnostic(
            result.target,
            result.status,
            selectedEntry.code,
            selectedEntry.category,
            selectedEntry.summary,
            safeMessage(result.message),
            selectedEntry.remediation,
            result.artifactPath
        )
    }

    private fun findMatch(message: String?): KmpBuildFailureTaxonomyEntry? {
        val normalized = safeMessage(message).lowercase(Locale.ROOT)
        if (normalized.isEmpty()) {
            return null
        }

        return TOP_COMMON_FAILURES.firstOrNull { entry ->
            entry.patterns.any { pattern -> normalized.contains(pattern.lowercase(Locale.ROOT)) }
        }
    }

    private fun safeMessage(message: String?): String {
        val normalized = message?.trim().orEmpty()
        return if (normalized.isEmpty()) {
            "Unknown failure"
        } else {
            normalized
        }
    }
}