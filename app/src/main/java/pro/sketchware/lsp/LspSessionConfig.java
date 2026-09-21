package pro.sketchware.lsp;

public final class LspSessionConfig {
    public final String projectId;
    public final String documentPath;
    public final LspLanguage language;

    public LspSessionConfig(String projectId, String documentPath, LspLanguage language) {
        this.projectId = projectId == null ? "" : projectId;
        this.documentPath = documentPath == null ? "" : documentPath;
        this.language = language == null ? LspLanguage.PLAIN_TEXT : language;
    }
}
