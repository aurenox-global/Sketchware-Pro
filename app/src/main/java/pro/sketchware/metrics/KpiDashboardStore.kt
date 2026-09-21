package pro.sketchware.metrics

import android.content.Context
import java.util.Collections

object KpiDashboardStore {
    @JvmStatic
    fun snapshot(context: Context?): KpiDashboardSnapshot {
        if (context == null) {
            return KpiDashboardSnapshot(
                System.currentTimeMillis(),
                BuildMetricsStore.Snapshot(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    "-",
                    "-",
                    0L,
                    false,
                    false,
                    0L,
                    Collections.emptyList()
                ),
                KmpBuildPerformanceMetricsStore.Snapshot.empty(),
                EditorPerformanceMetricsStore.snapshot(null),
                StartupPerformanceMetricsStore.snapshot(null),
                AccessibilityIssueReportStore.snapshot(null)
            )
        }

        val appContext = context.applicationContext
        return KpiDashboardSnapshot(
            System.currentTimeMillis(),
            BuildMetricsStore.snapshot(appContext),
            KmpBuildPerformanceMetricsStore.snapshot(appContext),
            EditorPerformanceMetricsStore.snapshot(appContext),
            StartupPerformanceMetricsStore.snapshot(appContext),
            AccessibilityIssueReportStore.snapshot(appContext)
        )
    }

    @JvmStatic
    fun evaluateReleaseGates(context: Context?): KpiDashboardReleaseGatesResult {
        return KpiDashboardReleaseGatesEvaluator.evaluate(snapshot(context))
    }

    @JvmStatic
    fun clearAllMetrics(context: Context?) {
        if (context == null) {
            return
        }

        val appContext = context.applicationContext
        BuildMetricsStore.clear(appContext)
        KmpBuildPerformanceMetricsStore.clear(appContext)
        EditorPerformanceMetricsStore.clear(appContext)
        StartupPerformanceMetricsStore.clear(appContext)
        AccessibilityIssueReportStore.clear(appContext)
    }
}
