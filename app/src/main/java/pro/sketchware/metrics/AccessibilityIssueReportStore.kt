package pro.sketchware.metrics

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.max
import pro.sketchware.accessibility.AccessibilityIssueReport
import pro.sketchware.accessibility.AccessibilityIssueSeverity

object AccessibilityIssueReportStore {
    private const val PREF_NAME = "accessibility_issue_reports"
    private const val KEY_TOTAL_SCANS = "total_scans"
    private const val KEY_TOTAL_ISSUES = "total_issues"
    private const val KEY_TOTAL_WARNINGS = "total_warnings"
    private const val KEY_TOTAL_ERRORS = "total_errors"
    private const val KEY_LAST_SCREEN_ID = "last_screen_id"
    private const val KEY_LAST_ISSUE_COUNT = "last_issue_count"
    private const val KEY_LAST_WARNING_COUNT = "last_warning_count"
    private const val KEY_LAST_ERROR_COUNT = "last_error_count"
    private const val KEY_LAST_TIMESTAMP_MS = "last_timestamp_ms"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"

    private val LOCK = Any()

    @JvmStatic
    fun recordReport(context: Context?, report: AccessibilityIssueReport?) {
        if (context == null || report == null) {
            return
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val totalScans = prefs.getInt(KEY_TOTAL_SCANS, 0) + 1
            var totalIssues = prefs.getInt(KEY_TOTAL_ISSUES, 0)
            var totalWarnings = prefs.getInt(KEY_TOTAL_WARNINGS, 0)
            var totalErrors = prefs.getInt(KEY_TOTAL_ERRORS, 0)

            var lastIssueCount = 0
            var lastWarningCount = 0
            var lastErrorCount = 0
            for (issue in report.issues) {
                if (issue == null) {
                    continue
                }
                lastIssueCount++
                when (issue.severity) {
                    AccessibilityIssueSeverity.ERROR -> lastErrorCount++
                    AccessibilityIssueSeverity.WARNING -> lastWarningCount++
                    else -> Unit
                }
            }

            totalIssues += lastIssueCount
            totalWarnings += lastWarningCount
            totalErrors += lastErrorCount

            val durationMs = max(report.finishedAtMs - report.startedAtMs, 0L)

            prefs.edit()
                .putInt(KEY_TOTAL_SCANS, totalScans)
                .putInt(KEY_TOTAL_ISSUES, totalIssues)
                .putInt(KEY_TOTAL_WARNINGS, totalWarnings)
                .putInt(KEY_TOTAL_ERRORS, totalErrors)
                .putString(KEY_LAST_SCREEN_ID, report.screenId)
                .putInt(KEY_LAST_ISSUE_COUNT, lastIssueCount)
                .putInt(KEY_LAST_WARNING_COUNT, lastWarningCount)
                .putInt(KEY_LAST_ERROR_COUNT, lastErrorCount)
                .putLong(KEY_LAST_TIMESTAMP_MS, report.finishedAtMs)
                .putLong(KEY_LAST_DURATION_MS, durationMs)
                .apply()
        }
    }

    @JvmStatic
    fun snapshot(context: Context?): Snapshot {
        if (context == null) {
            return Snapshot.empty()
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val totalScans = prefs.getInt(KEY_TOTAL_SCANS, 0)
            val totalIssues = prefs.getInt(KEY_TOTAL_ISSUES, 0)
            val totalWarnings = prefs.getInt(KEY_TOTAL_WARNINGS, 0)
            val totalErrors = prefs.getInt(KEY_TOTAL_ERRORS, 0)
            val issueRate = if (totalScans == 0) 0.0 else (totalIssues * 100.0) / totalScans

            return Snapshot(
                totalScans,
                totalIssues,
                totalWarnings,
                totalErrors,
                issueRate,
                prefs.getString(KEY_LAST_SCREEN_ID, ""),
                prefs.getInt(KEY_LAST_ISSUE_COUNT, 0),
                prefs.getInt(KEY_LAST_WARNING_COUNT, 0),
                prefs.getInt(KEY_LAST_ERROR_COUNT, 0),
                prefs.getLong(KEY_LAST_TIMESTAMP_MS, 0L),
                prefs.getLong(KEY_LAST_DURATION_MS, 0L)
            )
        }
    }

    @JvmStatic
    fun clear(context: Context?) {
        if (context == null) {
            return
        }

        synchronized(LOCK) {
            prefs(context).edit().clear().apply()
        }
    }

    @JvmStatic
    fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value)
    }

    @JvmStatic
    fun formatDuration(millis: Long): String {
        if (millis < 1000L) {
            return "$millis ms"
        }
        return String.format(Locale.US, "%.2f s", millis / 1000f)
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    class Snapshot(
        totalScans: Int,
        totalIssues: Int,
        totalWarnings: Int,
        totalErrors: Int,
        issueRate: Double,
        lastScreenId: String?,
        lastIssueCount: Int,
        lastWarningCount: Int,
        lastErrorCount: Int,
        lastTimestampMs: Long,
        lastDurationMs: Long
    ) {
        @JvmField
        val totalScans: Int = max(totalScans, 0)

        @JvmField
        val totalIssues: Int = max(totalIssues, 0)

        @JvmField
        val totalWarnings: Int = max(totalWarnings, 0)

        @JvmField
        val totalErrors: Int = max(totalErrors, 0)

        @JvmField
        val issueRate: Double = max(issueRate, 0.0)

        @JvmField
        val lastScreenId: String = lastScreenId ?: ""

        @JvmField
        val lastIssueCount: Int = max(lastIssueCount, 0)

        @JvmField
        val lastWarningCount: Int = max(lastWarningCount, 0)

        @JvmField
        val lastErrorCount: Int = max(lastErrorCount, 0)

        @JvmField
        val lastTimestampMs: Long = max(lastTimestampMs, 0L)

        @JvmField
        val lastDurationMs: Long = max(lastDurationMs, 0L)

        companion object {
            @JvmStatic
            fun empty(): Snapshot {
                return Snapshot(0, 0, 0, 0, 0.0, "", 0, 0, 0, 0L, 0L)
            }
        }
    }
}
