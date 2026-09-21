package pro.sketchware.plugins.security.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginStaticAnalyzerResult {

    public final String analyzerId;
    public final long durationMs;
    public final boolean successful;
    public final String errorMessage;
    public final List<PluginStaticAnalysisFinding> findings;

    private PluginStaticAnalyzerResult(String analyzerId,
                                       long durationMs,
                                       boolean successful,
                                       String errorMessage,
                                       List<PluginStaticAnalysisFinding> findings) {
        this.analyzerId = analyzerId == null ? "" : analyzerId;
        this.durationMs = Math.max(durationMs, 0L);
        this.successful = successful;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.findings = Collections.unmodifiableList(new ArrayList<>(
                findings == null ? Collections.emptyList() : findings
        ));
    }

    public static PluginStaticAnalyzerResult success(String analyzerId,
                                                     long durationMs,
                                                     List<PluginStaticAnalysisFinding> findings) {
        return new PluginStaticAnalyzerResult(analyzerId, durationMs, true, "", findings);
    }

    public static PluginStaticAnalyzerResult failure(String analyzerId,
                                                     long durationMs,
                                                     String errorMessage,
                                                     List<PluginStaticAnalysisFinding> findings) {
        return new PluginStaticAnalyzerResult(analyzerId, durationMs, false, errorMessage, findings);
    }
}
