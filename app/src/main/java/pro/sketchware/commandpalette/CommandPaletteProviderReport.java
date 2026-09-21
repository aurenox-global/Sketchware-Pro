package pro.sketchware.commandpalette;

public final class CommandPaletteProviderReport {

    public final String providerId;
    public final long durationMs;
    public final int actionCount;
    public final boolean success;
    public final String errorMessage;

    private CommandPaletteProviderReport(String providerId,
                                         long durationMs,
                                         int actionCount,
                                         boolean success,
                                         String errorMessage) {
        this.providerId = providerId == null ? "" : providerId;
        this.durationMs = Math.max(0L, durationMs);
        this.actionCount = Math.max(0, actionCount);
        this.success = success;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static CommandPaletteProviderReport success(String providerId, long durationMs, int actionCount) {
        return new CommandPaletteProviderReport(providerId, durationMs, actionCount, true, "");
    }

    public static CommandPaletteProviderReport failure(String providerId, long durationMs, String errorMessage) {
        return new CommandPaletteProviderReport(providerId, durationMs, 0, false, errorMessage);
    }
}
