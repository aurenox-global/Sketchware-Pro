package pro.sketchware.kmp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpBuildDiagnosticsReportSerializerTest {

    @Test
    public void toJson_andLogLine_includeDiagnosticsFields() {
        KmpBuildDiagnosticsReport report = new KmpBuildDiagnosticsReport(
                100L,
                2,
                2,
                Arrays.asList(
                        new KmpBuildFailureDiagnostic(
                                KmpTarget.ANDROID,
                                KmpTargetBuildStatus.FAILED,
                                "KMP_ERR_BUILD_TIMEOUT",
                                KmpBuildFailureCategory.TIMEOUT,
                                "Timed out",
                                "Timed out while executing task",
                                "Increase timeout",
                                null
                        ),
                        new KmpBuildFailureDiagnostic(
                                KmpTarget.DESKTOP,
                                KmpTargetBuildStatus.FAILED,
                                "KMP_ERR_KOTLIN_UNRESOLVED",
                                KmpBuildFailureCategory.COMPILATION,
                                "Unresolved reference",
                                "unresolved reference: foo",
                                "Fix imports",
                                null
                        )
                )
        );

        String json = KmpBuildDiagnosticsReportSerializer.toJson(report);
        String logLine = KmpBuildDiagnosticsReportSerializer.toLogLine(report);

        assertTrue(json.contains("\"code\": \"KMP_ERR_BUILD_TIMEOUT\""));
        assertTrue(json.contains("\"category\": \"TIMEOUT\""));
        assertTrue(logLine.contains("failures=2"));
        assertTrue(logLine.contains("compilation=1"));
        assertTrue(logLine.contains("timeout=1"));
    }

    @Test
    public void formatForUser_rendersRemediationHints() {
        KmpBuildDiagnosticsReport report = new KmpBuildDiagnosticsReport(
                1L,
                1,
                1,
                Arrays.asList(
                        new KmpBuildFailureDiagnostic(
                                KmpTarget.ANDROID,
                                KmpTargetBuildStatus.FAILED,
                                "KMP_ERR_ANDROID_LICENSE",
                                KmpBuildFailureCategory.ENVIRONMENT,
                                "SDK license missing",
                                "License for package not accepted",
                                "Run sdkmanager --licenses",
                                null
                        )
                )
        );

        String userMessage = KmpBuildDiagnosticsReportSerializer.formatForUser(report, 3);

        assertTrue(userMessage.contains("KMP diagnostics with remediation"));
        assertTrue(userMessage.contains("KMP_ERR_ANDROID_LICENSE"));
        assertTrue(userMessage.contains("Run sdkmanager --licenses"));
    }
}
