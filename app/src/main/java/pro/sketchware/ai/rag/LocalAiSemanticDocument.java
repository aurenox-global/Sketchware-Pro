package pro.sketchware.ai.rag;

public final class LocalAiSemanticDocument {

    public final String projectId;
    public final String documentId;
    public final String path;
    public final String language;
    public final String content;
    public final long updatedAtMs;

    public LocalAiSemanticDocument(String projectId,
                                   String documentId,
                                   String path,
                                   String language,
                                   String content,
                                   long updatedAtMs) {
        this.projectId = projectId == null ? "" : projectId;
        this.documentId = documentId == null ? "" : documentId;
        this.path = path == null ? "" : path;
        this.language = language == null ? "text" : language;
        this.content = content == null ? "" : content;
        this.updatedAtMs = Math.max(updatedAtMs, 0L);
    }
}
