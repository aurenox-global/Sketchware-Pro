package pro.sketchware.commandpalette;

import java.util.Collections;
import java.util.List;

public final class NoOpCommandPaletteProvider implements CommandPaletteProvider {

    @Override
    public String id() {
        return "noop";
    }

    @Override
    public List<CommandPaletteAction> getActions(CommandPaletteQuery query) {
        return Collections.emptyList();
    }
}
