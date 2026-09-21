package pro.sketchware.metrics

import kotlin.math.max

class KpiDashboardGateInputs(
    buildTotalCount: Int,
    buildSuccessCount: Int,
    startupP95Ms: Long,
    startupSampleCount: Int,
    editorDiagnosticsRenderP95Ms: Long,
    editorDiagnosticsRenderSampleCount: Int,
    accessibilityTotalScans: Int,
    accessibilityTotalErrors: Int
) {
    @JvmField
    val buildTotalCount: Int = max(buildTotalCount, 0)

    @JvmField
    val buildSuccessCount: Int = max(buildSuccessCount, 0)

    @JvmField
    val startupP95Ms: Long = max(startupP95Ms, 0L)

    @JvmField
    val startupSampleCount: Int = max(startupSampleCount, 0)

    @JvmField
    val editorDiagnosticsRenderP95Ms: Long = max(editorDiagnosticsRenderP95Ms, 0L)

    @JvmField
    val editorDiagnosticsRenderSampleCount: Int = max(editorDiagnosticsRenderSampleCount, 0)

    @JvmField
    val accessibilityTotalScans: Int = max(accessibilityTotalScans, 0)

    @JvmField
    val accessibilityTotalErrors: Int = max(accessibilityTotalErrors, 0)

    companion object {
        @JvmStatic
        fun fromSnapshot(snapshot: KpiDashboardSnapshot?): KpiDashboardGateInputs {
            if (snapshot == null) {
                return KpiDashboardGateInputs(0, 0, 0L, 0, 0L, 0, 0, 0)
            }

            return KpiDashboardGateInputs(
                snapshot.build?.totalCount ?: 0,
                snapshot.build?.successCount ?: 0,
                snapshot.startup?.total?.p95Ms ?: 0L,
                snapshot.startup?.total?.count ?: 0,
                snapshot.editor?.diagnosticsRender?.p95Ms ?: 0L,
                snapshot.editor?.diagnosticsRender?.count ?: 0,
                snapshot.accessibility?.totalScans ?: 0,
                snapshot.accessibility?.totalErrors ?: 0
            )
        }
    }
}
