package pro.sketchware.plugins.security.scoring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.plugins.security.analysis.PluginStaticAnalysisFinding;

public final class JsonPluginSecurityScoreReportFormatter implements PluginSecurityScoreReportFormatter {

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    @Override
    public String format(PluginSecurityScoreReport report) {
        PluginSecurityScoreReport safeReport = report == null
                ? new PluginSecurityScoreReport("", 0, PluginSecurityRiskLevel.CRITICAL, 0L, 0L, null, null)
                : report;

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("pluginId", safeReport.pluginId);
        root.put("score", safeReport.score);
        root.put("riskLevel", safeReport.riskLevel.name());
        root.put("analyzedDurationMs", safeReport.analyzedDurationMs);
        root.put("generatedAtMs", safeReport.generatedAtMs);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("analyzerCount", safeReport.breakdown.analyzerCount);
        breakdown.put("analyzerFailureCount", safeReport.breakdown.analyzerFailureCount);
        breakdown.put("findingCount", safeReport.breakdown.findingCount);
        breakdown.put("errorCount", safeReport.breakdown.errorCount);
        breakdown.put("warningCount", safeReport.breakdown.warningCount);
        breakdown.put("infoCount", safeReport.breakdown.infoCount);
        breakdown.put("penaltyPoints", safeReport.breakdown.penaltyPoints);
        root.put("breakdown", breakdown);

        root.put("findings", toFindingsArray(safeReport.findings));
        return gson.toJson(root);
    }

    private static List<Map<String, Object>> toFindingsArray(List<PluginStaticAnalysisFinding> findings) {
        return findings.stream().map(finding -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("analyzerId", finding.analyzerId);
            item.put("code", finding.code);
            item.put("severity", finding.severity.name());
            item.put("location", finding.location);
            item.put("message", finding.message);
            return item;
        }).toList();
    }
}
