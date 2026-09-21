package pro.sketchware.plugins.security.analysis;

public interface PluginStaticAnalyzer {

    String id();

    PluginStaticAnalyzerResult analyze(PluginStaticAnalysisRequest request);
}
