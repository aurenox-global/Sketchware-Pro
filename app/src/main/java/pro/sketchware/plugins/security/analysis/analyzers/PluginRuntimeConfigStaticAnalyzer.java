package pro.sketchware.plugins.security.analysis.analyzers;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.plugins.runtime.PluginClassLoaderConfig;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisRequest;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisSeverity;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalyzer;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalyzerResult;

public final class PluginRuntimeConfigStaticAnalyzer implements PluginStaticAnalyzer {

    private static final String ANALYZER_ID = "plugin.runtime";

    @Override
    public String id() {
        return ANALYZER_ID;
    }

    @Override
    public PluginStaticAnalyzerResult analyze(PluginStaticAnalysisRequest request) {
        long startedAt = System.currentTimeMillis();
        PluginStaticAnalysisRequest safeRequest = request == null
                ? new PluginStaticAnalysisRequest("", "", null, null, null)
                : request;

        PluginClassLoaderConfig config = safeRequest.classLoaderConfig;
        List<PluginStaticAnalysisFinding> findings = new ArrayList<>();

        if (config.pluginId.isEmpty()) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "runtime.plugin_id.empty",
                    PluginStaticAnalysisSeverity.ERROR,
                    "classLoaderConfig.pluginId",
                    "Plugin id must not be empty for runtime isolation"
            ));
        }

        if (!config.hasEntryClass()) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "runtime.entry_class.missing",
                    PluginStaticAnalysisSeverity.ERROR,
                    "classLoaderConfig.entryClassName",
                    "Entry class name is required for runtime loading"
            ));
        }

        if (config.classpath.isEmpty()) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "runtime.classpath.empty",
                    PluginStaticAnalysisSeverity.WARNING,
                    "classLoaderConfig.classpath",
                    "Classpath is empty; runtime loader will use fallback"
            ));
        }

        if (!config.optimizedDirectory.isEmpty() && config.optimizedDirectory.equals(config.classpath)) {
            findings.add(new PluginStaticAnalysisFinding(
                    ANALYZER_ID,
                    "runtime.optimized_dir.suspicious",
                    PluginStaticAnalysisSeverity.WARNING,
                    "classLoaderConfig.optimizedDirectory",
                    "Optimized directory should be distinct from classpath artifact"
            ));
        }

        return PluginStaticAnalyzerResult.success(
                ANALYZER_ID,
                System.currentTimeMillis() - startedAt,
                findings
        );
    }
}
