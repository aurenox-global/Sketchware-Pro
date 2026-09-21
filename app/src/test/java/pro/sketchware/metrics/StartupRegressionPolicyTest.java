package pro.sketchware.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class StartupRegressionPolicyTest {

    @Test
    public void evaluate_withInsufficientHistory_usesDefaultThreshold() {
        StartupRegressionDecision decision = StartupRegressionPolicy.evaluate(
                Arrays.asList(900L, 1100L, 1000L),
                1500L
        );

        assertEquals(3, decision.sampleCount);
        assertEquals(StartupRegressionPolicy.DEFAULT_THRESHOLD_MS, decision.thresholdMs);
        assertFalse(decision.regression);
    }

    @Test
    public void evaluate_withEnoughHistory_usesDynamicThreshold() {
        StartupRegressionDecision decision = StartupRegressionPolicy.evaluate(
                Arrays.asList(2000L, 2100L, 2200L, 2050L, 2150L, 2080L),
                2600L
        );

        assertTrue(decision.thresholdMs > StartupRegressionPolicy.DEFAULT_THRESHOLD_MS);
        assertEquals(2080L, decision.baselineP50Ms);
        assertFalse(decision.regression);
    }

    @Test
    public void evaluate_marksRegressionWhenDurationExceedsThreshold() {
        StartupRegressionDecision decision = StartupRegressionPolicy.evaluate(
                Arrays.asList(2000L, 2100L, 2200L, 2050L, 2150L, 2080L),
                3200L
        );

        assertTrue(decision.regression);
    }

    @Test
    public void evaluate_handlesEmptyHistory() {
        StartupRegressionDecision decision = StartupRegressionPolicy.evaluate(Collections.emptyList(), 5000L);

        assertEquals(0, decision.sampleCount);
        assertEquals(0L, decision.baselineP50Ms);
        assertEquals(StartupRegressionPolicy.DEFAULT_THRESHOLD_MS, decision.thresholdMs);
        assertTrue(decision.regression);
    }
}
