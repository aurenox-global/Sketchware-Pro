package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpMigrationAnalyzerTest {

    @Test
    public void analyze_sampleLegacyDependencies_generatesExpectedSummary() {
        KmpMigrationAnalysisReport report = KmpMigrationAnalyzer.analyze(
                "legacy-sample",
                Arrays.asList(
                        "com.squareup.retrofit2:retrofit:2.11.0",
                        "androidx.room:room-runtime:2.6.1",
                        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1",
                        "com.example:legacy-lib:0.1.0"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.IOS_ARM64)
        );

        assertNotNull(report);
        assertEquals("legacy-sample", report.projectId);
        assertEquals(4, report.summary.totalDependencies);
        assertEquals(1, report.summary.compatibleCount);
        assertEquals(2, report.summary.partiallyCompatibleCount);
        assertEquals(0, report.summary.incompatibleCount);
        assertEquals(1, report.summary.unknownCount);
        assertEquals(0.5d, report.summary.migrableRatio, 0.0001d);
        assertEquals(7, report.summary.estimatedManualWorkHours);
        assertEquals(KmpMigrationRiskLevel.MEDIUM, report.summary.riskLevel);
        assertTrue(report.summary.riskSummary.contains("partial=2"));
        assertFalse(report.topRisks.isEmpty());
    }
}