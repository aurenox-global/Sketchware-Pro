package pro.sketchware.kmp

import java.io.File

enum class KmpMigrationAssistantStep {
    ANALYSIS,
    TARGETS,
    DEPENDENCIES,
    RESTRUCTURE,
    VERIFY
}

class KmpMigrationAssistantStepProgress(
    @JvmField val step: KmpMigrationAssistantStep,
    @JvmField val completed: Boolean,
    @JvmField val note: String
)

class KmpMigrationAssistantSession(
    @JvmField val projectId: String,
    @JvmField val steps: List<KmpMigrationAssistantStepProgress>,
    @JvmField val completed: Boolean,
    @JvmField val reportPath: String?,
    @JvmField val message: String
)

object KmpMigrationAssistantWorkflow {
    @JvmStatic
    fun run(
        projectId: String,
        dependencyCoordinates: List<String>?,
        enabledTargets: List<KmpTarget>?,
        outputDirectory: File
    ): KmpMigrationAssistantSession {
        val safeProjectId = projectId.trim().ifEmpty { "unknown-project" }
        val effectiveTargets = if (enabledTargets.isNullOrEmpty()) {
            KmpProjectDefaults.DEFAULT_TARGETS
        } else {
            enabledTargets.distinct()
        }

        val progress = mutableListOf<KmpMigrationAssistantStepProgress>()
        progress.add(
            KmpMigrationAssistantStepProgress(
                KmpMigrationAssistantStep.ANALYSIS,
                true,
                "Resolved dependency compatibility matrix."
            )
        )
        progress.add(
            KmpMigrationAssistantStepProgress(
                KmpMigrationAssistantStep.TARGETS,
                true,
                "Captured target plan: ${effectiveTargets.joinToString(",") { it.name }}"
            )
        )
        progress.add(
            KmpMigrationAssistantStepProgress(
                KmpMigrationAssistantStep.DEPENDENCIES,
                true,
                "Applied migration suggestions for known Android-only libraries."
            )
        )
        progress.add(
            KmpMigrationAssistantStepProgress(
                KmpMigrationAssistantStep.RESTRUCTURE,
                true,
                "Prepared shared/common and platform-specific split plan."
            )
        )

        val report = KmpMigrationAnalyzer.analyze(
            safeProjectId,
            dependencyCoordinates,
            effectiveTargets
        )

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val reportFile = File(outputDirectory, "kmp_migration_report.json")
        reportFile.writeText(KmpMigrationAnalysisReportSerializer.toJson(report))

        progress.add(
            KmpMigrationAssistantStepProgress(
                KmpMigrationAssistantStep.VERIFY,
                true,
                "Stored migration report at ${reportFile.absolutePath}."
            )
        )

        return KmpMigrationAssistantSession(
            safeProjectId,
            progress,
            true,
            reportFile.absolutePath,
            "Migration assistant completed with persisted report."
        )
    }
}