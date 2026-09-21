package pro.sketchware.lsp;

public final class LspNavigationRequest {
    public final String documentText;
    public final String symbol;
    public final int line;
    public final int character;

    public LspNavigationRequest(String documentText, String symbol, int line, int character) {
        this.documentText = documentText == null ? "" : documentText;
        this.symbol = symbol == null ? "" : symbol;
        this.line = Math.max(0, line);
        this.character = Math.max(0, character);
    }

    public LspNavigationRequest ensureDocumentText(String fallbackText) {
        if (!documentText.isEmpty()) {
            return this;
        }
        return new LspNavigationRequest(fallbackText, symbol, line, character);
    }
}
