package pro.sketchware.plugins.security.analysis;

public final class PluginStaticAnalysisFinding {

    public final String analyzerId;
    public final String code;
    public final PluginStaticAnalysisSeverity severity;
    public final String location;
    public final String message;

    public PluginStaticAnalysisFinding(String analyzerId,
                                       String code,
                                       PluginStaticAnalysisSeverity severity,
                                       String location,
                                       String message) {
        this.analyzerId = analyzerId == null ? "" : analyzerId;
        this.code = code == null ? "" : code;
        this.severity = severity == null ? PluginStaticAnalysisSeverity.WARNING : severity;
        this.location = location == null ? "" : location;
        this.message = message == null ? "" : message;
    }
}
