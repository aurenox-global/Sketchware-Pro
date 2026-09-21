package pro.sketchware.ai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocalAiSemanticContext {

    public static final LocalAiSemanticContext EMPTY = new LocalAiSemanticContext(Collections.emptyList(), false, 0);

    public final List<LocalAiSemanticChunk> chunks;
    public final boolean truncated;
    public final int indexedDocuments;

    public LocalAiSemanticContext(List<LocalAiSemanticChunk> chunks,
                                  boolean truncated,
                                  int indexedDocuments) {
        this.chunks = Collections.unmodifiableList(new ArrayList<>(
                chunks == null ? Collections.emptyList() : chunks
        ));
        this.truncated = truncated;
        this.indexedDocuments = Math.max(indexedDocuments, 0);
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public String toPromptSection() {
        if (isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Relevant project context (RAG):\n");
        for (int i = 0; i < chunks.size(); i++) {
            LocalAiSemanticChunk chunk = chunks.get(i);
            builder.append(i + 1)
                    .append(") ")
                    .append(chunk.path)
                    .append(" [")
                    .append(chunk.language)
                    .append("] score=")
                    .append(chunk.score)
                    .append("\n")
                    .append("```\n")
                    .append(chunk.snippet)
                    .append("\n```\n");
        }
        if (truncated) {
            builder.append("(context list truncated)\n");
        }
        return builder.toString();
    }
}
