package pro.sketchware.kmp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpBuildOrchestrationReportSerializerTest {

    @Test
    public void toJson_containsPerTargetFields() {
        KmpBuildOrchestrationReport report = new KmpBuildOrchestrationReport(
                1L,
                101L,
                Arrays.asList(
                        new KmpTargetBuildResult(
                                KmpTarget.ANDROID,
                                KmpTargetBuildStatus.SUCCESS,
                                40L,
                                "/tmp/shared-debug.aar",
                                "ok"
                        ),
                        new KmpTargetBuildResult(
                                KmpTarget.DESKTOP,
                                KmpTargetBuildStatus.FAILED,
                                50L,
                                "/tmp/shared-jvm.jar",
                                "failed"
                        )
                )
        );

        String json = KmpBuildOrchestrationReportSerializer.toJson(report);

        assertTrue(json.contains("\"target\": \"ANDROID\""));
        assertTrue(json.contains("\"status\": \"SUCCESS\""));
        assertTrue(json.contains("\"artifactPath\": \"/tmp/shared-debug.aar\""));
    }

    @Test
    public void toLogLine_containsSummaryCounters() {
        KmpBuildOrchestrationReport report = new KmpBuildOrchestrationReport(
                10L,
                110L,
                Arrays.asList(
                        new KmpTargetBuildResult(KmpTarget.ANDROID, KmpTargetBuildStatus.SUCCESS, 40L, null, "ok"),
                        new KmpTargetBuildResult(KmpTarget.DESKTOP, KmpTargetBuildStatus.FAILED, 60L, null, "failed"),
                        new KmpTargetBuildResult(KmpTarget.WASM_JS, KmpTargetBuildStatus.NOT_SUPPORTED, 0L, null, "stub")
                )
        );

        String logLine = KmpBuildOrchestrationReportSerializer.toLogLine(report);

        assertTrue(logLine.contains("success=1"));
        assertTrue(logLine.contains("failed=1"));
        assertTrue(logLine.contains("notSupported=1"));
        assertTrue(logLine.contains("durationMs=100"));
    }
}
