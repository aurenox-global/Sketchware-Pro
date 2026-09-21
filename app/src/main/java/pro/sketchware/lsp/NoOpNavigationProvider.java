package pro.sketchware.lsp;

import java.util.Collections;
import java.util.List;

public final class NoOpNavigationProvider implements LspNavigationProvider {

    @Override
    public String id() {
        return "noop-navigation-primary";
    }

    @Override
    public List<LspNavigationLocation> findDefinition(LspSessionConfig config, LspNavigationRequest request) {
        return Collections.emptyList();
    }

    @Override
    public List<LspNavigationLocation> findReferences(LspSessionConfig config, LspNavigationRequest request) {
        return Collections.emptyList();
    }
}
