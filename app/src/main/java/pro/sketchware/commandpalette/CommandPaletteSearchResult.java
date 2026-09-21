package pro.sketchware.commandpalette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandPaletteSearchResult {

    public final List<CommandPaletteAction> actions;
    public final List<CommandPaletteProviderReport> providerReports;
    public final long durationMs;

    public CommandPaletteSearchResult(List<CommandPaletteAction> actions,
                                      List<CommandPaletteProviderReport> providerReports,
                                      long durationMs) {
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions == null
                ? Collections.emptyList()
                : actions));
        this.providerReports = Collections.unmodifiableList(new ArrayList<>(providerReports == null
                ? Collections.emptyList()
                : providerReports));
        this.durationMs = Math.max(0L, durationMs);
    }
}
