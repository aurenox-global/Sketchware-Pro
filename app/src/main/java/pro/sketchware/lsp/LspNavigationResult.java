package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LspNavigationResult {
    public final List<LspNavigationLocation> locations;
    public final String providerId;
    public final long durationMs;
    public final boolean fallbackUsed;
    public final boolean timedOut;
    public final String errorMessage;

    private LspNavigationResult(List<LspNavigationLocation> locations,
                                String providerId,
                                long durationMs,
                                boolean fallbackUsed,
                                boolean timedOut,
                                String errorMessage) {
        this.locations = Collections.unmodifiableList(new ArrayList<>(
                locations == null ? Collections.emptyList() : locations
        ));
        this.providerId = providerId == null ? "" : providerId;
        this.durationMs = Math.max(0L, durationMs);
        this.fallbackUsed = fallbackUsed;
        this.timedOut = timedOut;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static LspNavigationResult success(List<LspNavigationLocation> locations,
                                              String providerId,
                                              long durationMs,
                                              boolean fallbackUsed) {
        return new LspNavigationResult(locations, providerId, durationMs, fallbackUsed, false, "");
    }

    public static LspNavigationResult failure(String providerId,
                                              long durationMs,
                                              boolean fallbackUsed,
                                              boolean timedOut,
                                              String errorMessage) {
        return new LspNavigationResult(Collections.emptyList(), providerId, durationMs, fallbackUsed, timedOut, errorMessage);
    }
}
