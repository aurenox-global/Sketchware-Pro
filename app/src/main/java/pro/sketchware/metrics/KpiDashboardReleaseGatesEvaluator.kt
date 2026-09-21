package pro.sketchware.metrics

object KpiDashboardReleaseGatesEvaluator {
    private const val MIN_BUILD_SUCCESS_RATE_PERCENT = 90.0
    private const val MAX_STARTUP_P95_MS = 2500L
    private const val MAX_EDITOR_DIAGNOSTICS_P95_MS = 500L
    private const val MAX_ACCESSIBILITY_ERROR_RATE_PERCENT = 5.0

    @JvmStatic
    fun evaluate(snapshot: KpiDashboardSnapshot?): KpiDashboardReleaseGatesResult {
        return evaluate(KpiDashboardGateInputs.fromSnapshot(snapshot))
    }

    @JvmStatic
    fun evaluate(inputs: KpiDashboardGateInputs?): KpiDashboardReleaseGatesResult {
        val safeInputs = inputs ?: KpiDashboardGateInputs(0, 0, 0L, 0, 0L, 0, 0, 0)

        val gates = ArrayList<KpiDashboardReleaseGate>()
        gates.add(evaluateBuildSuccessRate(safeInputs))
        gates.add(evaluateStartupP95(safeInputs))
        gates.add(evaluateEditorDiagnosticsP95(safeInputs))
        gates.add(evaluateAccessibilityErrorRate(safeInputs))

        return KpiDashboardReleaseGatesResult(gates)
    }

    private fun evaluateBuildSuccessRate(inputs: KpiDashboardGateInputs): KpiDashboardReleaseGate {
        val total = inputs.buildTotalCount
        val success = inputs.buildSuccessCount
        if (total == 0) {
            return KpiDashboardReleaseGate(
                "build_success_rate",
                "Build Success Rate",
                KpiDashboardReleaseGateStatus.WARN,
                "No build samples yet"
            )
        }

        val successRate = (success * 100.0) / total
        val status = if (successRate >= MIN_BUILD_SUCCESS_RATE_PERCENT) {
            KpiDashboardReleaseGateStatus.PASS
        } else {
            KpiDashboardReleaseGateStatus.FAIL
        }

        return KpiDashboardReleaseGate(
            "build_success_rate",
            "Build Success Rate",
            status,
            BuildMetricsStore.formatPercent(successRate) + " (min " +
                BuildMetricsStore.formatPercent(MIN_BUILD_SUCCESS_RATE_PERCENT) + ")"
        )
    }

    private fun evaluateStartupP95(inputs: KpiDashboardGateInputs): KpiDashboardReleaseGate {
        val p95 = inputs.startupP95Ms
        val sampleCount = inputs.startupSampleCount

        if (sampleCount == 0) {
            return KpiDashboardReleaseGate(
                "startup_p95",
                "Startup P95",
                KpiDashboardReleaseGateStatus.WARN,
                "No startup samples yet"
            )
        }

        val status = if (p95 <= MAX_STARTUP_P95_MS) {
            KpiDashboardReleaseGateStatus.PASS
        } else {
            KpiDashboardReleaseGateStatus.FAIL
        }

        return KpiDashboardReleaseGate(
            "startup_p95",
            "Startup P95",
            status,
            StartupPerformanceMetricsStore.formatDuration(p95) +
                " (max " + StartupPerformanceMetricsStore.formatDuration(MAX_STARTUP_P95_MS) + ")"
        )
    }

    private fun evaluateEditorDiagnosticsP95(inputs: KpiDashboardGateInputs): KpiDashboardReleaseGate {
        val p95 = inputs.editorDiagnosticsRenderP95Ms
        val sampleCount = inputs.editorDiagnosticsRenderSampleCount

        if (sampleCount == 0) {
            return KpiDashboardReleaseGate(
                "editor_diagnostics_p95",
                "Editor Diagnostics P95",
                KpiDashboardReleaseGateStatus.WARN,
                "No diagnostics render samples yet"
            )
        }

        val status = if (p95 <= MAX_EDITOR_DIAGNOSTICS_P95_MS) {
            KpiDashboardReleaseGateStatus.PASS
        } else {
            KpiDashboardReleaseGateStatus.FAIL
        }

        return KpiDashboardReleaseGate(
            "editor_diagnostics_p95",
            "Editor Diagnostics P95",
            status,
            EditorPerformanceMetricsStore.formatDuration(p95) +
                " (max " + EditorPerformanceMetricsStore.formatDuration(MAX_EDITOR_DIAGNOSTICS_P95_MS) + ")"
        )
    }

    private fun evaluateAccessibilityErrorRate(inputs: KpiDashboardGateInputs): KpiDashboardReleaseGate {
        val scans = inputs.accessibilityTotalScans
        val errors = inputs.accessibilityTotalErrors

        if (scans == 0) {
            return KpiDashboardReleaseGate(
                "accessibility_error_rate",
                "Accessibility Error Rate",
                KpiDashboardReleaseGateStatus.WARN,
                "No accessibility scans yet"
            )
        }

        val errorRate = (errors * 100.0) / scans
        val status = if (errorRate <= MAX_ACCESSIBILITY_ERROR_RATE_PERCENT) {
            KpiDashboardReleaseGateStatus.PASS
        } else {
            KpiDashboardReleaseGateStatus.FAIL
        }

        return KpiDashboardReleaseGate(
            "accessibility_error_rate",
            "Accessibility Error Rate",
            status,
            AccessibilityIssueReportStore.formatPercent(errorRate) +
                " (max " + AccessibilityIssueReportStore.formatPercent(MAX_ACCESSIBILITY_ERROR_RATE_PERCENT) + ")"
        )
    }
}
