package pro.sketchware.plugins.security.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisReport;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisSeverity;
import pro.sketchware.plugins.security.analysis.PluginStaticAnalyzerResult;

public final class PluginSecurityScoreCalculator {

    private final PluginSecurityScoreWeights weights;

    public PluginSecurityScoreCalculator() {
        this(PluginSecurityScoreWeights.DEFAULT);
    }

    public PluginSecurityScoreCalculator(PluginSecurityScoreWeights weights) {
        this.weights = weights == null ? PluginSecurityScoreWeights.DEFAULT : weights;
    }

    public PluginSecurityScoreReport calculate(String pluginId, PluginStaticAnalysisReport report) {
        PluginStaticAnalysisReport safeReport = report == null
                ? new PluginStaticAnalysisReport(0L, 0L, null, null)
                : report;

        int analyzerFailureCount = 0;
        for (PluginStaticAnalyzerResult analyzerResult : safeReport.analyzerResults) {
            if (analyzerResult != null && !analyzerResult.successful) {
                analyzerFailureCount++;
            }
        }

        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;
        for (PluginStaticAnalysisFinding finding : safeReport.findings) {
            if (finding == null) {
                continue;
            }
            if (finding.severity == PluginStaticAnalysisSeverity.ERROR) {
                errorCount++;
            } else if (finding.severity == PluginStaticAnalysisSeverity.WARNING) {
                warningCount++;
            } else {
                infoCount++;
            }
        }

        int penalty = errorCount * weights.errorPenalty
                + warningCount * weights.warningPenalty
                + infoCount * weights.infoPenalty
                + analyzerFailureCount * weights.analyzerFailurePenalty;

        int score = Math.max(0, 100 - penalty);
        PluginSecurityRiskLevel riskLevel = toRiskLevel(score);

        PluginSecurityScoreBreakdown breakdown = new PluginSecurityScoreBreakdown(
                safeReport.analyzerResults.size(),
                analyzerFailureCount,
                safeReport.findings.size(),
                errorCount,
                warningCount,
                infoCount,
                penalty
        );

        List<PluginStaticAnalysisFinding> sortedFindings = new ArrayList<>(safeReport.findings);
        sortedFindings.sort(Comparator
                .comparingInt((PluginStaticAnalysisFinding finding) -> severityRank(finding.severity))
                .thenComparing(finding -> finding.analyzerId == null ? "" : finding.analyzerId)
                .thenComparing(finding -> finding.code == null ? "" : finding.code));

        return new PluginSecurityScoreReport(
                pluginId,
                score,
                riskLevel,
                safeReport.durationMs(),
                System.currentTimeMillis(),
                breakdown,
                sortedFindings
        );
    }

    private static PluginSecurityRiskLevel toRiskLevel(int score) {
        if (score >= 90) {
            return PluginSecurityRiskLevel.LOW;
        }
        if (score >= 70) {
            return PluginSecurityRiskLevel.MEDIUM;
        }
        if (score >= 40) {
            return PluginSecurityRiskLevel.HIGH;
        }
        return PluginSecurityRiskLevel.CRITICAL;
    }

    private static int severityRank(PluginStaticAnalysisSeverity severity) {
        if (severity == PluginStaticAnalysisSeverity.ERROR) {
            return 0;
        }
        if (severity == PluginStaticAnalysisSeverity.WARNING) {
            return 1;
        }
        return 2;
    }
}
