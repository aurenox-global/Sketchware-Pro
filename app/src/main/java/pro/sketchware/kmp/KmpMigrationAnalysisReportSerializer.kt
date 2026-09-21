package pro.sketchware.kmp

import com.google.gson.GsonBuilder
import java.util.Locale

object KmpMigrationAnalysisReportSerializer {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    @JvmStatic
    fun toJson(report: KmpMigrationAnalysisReport): String {
        return gson.toJson(report)
    }

    @JvmStatic
    fun toLogLine(report: KmpMigrationAnalysisReport): String {
        return String.format(
            Locale.US,
            "KMP migration analysis: projectId=%s total=%d ratio=%.3f estimatedHours=%d risk=%s",
            report.projectId,
            report.summary.totalDependencies,
            report.summary.migrableRatio,
            report.summary.estimatedManualWorkHours,
            report.summary.riskLevel
        )
    }
}