package pro.sketchware.kmp

import com.google.gson.GsonBuilder
import java.util.Locale

object KmpBuildOrchestrationReportSerializer {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    @JvmStatic
    fun toJson(report: KmpBuildOrchestrationReport): String {
        return gson.toJson(report)
    }

    @JvmStatic
    fun toLogLine(report: KmpBuildOrchestrationReport): String {
        val success = report.results.count { it.status == KmpTargetBuildStatus.SUCCESS }
        val failed = report.results.count { it.status == KmpTargetBuildStatus.FAILED }
        val notSupported = report.results.count { it.status == KmpTargetBuildStatus.NOT_SUPPORTED }
        val skipped = report.results.count { it.status == KmpTargetBuildStatus.SKIPPED }
        val durationMs = report.finishedAtMs - report.startedAtMs

        return String.format(
            Locale.US,
            "KMP orchestrator report: success=%d failed=%d skipped=%d notSupported=%d durationMs=%d",
            success,
            failed,
            skipped,
            notSupported,
            durationMs
        )
    }
}
