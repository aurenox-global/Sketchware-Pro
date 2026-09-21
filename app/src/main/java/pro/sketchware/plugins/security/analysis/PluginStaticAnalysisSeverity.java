package pro.sketchware.plugins.security.analysis;

public enum PluginStaticAnalysisSeverity {

    INFO,
    WARNING,
    ERROR;

    public boolean isError() {
        return this == ERROR;
    }
}
