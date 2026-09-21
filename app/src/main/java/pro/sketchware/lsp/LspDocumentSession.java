package pro.sketchware.lsp;

public interface LspDocumentSession {
    LspDiagnosticsStream diagnostics();

    LspCompletionResult requestCompletions(LspCompletionRequest request);

    LspNavigationResult requestDefinition(LspNavigationRequest request);

    LspNavigationResult requestReferences(LspNavigationRequest request);

    void openDocument(String documentText);

    void updateDocument(String documentText);

    void closeDocument();

    void dispose();
}
