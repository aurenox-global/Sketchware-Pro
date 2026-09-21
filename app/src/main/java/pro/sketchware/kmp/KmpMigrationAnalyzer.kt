package pro.sketchware.kmp

import kotlin.math.round

enum class KmpMigrationRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

class KmpMigrationAnalysisSummary(
    @JvmField val totalDependencies: Int,
    @JvmField val compatibleCount: Int,
    @JvmField val partiallyCompatibleCount: Int,
    @JvmField val incompatibleCount: Int,
    @JvmField val unknownCount: Int,
    @JvmField val migrableRatio: Double,
    @JvmField val estimatedManualWorkHours: Int,
    @JvmField val riskLevel: KmpMigrationRiskLevel,
    @JvmField val riskSummary: String
)

class KmpMigrationAnalysisReport(
    @JvmField val projectId: String,
    @JvmField val generatedAtMs: Long,
    @JvmField val summary: KmpMigrationAnalysisSummary,
    @JvmField val compatibilityResult: KmpDependencyCompatibilityResult,
    @JvmField val topRisks: List<String>
)

object KmpMigrationAnalyzer {
    @JvmStatic
    fun analyze(
        projectId: String,
        dependencyCoordinates: List<String>?,
        enabledTargets: List<KmpTarget>?
    ): KmpMigrationAnalysisReport {
        val compatibility = KmpDependencyCompatibilityResolver.resolve(dependencyCoordinates, enabledTargets)

        val compatibleCount = compatibility.entries.count {
            it.status == KmpDependencyCompatibilityStatus.COMPATIBLE
        }
        val partiallyCompatibleCount = compatibility.entries.count {
            it.status == KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE
        }
        val incompatibleCount = compatibility.entries.count {
            it.status == KmpDependencyCompatibilityStatus.INCOMPATIBLE
        }
        val unknownCount = compatibility.entries.count {
            it.status == KmpDependencyCompatibilityStatus.UNKNOWN
        }

        val totalDependencies = compatibility.entries.size
        val weightedMigrable = compatibleCount + (partiallyCompatibleCount * 0.5)
        val migrableRatio = if (totalDependencies == 0) {
            1.0
        } else {
            round((weightedMigrable / totalDependencies) * 1000.0) / 1000.0
        }

        val estimatedManualWorkHours = (partiallyCompatibleCount * 2) + (incompatibleCount * 4) + (unknownCount * 3)
        val riskLevel = resolveRiskLevel(partiallyCompatibleCount, incompatibleCount, unknownCount)
        val riskSummary = "Migration risk is $riskLevel (partial=$partiallyCompatibleCount, incompatible=$incompatibleCount, unknown=$unknownCount)."

        val topRisks = compatibility.entries
            .flatMap { entry -> entry.diagnostics.map { it.message } }
            .distinct()
            .take(5)

        val summary = KmpMigrationAnalysisSummary(
            totalDependencies,
            compatibleCount,
            partiallyCompatibleCount,
            incompatibleCount,
            unknownCount,
            migrableRatio,
            estimatedManualWorkHours,
            riskLevel,
            riskSummary
        )

        return KmpMigrationAnalysisReport(
            projectId.trim().ifEmpty { "unknown-project" },
            System.currentTimeMillis(),
            summary,
            compatibility,
            topRisks
        )
    }

    private fun resolveRiskLevel(
        partiallyCompatibleCount: Int,
        incompatibleCount: Int,
        unknownCount: Int
    ): KmpMigrationRiskLevel {
        if (incompatibleCount > 0 || unknownCount > 1) {
            return KmpMigrationRiskLevel.HIGH
        }
        if (partiallyCompatibleCount > 0 || unknownCount > 0) {
            return KmpMigrationRiskLevel.MEDIUM
        }
        return KmpMigrationRiskLevel.LOW
    }
}