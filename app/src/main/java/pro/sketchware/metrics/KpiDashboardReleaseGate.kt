package pro.sketchware.metrics

class KpiDashboardReleaseGate(
    id: String?,
    label: String?,
    status: KpiDashboardReleaseGateStatus?,
    detail: String?
) {
    @JvmField
    val id: String = id ?: ""

    @JvmField
    val label: String = label ?: ""

    @JvmField
    val status: KpiDashboardReleaseGateStatus = status ?: KpiDashboardReleaseGateStatus.WARN

    @JvmField
    val detail: String = detail ?: ""
}
