package pro.sketchware.kmp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpMigrationAnalysisReportSerializerTest {

    @Test
    public void serializer_outputsStableLogAndJson() {
        KmpMigrationAnalysisReport report = KmpMigrationAnalyzer.analyze(
                "legacy-report",
                Arrays.asList(
                        "com.google.code.gson:gson:2.11.0",
                        "io.reactivex.rxjava3:rxjava:3.1.9"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.IOS_ARM64)
        );

        String logLine = KmpMigrationAnalysisReportSerializer.toLogLine(report);
        String json = KmpMigrationAnalysisReportSerializer.toJson(report);

        assertTrue(logLine.contains("projectId=legacy-report"));
        assertTrue(logLine.contains("risk="));
        assertTrue(json.contains("\"projectId\": \"legacy-report\""));
        assertTrue(json.contains("\"estimatedManualWorkHours\""));
        assertTrue(json.contains("\"compatibilityResult\""));
    }
}