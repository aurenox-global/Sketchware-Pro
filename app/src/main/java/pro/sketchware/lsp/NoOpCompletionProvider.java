package pro.sketchware.lsp;

import java.util.Collections;
import java.util.List;

public final class NoOpCompletionProvider implements LspCompletionProvider {

    @Override
    public String id() {
        return "noop-primary";
    }

    @Override
    public List<LspCompletionItem> getCompletions(LspSessionConfig config, LspCompletionRequest request) {
        return Collections.emptyList();
    }
}
