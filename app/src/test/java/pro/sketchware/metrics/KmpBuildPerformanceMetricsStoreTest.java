package pro.sketchware.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KmpBuildPerformanceMetricsStoreTest {

    @Test
    public void formatOperation_withStats_includesTrendFields() {
        KmpBuildPerformanceMetricsStore.OperationStats stats =
                new KmpBuildPerformanceMetricsStore.OperationStats(5, 120L, 620L, 300L, 250L, 600L, 280L);

        String summary = KmpBuildPerformanceMetricsStore.formatOperation("total", stats);

        assertTrue(summary.contains("n=5"));
        assertTrue(summary.contains("p50"));
        assertTrue(summary.contains("p95"));
        assertTrue(summary.contains("avg"));
        assertTrue(summary.contains("last"));
    }

    @Test
    public void formatTargetTrend_andStageSummary_areDeterministic() {
        KmpBuildPerformanceMetricsStore.TargetDurationTrend androidTrend =
                new KmpBuildPerformanceMetricsStore.TargetDurationTrend(
                        "ANDROID",
                        new KmpBuildPerformanceMetricsStore.OperationStats(2, 100L, 200L, 150L, 140L, 190L, 180L)
                );
        KmpBuildPerformanceMetricsStore.TargetDurationTrend desktopTrend =
                new KmpBuildPerformanceMetricsStore.TargetDurationTrend(
                        "DESKTOP",
                        new KmpBuildPerformanceMetricsStore.OperationStats(2, 300L, 500L, 400L, 390L, 490L, 410L)
                );

        String trend = KmpBuildPerformanceMetricsStore.formatTargetTrend(
                Arrays.asList(androidTrend, desktopTrend),
                4
        );

        assertTrue(trend.contains("ANDROID"));
        assertTrue(trend.contains("DESKTOP"));

        KmpBuildPerformanceMetricsStore.TargetStageDuration androidStage =
                new KmpBuildPerformanceMetricsStore.TargetStageDuration(
                        "ANDROID",
                        "SUCCESS",
                        1800L,
                        "android.apk",
                        "ok"
                );
        KmpBuildPerformanceMetricsStore.TargetStageDuration desktopStage =
                new KmpBuildPerformanceMetricsStore.TargetStageDuration(
                        "DESKTOP",
                        "SUCCESS",
                        900L,
                        "desktop.jar",
                        "ok"
                );

        String stages = KmpBuildPerformanceMetricsStore.formatStageSummary(
                Arrays.asList(androidStage, desktopStage),
                4
        );

        assertTrue(stages.contains("ANDROID(SUCCESS)"));
        assertTrue(stages.contains("DESKTOP(SUCCESS)"));
    }

    @Test
    public void snapshot_empty_hasZeroedDefaults() {
        KmpBuildPerformanceMetricsStore.Snapshot snapshot = KmpBuildPerformanceMetricsStore.Snapshot.empty();

        assertEquals(0, snapshot.totalRuns);
        assertEquals(0, snapshot.coldRuns);
        assertEquals(0, snapshot.incrementalRuns);
        assertEquals("-", snapshot.lastProfile);
        assertEquals(0L, snapshot.lastDurationMs);
        assertTrue(snapshot.targetDurationTrends.isEmpty());
        assertTrue(snapshot.lastTargetStages.isEmpty());
        assertEquals("No stage data yet.", KmpBuildPerformanceMetricsStore.formatStageSummary(Collections.emptyList(), 3));
    }
}
