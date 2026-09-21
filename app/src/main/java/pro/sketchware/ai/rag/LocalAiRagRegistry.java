package pro.sketchware.ai.rag;

import android.content.Context;

public final class LocalAiRagRegistry {

    private static final LocalAiRagRegistry INSTANCE = new LocalAiRagRegistry();

    private final LocalAiProjectSemanticIndexer semanticIndexer =
            new LocalAiProjectSemanticIndexer(new InMemoryLocalAiProjectSemanticIndex());

    private LocalAiRagRegistry() {
    }

    public static LocalAiRagRegistry getInstance() {
        return INSTANCE;
    }

    public LocalAiProjectSemanticIndexer semanticIndexer() {
        return semanticIndexer;
    }

    public void initialize(Context context) {
        semanticIndexer.setContext(context.getApplicationContext());
    }
}
