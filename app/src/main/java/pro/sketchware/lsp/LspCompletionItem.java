package pro.sketchware.lsp;

public final class LspCompletionItem {
    public final String label;
    public final String insertText;
    public final String detail;
    public final String source;

    public LspCompletionItem(String label, String insertText, String detail, String source) {
        this.label = label == null ? "" : label;
        this.insertText = insertText == null ? this.label : insertText;
        this.detail = detail == null ? "" : detail;
        this.source = source == null ? "" : source;
    }
}
