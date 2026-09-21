package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class KmpBuildFailureTaxonomyTest {

    @Test
    public void topFailureCodes_containsAtLeastTenActionableCodes() {
        List<String> codes = KmpBuildFailureTaxonomy.topFailureCodes();

        assertNotNull(codes);
        assertTrue(codes.size() >= 10);
        assertFalse(codes.contains("KMP_ERR_UNKNOWN"));
    }

    @Test
    public void diagnose_classifiesKnownFailuresWithRemediation() {
        KmpBuildOrchestrationReport report = new KmpBuildOrchestrationReport(
                10L,
                110L,
                Arrays.asList(
                        new KmpTargetBuildResult(
                                KmpTarget.ANDROID,
                                KmpTargetBuildStatus.FAILED,
                                50L,
                                null,
                                "Timed out while executing :androidApp:assembleDebug"
                        ),
                        new KmpTargetBuildResult(
                                KmpTarget.DESKTOP,
                                KmpTargetBuildStatus.FAILED,
                                60L,
                                null,
                                "e: unresolved reference: logInfo"
                        )
                )
        );

        KmpBuildDiagnosticsReport diagnosticsReport = KmpBuildFailureTaxonomy.diagnose(report);

        assertEquals(2, diagnosticsReport.totalFailures);
        assertEquals(2, diagnosticsReport.matchedFailures);
        assertEquals("KMP_ERR_BUILD_TIMEOUT", diagnosticsReport.diagnostics.get(0).code);
        assertEquals("KMP_ERR_KOTLIN_UNRESOLVED", diagnosticsReport.diagnostics.get(1).code);
        assertFalse(diagnosticsReport.diagnostics.get(0).remediation.isEmpty());
        assertFalse(diagnosticsReport.diagnostics.get(1).remediation.isEmpty());
    }

    @Test
    public void diagnose_fallbacksToUnknownForUnmatchedFailure() {
        KmpBuildOrchestrationReport report = new KmpBuildOrchestrationReport(
                1L,
                2L,
                Arrays.asList(
                        new KmpTargetBuildResult(
                                KmpTarget.ANDROID,
                                KmpTargetBuildStatus.FAILED,
                                1L,
                                null,
                                "Unexpected fatal build problem"
                        )
                )
        );

        KmpBuildDiagnosticsReport diagnosticsReport = KmpBuildFailureTaxonomy.diagnose(report);

        assertEquals(1, diagnosticsReport.totalFailures);
        assertEquals(0, diagnosticsReport.matchedFailures);
        assertEquals("KMP_ERR_UNKNOWN", diagnosticsReport.diagnostics.get(0).code);
        assertFalse(diagnosticsReport.diagnostics.get(0).remediation.isEmpty());
    }
}
