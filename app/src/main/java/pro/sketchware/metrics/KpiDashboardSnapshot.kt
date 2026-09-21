package pro.sketchware.metrics

import kotlin.math.max

class KpiDashboardSnapshot(
    generatedAtMs: Long,
    @JvmField val build: BuildMetricsStore.Snapshot?,
    @JvmField val kmpBuild: KmpBuildPerformanceMetricsStore.Snapshot?,
    @JvmField val editor: EditorPerformanceMetricsStore.Snapshot?,
    @JvmField val startup: StartupPerformanceMetricsStore.Snapshot?,
    @JvmField val accessibility: AccessibilityIssueReportStore.Snapshot?
) {
    @JvmField
    val generatedAtMs: Long = max(generatedAtMs, 0L)
}
