package pro.sketchware.kmp

import com.google.gson.GsonBuilder
import java.util.Locale

object KmpBuildDiagnosticsReportSerializer {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    @JvmStatic
    fun toJson(report: KmpBuildDiagnosticsReport): String {
        return gson.toJson(report)
    }

    @JvmStatic
    fun toLogLine(report: KmpBuildDiagnosticsReport): String {
        val environment = report.diagnostics.count { it.category == KmpBuildFailureCategory.ENVIRONMENT }
        val toolchain = report.diagnostics.count { it.category == KmpBuildFailureCategory.TOOLCHAIN }
        val configuration = report.diagnostics.count { it.category == KmpBuildFailureCategory.CONFIGURATION }
        val dependency = report.diagnostics.count { it.category == KmpBuildFailureCategory.DEPENDENCY }
        val compilation = report.diagnostics.count { it.category == KmpBuildFailureCategory.COMPILATION }
        val timeout = report.diagnostics.count { it.category == KmpBuildFailureCategory.TIMEOUT }
        val unsupported = report.diagnostics.count { it.category == KmpBuildFailureCategory.UNSUPPORTED }
        val unknown = report.diagnostics.count { it.category == KmpBuildFailureCategory.UNKNOWN }

        return String.format(
            Locale.US,
            "KMP diagnostics: failures=%d matched=%d env=%d toolchain=%d config=%d dependency=%d compilation=%d timeout=%d unsupported=%d unknown=%d",
            report.totalFailures,
            report.matchedFailures,
            environment,
            toolchain,
            configuration,
            dependency,
            compilation,
            timeout,
            unsupported,
            unknown
        )
    }

    @JvmStatic
    fun formatForUser(report: KmpBuildDiagnosticsReport, maxItems: Int): String {
        if (report.totalFailures == 0 || report.diagnostics.isEmpty()) {
            return "KMP diagnostics: no actionable failures."
        }

        val safeMax = maxItems.coerceAtLeast(1)
        val builder = StringBuilder()
        builder.append("KMP diagnostics with remediation:")

        var shown = 0
        for (diagnostic in report.diagnostics) {
            if (shown >= safeMax) {
                break
            }

            builder.append("\n")
                .append("- ")
                .append(diagnostic.target.name)
                .append(" [")
                .append(diagnostic.code)
                .append("] ")
                .append(diagnostic.summary)
                .append(" Remediation: ")
                .append(diagnostic.remediation)

            shown++
        }

        if (report.diagnostics.size > safeMax) {
            builder.append("\n")
                .append("+ ")
                .append(report.diagnostics.size - safeMax)
                .append(" more failure(s).")
        }

        return builder.toString()
    }
}