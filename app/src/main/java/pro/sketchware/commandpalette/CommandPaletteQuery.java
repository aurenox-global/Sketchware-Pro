package pro.sketchware.commandpalette;

public final class CommandPaletteQuery {

    public static final int DEFAULT_MAX_RESULTS = 25;

    public final String text;
    public final int maxResults;

    public CommandPaletteQuery(String text, int maxResults) {
        this.text = text == null ? "" : text.trim();
        this.maxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults;
    }

    public static CommandPaletteQuery defaultQuery() {
        return new CommandPaletteQuery("", DEFAULT_MAX_RESULTS);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }
}
