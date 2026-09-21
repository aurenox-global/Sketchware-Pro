package pro.sketchware.plugins.security.scoring;

public final class PluginSecurityScoreWeights {

    public static final PluginSecurityScoreWeights DEFAULT =
            new PluginSecurityScoreWeights(25, 8, 2, 5);

    public final int errorPenalty;
    public final int warningPenalty;
    public final int infoPenalty;
    public final int analyzerFailurePenalty;

    public PluginSecurityScoreWeights(int errorPenalty,
                                      int warningPenalty,
                                      int infoPenalty,
                                      int analyzerFailurePenalty) {
        this.errorPenalty = Math.max(errorPenalty, 0);
        this.warningPenalty = Math.max(warningPenalty, 0);
        this.infoPenalty = Math.max(infoPenalty, 0);
        this.analyzerFailurePenalty = Math.max(analyzerFailurePenalty, 0);
    }
}
