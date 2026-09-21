package pro.sketchware.lsp;

public interface LspClient {
    LspDocumentSession createSession(LspSessionConfig config);
}
