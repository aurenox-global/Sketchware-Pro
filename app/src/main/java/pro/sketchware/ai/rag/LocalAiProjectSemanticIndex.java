package pro.sketchware.ai.rag;

public interface LocalAiProjectSemanticIndex {

    void clear();

    void clearProject(String projectId);

    void indexDocument(LocalAiSemanticDocument document);

    int indexedDocumentCount(String projectId);

    LocalAiSemanticContext search(LocalAiSemanticSearchQuery query);
}
