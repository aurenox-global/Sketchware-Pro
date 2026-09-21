package pro.sketchware.plugins.security.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import pro.sketchware.plugins.runtime.PluginClassLoaderConfig;
import pro.sketchware.plugins.security.analysis.analyzers.PluginRuntimeConfigStaticAnalyzer;

public class PluginStaticAnalysisPipelineOrchestratorTest {

    @Test
    public void defaultPipeline_invalidInput_reportsErrors() {
        PluginStaticAnalysisPipelineOrchestrator orchestrator =
                PluginStaticAnalysisPipelineOrchestrator.createDefault();

        PluginStaticAnalysisRequest request = new PluginStaticAnalysisRequest(
                "",
                "{}",
                new byte[0],
                new PluginClassLoaderConfig("", "", "", "", ""),
                null
        );

        PluginStaticAnalysisReport report = orchestrator.analyze(request);
        assertTrue(report.hasErrors());
        assertEquals(3, report.analyzerResults.size());
    }

    @Test
    public void orchestrator_handlesAnalyzerFailureAndContinues() {
        PluginStaticAnalysisPipelineOrchestrator orchestrator =
                new PluginStaticAnalysisPipelineOrchestrator();
        orchestrator.registerAnalyzer(new PluginStaticAnalyzer() {
            @Override
            public String id() {
                return "a.fail";
            }

            @Override
            public PluginStaticAnalyzerResult analyze(PluginStaticAnalysisRequest request) {
                throw new IllegalStateException("forced failure");
            }
        });
        orchestrator.registerAnalyzer(new PluginRuntimeConfigStaticAnalyzer());

        PluginStaticAnalysisRequest request = new PluginStaticAnalysisRequest(
                "plugin.ok",
                "",
                null,
                new PluginClassLoaderConfig("plugin.ok", "java.lang.String", "/tmp/plugin.jar", "/tmp/opt", ""),
                null
        );

        PluginStaticAnalysisReport report = orchestrator.analyze(request);
        assertEquals(2, report.analyzerResults.size());

        PluginStaticAnalyzerResult first = report.analyzerResults.get(0);
        assertEquals("a.fail", first.analyzerId);
        assertFalse(first.successful);

        boolean hasFailureFinding = false;
        for (PluginStaticAnalysisFinding finding : report.findings) {
            if ("analyzer.execution_failed".equals(finding.code)) {
                hasFailureFinding = true;
                break;
            }
        }
        assertTrue(hasFailureFinding);
    }

    @Test
    public void defaultPipeline_validInput_hasNoErrors() {
        PluginStaticAnalysisPipelineOrchestrator orchestrator =
                PluginStaticAnalysisPipelineOrchestrator.createDefault();

        String manifestJson = """
                {
                  "schemaVersion": 1,
                  "pluginId": "sample.plugin.core",
                  "name": "Sample Plugin",
                  "version": "1.0.0",
                  "entryClass": "sample.plugin.core.MainPlugin",
                  "permissions": ["project.read"],
                  "signature": {
                    "algorithm": "SHA256withRSA",
                    "value": "deadbeef"
                  }
                }
                """;

        PluginStaticAnalysisRequest request = new PluginStaticAnalysisRequest(
                "sample.plugin.core",
                manifestJson,
                new byte[] {1, 2, 3},
                new PluginClassLoaderConfig(
                        "sample.plugin.core",
                        "sample.plugin.core.MainPlugin",
                        "/tmp/sample-plugin.jar",
                        "/tmp/sample-opt",
                        ""
                ),
                null
        );

        PluginStaticAnalysisReport report = orchestrator.analyze(request);
        assertFalse(report.hasErrors());
        assertEquals(3, report.analyzerResults.size());

        List<String> analyzerIds = orchestrator.analyzerIds();
        assertEquals("plugin.dependencies", analyzerIds.get(0));
        assertEquals("plugin.manifest", analyzerIds.get(1));
        assertEquals("plugin.runtime", analyzerIds.get(2));
    }
}
