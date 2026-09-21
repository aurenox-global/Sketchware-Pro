package pro.sketchware.metrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KpiDashboardReleaseGatesEvaluatorTest {

    @Test
    public void evaluate_returnsWarnWhenNoData() {
        KpiDashboardReleaseGatesResult result = KpiDashboardReleaseGatesEvaluator.evaluate(
                new KpiDashboardGateInputs(0, 0, 0L, 0, 0L, 0, 0, 0)
        );

        assertEquals(KpiDashboardReleaseGateStatus.WARN, result.overallStatus());
        assertEquals(0, result.failCount);
        assertEquals(4, result.warnCount);
    }

    @Test
    public void evaluate_returnsPassWhenAllThresholdsMeet() {
        KpiDashboardReleaseGatesResult result = KpiDashboardReleaseGatesEvaluator.evaluate(
                new KpiDashboardGateInputs(
                        100,
                        96,
                        1800L,
                        10,
                        300L,
                        20,
                        50,
                        1
                )
        );

        assertEquals(KpiDashboardReleaseGateStatus.PASS, result.overallStatus());
        assertEquals(4, result.passCount);
        assertEquals(0, result.warnCount);
        assertEquals(0, result.failCount);
    }

    @Test
    public void evaluate_returnsFailWhenAnyGateFails() {
        KpiDashboardReleaseGatesResult result = KpiDashboardReleaseGatesEvaluator.evaluate(
                new KpiDashboardGateInputs(
                        20,
                        10,
                        3200L,
                        6,
                        900L,
                        8,
                        10,
                        3
                )
        );

        assertEquals(KpiDashboardReleaseGateStatus.FAIL, result.overallStatus());
        assertEquals(0, result.passCount);
        assertEquals(0, result.warnCount);
        assertEquals(4, result.failCount);
    }
}
