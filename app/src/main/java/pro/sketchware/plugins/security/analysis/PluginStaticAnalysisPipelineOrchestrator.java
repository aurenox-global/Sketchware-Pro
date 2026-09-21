package pro.sketchware.plugins.security.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import pro.sketchware.plugins.security.analysis.analyzers.PluginDependencyVulnerabilityStaticAnalyzer;
import pro.sketchware.plugins.security.analysis.analyzers.PluginManifestStaticAnalyzer;
import pro.sketchware.plugins.security.analysis.analyzers.PluginRuntimeConfigStaticAnalyzer;

public final class PluginStaticAnalysisPipelineOrchestrator {

    private final CopyOnWriteArrayList<PluginStaticAnalyzer> analyzers = new CopyOnWriteArrayList<>();

    public PluginStaticAnalysisPipelineOrchestrator() {
    }

    public static PluginStaticAnalysisPipelineOrchestrator createDefault() {
        PluginStaticAnalysisPipelineOrchestrator orchestrator = new PluginStaticAnalysisPipelineOrchestrator();
        orchestrator.registerAnalyzer(new PluginDependencyVulnerabilityStaticAnalyzer());
        orchestrator.registerAnalyzer(new PluginManifestStaticAnalyzer());
        orchestrator.registerAnalyzer(new PluginRuntimeConfigStaticAnalyzer());
        return orchestrator;
    }

    public void registerAnalyzer(PluginStaticAnalyzer analyzer) {
        if (analyzer == null || analyzer.id() == null || analyzer.id().trim().isEmpty()) {
            return;
        }

        String analyzerId = analyzer.id().trim();
        analyzers.removeIf(existing -> analyzerId.equals(existing.id()));
        analyzers.add(analyzer);
        analyzers.sort(Comparator.comparing(PluginStaticAnalyzer::id));
    }

    public boolean removeAnalyzer(String analyzerId) {
        if (analyzerId == null || analyzerId.trim().isEmpty()) {
            return false;
        }
        return analyzers.removeIf(existing -> analyzerId.trim().equals(existing.id()));
    }

    public void clearAnalyzers() {
        analyzers.clear();
    }

    public List<String> analyzerIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (PluginStaticAnalyzer analyzer : analyzers) {
            ids.add(analyzer.id());
        }
        return Collections.unmodifiableList(ids);
    }

    public PluginStaticAnalysisReport analyze(PluginStaticAnalysisRequest request) {
        long startedAt = System.currentTimeMillis();
        PluginStaticAnalysisRequest safeRequest = request == null
                ? new PluginStaticAnalysisRequest("", "", null, null, null)
                : request;

        ArrayList<PluginStaticAnalyzerResult> results = new ArrayList<>();
        ArrayList<PluginStaticAnalysisFinding> findings = new ArrayList<>();

        for (PluginStaticAnalyzer analyzer : analyzers) {
            long analyzerStart = System.currentTimeMillis();
            try {
                PluginStaticAnalyzerResult result = analyzer.analyze(safeRequest);
                PluginStaticAnalyzerResult safeResult = result == null
                        ? PluginStaticAnalyzerResult.success(analyzer.id(), System.currentTimeMillis() - analyzerStart, Collections.emptyList())
                        : result;
                results.add(safeResult);
                findings.addAll(safeResult.findings);
            } catch (Throwable throwable) {
                long duration = System.currentTimeMillis() - analyzerStart;
                PluginStaticAnalysisFinding failureFinding = new PluginStaticAnalysisFinding(
                        analyzer.id(),
                        "analyzer.execution_failed",
                        PluginStaticAnalysisSeverity.ERROR,
                        analyzer.id(),
                        throwable.getMessage() == null ? "Analyzer execution failed" : throwable.getMessage()
                );
                results.add(PluginStaticAnalyzerResult.failure(
                        analyzer.id(),
                        duration,
                        failureFinding.message,
                        Collections.singletonList(failureFinding)
                ));
                findings.add(failureFinding);
            }
        }

        return new PluginStaticAnalysisReport(
                startedAt,
                System.currentTimeMillis(),
                results,
                findings
        );
    }
}
