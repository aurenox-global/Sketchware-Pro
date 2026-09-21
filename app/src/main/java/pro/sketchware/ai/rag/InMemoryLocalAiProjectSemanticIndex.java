package pro.sketchware.ai.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryLocalAiProjectSemanticIndex implements LocalAiProjectSemanticIndex {

    private final ConcurrentMap<String, ConcurrentMap<String, LocalAiSemanticDocument>> projectDocuments = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            projectDocuments.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clearProject(String projectId) {
        String safeProjectId = safe(projectId);
        lock.writeLock().lock();
        try {
            projectDocuments.remove(safeProjectId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void indexDocument(LocalAiSemanticDocument document) {
        if (document == null || document.documentId.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            projectDocuments
                    .computeIfAbsent(safe(document.projectId), key -> new ConcurrentHashMap<>())
                    .put(document.documentId, document);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int indexedDocumentCount(String projectId) {
        lock.readLock().lock();
        try {
            Map<String, LocalAiSemanticDocument> docs = projectDocuments.get(safe(projectId));
            return docs == null ? 0 : docs.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public LocalAiSemanticContext search(LocalAiSemanticSearchQuery query) {
        if (query == null || query.queryText.trim().isEmpty()) {
            return LocalAiSemanticContext.EMPTY;
        }

        lock.readLock().lock();
        try {
            Map<String, LocalAiSemanticDocument> documents = projectDocuments.get(safe(query.projectId));
            if (documents == null || documents.isEmpty()) {
                return LocalAiSemanticContext.EMPTY;
            }

            Set<String> queryTerms = tokenize(query.queryText);
            if (queryTerms.isEmpty()) {
                return LocalAiSemanticContext.EMPTY;
            }

            Map<String, Integer> documentFrequencies = new HashMap<>();
            for (LocalAiSemanticDocument document : documents.values()) {
                if (document == null || document.documentId.equals(query.excludeDocumentId)) {
                    continue;
                }
                String loweredContent = document.content.toLowerCase(Locale.US);
                for (String term : queryTerms) {
                    if (loweredContent.indexOf(term) >= 0) {
                        documentFrequencies.merge(term, 1, Integer::sum);
                    }
                }
            }

            Map<String, Float> idfWeights = new HashMap<>();
            int documentCount = Math.max(1, documents.size());
            for (String term : queryTerms) {
                int documentFrequency = documentFrequencies.getOrDefault(term, 0);
                idfWeights.put(term, (float) (1.0 + Math.log(documentCount / (1.0 + documentFrequency))));
            }

            List<ScoredChunk> scoredChunks = new ArrayList<>();
            for (LocalAiSemanticDocument document : documents.values()) {
                if (document == null) {
                    continue;
                }
                if (document.documentId.equals(query.excludeDocumentId)) {
                    continue;
                }

                int score = scoreDocument(document, queryTerms, idfWeights, query.languageHint);
                if (score <= 0) {
                    continue;
                }

                String snippet = buildSnippet(document.content, queryTerms, query.maxSnippetChars);
                if (snippet.isEmpty()) {
                    continue;
                }

                scoredChunks.add(new ScoredChunk(new LocalAiSemanticChunk(
                        document.documentId,
                        document.path,
                        document.language,
                        snippet,
                        score
                )));
            }

            scoredChunks.sort(Comparator.comparingInt(ScoredChunk::score).reversed());
            boolean truncated = scoredChunks.size() > query.maxResults;

            List<LocalAiSemanticChunk> finalChunks = new ArrayList<>();
            int count = Math.min(query.maxResults, scoredChunks.size());
            for (int i = 0; i < count; i++) {
                finalChunks.add(scoredChunks.get(i).chunk);
            }

            return new LocalAiSemanticContext(finalChunks, truncated, documents.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    private static int scoreDocument(LocalAiSemanticDocument document,
                                     Set<String> queryTerms,
                                     Map<String, Float> idfWeights,
                                     String languageHint) {
        if (document.content.isEmpty()) {
            return 0;
        }

        String loweredContent = document.content.toLowerCase(Locale.US);
        Set<String> matchedTerms = new HashSet<>();
        double weightedHits = 0;
        for (String term : queryTerms) {
            if (term.isEmpty()) {
                continue;
            }

            int index = loweredContent.indexOf(term);
            int termCount = 0;
            while (index >= 0) {
                termCount++;
                matchedTerms.add(term);
                index = loweredContent.indexOf(term, index + term.length());
            }
            if (termCount > 0) {
                float idf = idfWeights.getOrDefault(term, 1f);
                double tf = 1.0 + Math.log(termCount);
                weightedHits += tf * idf;
            }
        }

        if (weightedHits <= 0) {
            return 0;
        }

        int score = (int) (weightedHits * 10) + (matchedTerms.size() * 20);

        String safeLanguageHint = safe(languageHint);
        if (!safeLanguageHint.isEmpty() && safe(document.language).equalsIgnoreCase(safeLanguageHint)) {
            score += 8;
        }

        String path = safe(document.path).toLowerCase(Locale.US);
        for (String term : queryTerms) {
            if (path.contains(term)) {
                score += 6;
            }
        }

        return score;
    }

    private static String buildSnippet(String content,
                                       Set<String> queryTerms,
                                       int maxSnippetChars) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        int safeSnippetChars = Math.max(maxSnippetChars, 200);
        String lowered = content.toLowerCase(Locale.US);

        int bestIndex = -1;
        for (String term : queryTerms) {
            int index = lowered.indexOf(term);
            if (index >= 0 && (bestIndex == -1 || index < bestIndex)) {
                bestIndex = index;
            }
        }

        if (bestIndex < 0) {
            bestIndex = 0;
        }

        int start = Math.max(0, bestIndex - (safeSnippetChars / 3));
        int end = Math.min(content.length(), start + safeSnippetChars);

        String snippet = content.substring(start, end).trim();
        if (start > 0) {
            snippet = "...\n" + snippet;
        }
        if (end < content.length()) {
            snippet = snippet + "\n...";
        }
        return snippet;
    }

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "u", "que", "con",
            "por", "para", "en", "del", "al", "como", "es", "son", "se", "su", "sus", "lo", "le", "les",
            "pero", "mas", "menos", "muy", "este", "esta", "esto", "ese", "esa", "eso", "mi", "mis", "tu",
            "tus", "no", "si", "ya", "sin", "sobre", "entre", "hacia", "hasta", "desde", "cuando", "donde",
            "tambien", "solo", "todo", "todos", "toda", "todas", "hay", "hacer", "puede", "pueden", "debe",
            "deben", "ser", "estado", "estan", "etc", "bien", "asi", "aqui", "ahi", "usuario", "aplicacion",
            "the", "a", "an", "and", "or", "of", "to", "in", "for", "on", "with", "as", "is", "are", "was",
            "were", "be", "been", "being", "it", "its", "this", "that", "these", "those", "at", "by",
            "from", "up", "down", "out", "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most",
            "other", "some", "such", "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "can", "will", "just", "should", "would", "could", "has", "have", "had", "do", "does", "did",
            "but", "if", "because", "until", "while", "about", "into", "through", "during", "before",
            "after", "above", "below", "your", "you", "we", "they", "he", "she", "him", "her", "them",
            "us", "my", "our", "their",
            "code", "class", "public", "private", "void", "static", "return", "new", "import", "string",
            "int", "long", "boolean", "else", "for", "while", "switch", "case", "break", "continue", "try",
            "catch", "finally", "throw", "throws", "extends", "implements", "interface", "package", "null",
            "true", "false", "this", "super", "final", "abstract", "function", "method", "variable",
            "value", "file", "use", "using", "used", "via", "eg", "ie", "example", "app", "android"
    ));

    private static Set<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        String normalized = text
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> tokens = new LinkedHashSet<>(Arrays.asList(normalized.split("\\s+")));
        tokens.removeIf(token -> token.length() < 2 || STOPWORDS.contains(token));
        if (tokens.isEmpty()) {
            return Collections.emptySet();
        }
        if (tokens.size() > 18) {
            List<String> firstTokens = new ArrayList<>(tokens).subList(0, 18);
            return new LinkedHashSet<>(firstTokens);
        }
        return tokens;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ScoredChunk {
        private final LocalAiSemanticChunk chunk;

        private ScoredChunk(LocalAiSemanticChunk chunk) {
            this.chunk = chunk;
        }

        private int score() {
            return chunk.score;
        }
    }
}
