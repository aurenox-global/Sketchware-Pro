package pro.sketchware.lsp;

public final class LspDiagnostic {
    public final LspSeverity severity;
    public final String message;
    public final LspRange range;
    public final String source;

    public LspDiagnostic(LspSeverity severity, String message, LspRange range, String source) {
        this.severity = severity == null ? LspSeverity.INFO : severity;
        this.message = message == null ? "" : message;
        this.range = range == null ? new LspRange(0, 0, 0, 0) : range;
        this.source = source == null ? "" : source;
    }
}
