package pro.sketchware.blocks.generator.components.parsers;

public final class SourceToBlockParseIssue {

    public final String code;
    public final String message;
    public final int line;
    public final int column;

    public SourceToBlockParseIssue(String code, String message, int line, int column) {
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
        this.line = Math.max(line, -1);
        this.column = Math.max(column, -1);
    }
}
