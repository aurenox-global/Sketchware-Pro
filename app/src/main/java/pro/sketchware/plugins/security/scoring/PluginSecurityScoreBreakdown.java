package pro.sketchware.plugins.security.scoring;

public final class PluginSecurityScoreBreakdown {

    public final int analyzerCount;
    public final int analyzerFailureCount;
    public final int findingCount;
    public final int errorCount;
    public final int warningCount;
    public final int infoCount;
    public final int penaltyPoints;

    public PluginSecurityScoreBreakdown(int analyzerCount,
                                        int analyzerFailureCount,
                                        int findingCount,
                                        int errorCount,
                                        int warningCount,
                                        int infoCount,
                                        int penaltyPoints) {
        this.analyzerCount = Math.max(analyzerCount, 0);
        this.analyzerFailureCount = Math.max(analyzerFailureCount, 0);
        this.findingCount = Math.max(findingCount, 0);
        this.errorCount = Math.max(errorCount, 0);
        this.warningCount = Math.max(warningCount, 0);
        this.infoCount = Math.max(infoCount, 0);
        this.penaltyPoints = Math.max(penaltyPoints, 0);
    }
}
