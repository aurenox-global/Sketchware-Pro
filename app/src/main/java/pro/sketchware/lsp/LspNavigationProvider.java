package pro.sketchware.lsp;

import java.util.List;

public interface LspNavigationProvider {
    String id();

    List<LspNavigationLocation> findDefinition(LspSessionConfig config, LspNavigationRequest request) throws Exception;

    List<LspNavigationLocation> findReferences(LspSessionConfig config, LspNavigationRequest request) throws Exception;
}
