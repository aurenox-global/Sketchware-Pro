package pro.sketchware.plugins.security.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisReport;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisSeverity;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalyzerResult;

public class PluginSecurityScoreCalculatorTest {

    @Test
    public void calculate_cleanReport_returnsLowRiskAndHighScore() {
        PluginStaticAnalysisReport report = new PluginStaticAnalysisReport(
                100L,
                160L,
                List.of(PluginStaticAnalyzerResult.success("plugin.manifest", 10L, List.of())),
                List.of()
        );

        PluginSecurityScoreReport scoreReport = new PluginSecurityScoreCalculator().calculate("sample.plugin", report);

        assertEquals(100, scoreReport.score);
        assertEquals(PluginSecurityRiskLevel.LOW, scoreReport.riskLevel);
        assertFalse(scoreReport.hasBlockingRisk());
        assertEquals(0, scoreReport.breakdown.penaltyPoints);
    }

    @Test
    public void calculate_mixedFindings_returnsExpectedBreakdownAndRisk() {
        PluginStaticAnalysisReport report = new PluginStaticAnalysisReport(
                100L,
                260L,
                List.of(
                        PluginStaticAnalyzerResult.failure("plugin.runtime", 5L, "failed", List.of()),
                        PluginStaticAnalyzerResult.success("plugin.manifest", 7L, List.of())
                ),
                List.of(
                        new PluginStaticAnalysisFinding("plugin.runtime", "runtime.entry_class.missing", PluginStaticAnalysisSeverity.ERROR, "entry", "missing"),
                        new PluginStaticAnalysisFinding("plugin.manifest", "manifest.unknown_permission", PluginStaticAnalysisSeverity.WARNING, "permissions[0]", "unknown"),
                        new PluginStaticAnalysisFinding("plugin.manifest", "signature.skipped", PluginStaticAnalysisSeverity.INFO, "signature", "skipped")
                )
        );

        PluginSecurityScoreReport scoreReport = new PluginSecurityScoreCalculator().calculate("sample.plugin", report);

        assertEquals(60, scoreReport.score);
        assertEquals(PluginSecurityRiskLevel.HIGH, scoreReport.riskLevel);
        assertTrue(scoreReport.hasBlockingRisk());

        assertEquals(2, scoreReport.breakdown.analyzerCount);
        assertEquals(1, scoreReport.breakdown.analyzerFailureCount);
        assertEquals(1, scoreReport.breakdown.errorCount);
        assertEquals(1, scoreReport.breakdown.warningCount);
        assertEquals(1, scoreReport.breakdown.infoCount);
        assertEquals(40, scoreReport.breakdown.penaltyPoints);
    }
}
