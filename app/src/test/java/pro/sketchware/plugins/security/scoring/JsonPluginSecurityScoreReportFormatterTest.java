package pro.sketchware.plugins.security.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisSeverity;

public class JsonPluginSecurityScoreReportFormatterTest {

    @Test
    public void format_isDeterministicAndContainsCoreFields() {
        PluginSecurityScoreReport report = new PluginSecurityScoreReport(
                "sample.plugin",
                88,
                PluginSecurityRiskLevel.MEDIUM,
                50L,
                12345L,
                new PluginSecurityScoreBreakdown(2, 0, 1, 0, 1, 0, 8),
                List.of(new PluginStaticAnalysisFinding(
                        "plugin.manifest",
                        "manifest.signature_missing",
                        PluginStaticAnalysisSeverity.WARNING,
                        "signature",
                        "missing"
                ))
        );

        JsonPluginSecurityScoreReportFormatter formatter = new JsonPluginSecurityScoreReportFormatter();
        String first = formatter.format(report);
        String second = formatter.format(report);

        assertEquals(first, second);
        assertTrue(first.contains("\"schemaVersion\": 1"));
        assertTrue(first.contains("\"pluginId\": \"sample.plugin\""));
        assertTrue(first.contains("\"score\": 88"));
        assertTrue(first.contains("\"riskLevel\": \"MEDIUM\""));
        assertTrue(first.contains("\"manifest.signature_missing\""));
    }
}
