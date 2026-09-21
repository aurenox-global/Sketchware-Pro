package pro.sketchware.ai.rag;

public final class LocalAiSemanticChunk {

    public final String documentId;
    public final String path;
    public final String language;
    public final String snippet;
    public final int score;

    public LocalAiSemanticChunk(String documentId,
                                String path,
                                String language,
                                String snippet,
                                int score) {
        this.documentId = documentId == null ? "" : documentId;
        this.path = path == null ? "" : path;
        this.language = language == null ? "text" : language;
        this.snippet = snippet == null ? "" : snippet;
        this.score = Math.max(score, 0);
    }
}
