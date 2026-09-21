package pro.sketchware.metrics

import java.util.ArrayList
import java.util.Collections

class KpiDashboardReleaseGatesResult(gates: List<KpiDashboardReleaseGate?>?) {
    @JvmField
    val gates: List<KpiDashboardReleaseGate?> = Collections.unmodifiableList(
        ArrayList(gates ?: emptyList())
    )

    @JvmField
    val passCount: Int

    @JvmField
    val warnCount: Int

    @JvmField
    val failCount: Int

    init {
        var pass = 0
        var warn = 0
        var fail = 0
        for (gate in this.gates) {
            if (gate == null) {
                continue
            }
            when (gate.status) {
                KpiDashboardReleaseGateStatus.PASS -> pass++
                KpiDashboardReleaseGateStatus.FAIL -> fail++
                else -> warn++
            }
        }

        passCount = pass
        warnCount = warn
        failCount = fail
    }

    fun overallStatus(): KpiDashboardReleaseGateStatus {
        if (failCount > 0) {
            return KpiDashboardReleaseGateStatus.FAIL
        }
        if (warnCount > 0) {
            return KpiDashboardReleaseGateStatus.WARN
        }
        return KpiDashboardReleaseGateStatus.PASS
    }
}
