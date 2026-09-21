package pro.sketchware.lsp;

import java.util.List;

public interface LspCompletionProvider {
    String id();

    List<LspCompletionItem> getCompletions(LspSessionConfig config, LspCompletionRequest request) throws Exception;
}
