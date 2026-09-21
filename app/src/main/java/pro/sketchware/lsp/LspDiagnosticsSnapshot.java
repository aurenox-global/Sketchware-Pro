package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LspDiagnosticsSnapshot {
    public final long timestampMs;
    public final List<LspDiagnostic> diagnostics;

    public LspDiagnosticsSnapshot(long timestampMs, List<LspDiagnostic> diagnostics) {
        this.timestampMs = timestampMs;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(
                diagnostics == null ? Collections.emptyList() : diagnostics
        ));
    }
}
