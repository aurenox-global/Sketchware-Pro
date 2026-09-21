package pro.sketchware.plugins.security.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginStaticAnalysisReport {

    public final long startedAtMs;
    public final long finishedAtMs;
    public final List<PluginStaticAnalyzerResult> analyzerResults;
    public final List<PluginStaticAnalysisFinding> findings;

    public PluginStaticAnalysisReport(long startedAtMs,
                                      long finishedAtMs,
                                      List<PluginStaticAnalyzerResult> analyzerResults,
                                      List<PluginStaticAnalysisFinding> findings) {
        this.startedAtMs = Math.max(startedAtMs, 0L);
        this.finishedAtMs = Math.max(finishedAtMs, this.startedAtMs);
        this.analyzerResults = Collections.unmodifiableList(new ArrayList<>(
                analyzerResults == null ? Collections.emptyList() : analyzerResults
        ));
        this.findings = Collections.unmodifiableList(new ArrayList<>(
                findings == null ? Collections.emptyList() : findings
        ));
    }

    public long durationMs() {
        return finishedAtMs - startedAtMs;
    }

    public boolean hasErrors() {
        for (PluginStaticAnalysisFinding finding : findings) {
            if (finding != null && finding.severity.isError()) {
                return true;
            }
        }
        return false;
    }
}
