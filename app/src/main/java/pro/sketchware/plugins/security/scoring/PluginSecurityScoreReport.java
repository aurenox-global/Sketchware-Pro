package pro.sketchware.plugins.security.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;

public final class PluginSecurityScoreReport {

    public final String pluginId;
    public final int score;
    public final PluginSecurityRiskLevel riskLevel;
    public final long analyzedDurationMs;
    public final long generatedAtMs;
    public final PluginSecurityScoreBreakdown breakdown;
    public final List<PluginStaticAnalysisFinding> findings;

    public PluginSecurityScoreReport(String pluginId,
                                     int score,
                                     PluginSecurityRiskLevel riskLevel,
                                     long analyzedDurationMs,
                                     long generatedAtMs,
                                     PluginSecurityScoreBreakdown breakdown,
                                     List<PluginStaticAnalysisFinding> findings) {
        this.pluginId = pluginId == null ? "" : pluginId;
        this.score = Math.max(0, Math.min(score, 100));
        this.riskLevel = riskLevel == null ? PluginSecurityRiskLevel.CRITICAL : riskLevel;
        this.analyzedDurationMs = Math.max(analyzedDurationMs, 0L);
        this.generatedAtMs = Math.max(generatedAtMs, 0L);
        this.breakdown = breakdown == null
                ? new PluginSecurityScoreBreakdown(0, 0, 0, 0, 0, 0, 0)
                : breakdown;
        this.findings = Collections.unmodifiableList(new ArrayList<>(
                findings == null ? Collections.emptyList() : findings
        ));
    }

    public boolean hasBlockingRisk() {
        return riskLevel == PluginSecurityRiskLevel.HIGH
                || riskLevel == PluginSecurityRiskLevel.CRITICAL
                || breakdown.errorCount > 0;
    }
}
