package pro.sketchware.plugins.manifest;

public final class PluginManifestIssue {

    public final PluginManifestIssueCode code;
    public final PluginManifestIssueSeverity severity;
    public final String fieldPath;
    public final String message;

    public PluginManifestIssue(PluginManifestIssueCode code,
                               PluginManifestIssueSeverity severity,
                               String fieldPath,
                               String message) {
        this.code = code == null ? PluginManifestIssueCode.INVALID_FIELD_VALUE : code;
        this.severity = severity == null ? PluginManifestIssueSeverity.ERROR : severity;
        this.fieldPath = fieldPath == null ? "" : fieldPath;
        this.message = message == null ? "" : message;
    }
}
