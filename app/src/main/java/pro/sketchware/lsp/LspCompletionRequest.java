package pro.sketchware.lsp;

public final class LspCompletionRequest {
    public final String documentText;
    public final String prefix;
    public final int line;
    public final int character;

    public LspCompletionRequest(String documentText, String prefix, int line, int character) {
        this.documentText = documentText == null ? "" : documentText;
        this.prefix = prefix == null ? "" : prefix;
        this.line = Math.max(0, line);
        this.character = Math.max(0, character);
    }

    public LspCompletionRequest ensureDocumentText(String fallbackText) {
        if (!documentText.isEmpty()) {
            return this;
        }
        return new LspCompletionRequest(fallbackText, prefix, line, character);
    }
}
