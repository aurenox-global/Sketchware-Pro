package pro.sketchware.ai.rag;

public final class LocalAiSemanticSearchQuery {

    public final String projectId;
    public final String queryText;
    public final String excludeDocumentId;
    public final String languageHint;
    public final int maxResults;
    public final int maxSnippetChars;

    public LocalAiSemanticSearchQuery(String projectId,
                                      String queryText,
                                      String excludeDocumentId,
                                      String languageHint,
                                      int maxResults,
                                      int maxSnippetChars) {
        this.projectId = projectId == null ? "" : projectId;
        this.queryText = queryText == null ? "" : queryText;
        this.excludeDocumentId = excludeDocumentId == null ? "" : excludeDocumentId;
        this.languageHint = languageHint == null ? "" : languageHint;
        this.maxResults = Math.max(maxResults, 1);
        this.maxSnippetChars = Math.max(maxSnippetChars, 200);
    }
}
