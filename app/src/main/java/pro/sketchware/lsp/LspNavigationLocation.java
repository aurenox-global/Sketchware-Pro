package pro.sketchware.lsp;

public final class LspNavigationLocation {
    public final String documentPath;
    public final LspRange range;
    public final String preview;

    public LspNavigationLocation(String documentPath, LspRange range, String preview) {
        this.documentPath = documentPath == null ? "" : documentPath;
        this.range = range == null ? new LspRange(0, 0, 0, 0) : range;
        this.preview = preview == null ? "" : preview;
    }
}
