package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LspCompletionResult {
    public final List<LspCompletionItem> items;
    public final String providerId;
    public final long durationMs;
    public final boolean fallbackUsed;
    public final boolean timedOut;
    public final String errorMessage;

    private LspCompletionResult(List<LspCompletionItem> items,
                                String providerId,
                                long durationMs,
                                boolean fallbackUsed,
                                boolean timedOut,
                                String errorMessage) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? Collections.emptyList() : items));
        this.providerId = providerId == null ? "" : providerId;
        this.durationMs = Math.max(0L, durationMs);
        this.fallbackUsed = fallbackUsed;
        this.timedOut = timedOut;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static LspCompletionResult success(List<LspCompletionItem> items,
                                              String providerId,
                                              long durationMs,
                                              boolean fallbackUsed) {
        return new LspCompletionResult(items, providerId, durationMs, fallbackUsed, false, "");
    }

    public static LspCompletionResult failure(String providerId,
                                              long durationMs,
                                              boolean fallbackUsed,
                                              boolean timedOut,
                                              String errorMessage) {
        return new LspCompletionResult(Collections.emptyList(), providerId, durationMs, fallbackUsed, timedOut, errorMessage);
    }
}
