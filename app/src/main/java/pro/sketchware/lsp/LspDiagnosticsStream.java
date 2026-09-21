package pro.sketchware.lsp;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class LspDiagnosticsStream {

    public interface Listener {
        void onDiagnosticsUpdated(LspDiagnosticsSnapshot snapshot);
    }

    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile LspDiagnosticsSnapshot latestSnapshot =
            new LspDiagnosticsSnapshot(System.currentTimeMillis(), Collections.emptyList());

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onDiagnosticsUpdated(latestSnapshot);
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    public void publish(List<LspDiagnostic> diagnostics) {
        LspDiagnosticsSnapshot snapshot = new LspDiagnosticsSnapshot(
                System.currentTimeMillis(),
                diagnostics
        );
        latestSnapshot = snapshot;

        for (Listener listener : listeners) {
            listener.onDiagnosticsUpdated(snapshot);
        }
    }

    public LspDiagnosticsSnapshot latestSnapshot() {
        return latestSnapshot;
    }
}
