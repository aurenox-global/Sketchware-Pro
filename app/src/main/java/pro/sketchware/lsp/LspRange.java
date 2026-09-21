package pro.sketchware.lsp;

public final class LspRange {
    public final int startLine;
    public final int startCharacter;
    public final int endLine;
    public final int endCharacter;

    public LspRange(int startLine, int startCharacter, int endLine, int endCharacter) {
        this.startLine = Math.max(0, startLine);
        this.startCharacter = Math.max(0, startCharacter);
        this.endLine = Math.max(this.startLine, endLine);
        this.endCharacter = Math.max(0, endCharacter);
    }
}
