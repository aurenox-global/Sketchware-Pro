package pro.sketchware.ai.rag;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Locale;

import pro.sketchware.utility.FileUtil;

public final class LocalAiProjectSemanticIndexer {

    private static final int MAX_INDEXED_FILES = 220;
    private static final int MAX_FILE_CHARS = 80000;
    private static final int DEFAULT_MAX_RESULTS = 4;
    private static final int DEFAULT_MAX_SNIPPET_CHARS = 900;
    private static final String PREFS_RAG_TS = "rag_last_indexed_ts";
    private static final String PREFS_RAG_COUNT = "rag_last_indexed_count";

    private final LocalAiProjectSemanticIndex semanticIndex;
    private Context appContext;

    public LocalAiProjectSemanticIndexer(LocalAiProjectSemanticIndex semanticIndex) {
        this.semanticIndex = semanticIndex == null ? new InMemoryLocalAiProjectSemanticIndex() : semanticIndex;
    }

    public void setContext(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    public LocalAiSemanticContext buildContext(String projectId,
                                               String currentDocumentPath,
                                               String queryText) {
        String safeProjectId = safe(projectId);
        String safeQueryText = safe(queryText).trim();
        if (safeProjectId.isEmpty() || safeQueryText.isEmpty()) {
            return LocalAiSemanticContext.EMPTY;
        }

        indexProjectFiles(safeProjectId);
        String excludedDocumentId = toRelativePath(safeProjectId, currentDocumentPath);

        LocalAiSemanticSearchQuery query = new LocalAiSemanticSearchQuery(
                safeProjectId,
                safeQueryText,
                excludedDocumentId,
                languageFromPath(currentDocumentPath),
                DEFAULT_MAX_RESULTS,
                DEFAULT_MAX_SNIPPET_CHARS
        );
        return semanticIndex.search(query);
    }

    private void indexProjectFiles(String projectId) {
        File filesDir = new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + projectId + "/files");
        if (!filesDir.exists() || !filesDir.isDirectory()) {
            return;
        }

        int[] indexableCount = {0};
        long currentMaxTs = getMaxIndexableFileTimestamp(filesDir, indexableCount);
        long lastIndexed = getLastIndexedTimestamp(projectId);
        int lastCount = getLastIndexedCount(projectId);
        if (currentMaxTs > 0 && currentMaxTs <= lastIndexed
                && indexableCount[0] == lastCount
                && semanticIndex.indexedDocumentCount(projectId) > 0) {
            return;
        }

        semanticIndex.clearProject(projectId);
        indexRecursively(projectId, filesDir, filesDir, new int[]{0});
        saveLastIndexedTimestamp(projectId, System.currentTimeMillis());
        saveLastIndexedCount(projectId, indexableCount[0]);
    }

    private long getMaxIndexableFileTimestamp(File dir, int[] indexableCount) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        if (dir.isFile()) {
            if (!"text".equals(languageFromPath(dir.getAbsolutePath()))) {
                indexableCount[0]++;
            }
            return dir.lastModified();
        }
        long max = dir.lastModified();
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                long childTs = getMaxIndexableFileTimestamp(child, indexableCount);
                if (childTs > max) {
                    max = childTs;
                }
            }
        }
        return max;
    }

    private long getLastIndexedTimestamp(String projectId) {
        if (appContext == null) {
            return 0L;
        }
        return appContext.getSharedPreferences(PREFS_RAG_TS, Context.MODE_PRIVATE)
                .getLong(projectId, 0L);
    }

    private void saveLastIndexedTimestamp(String projectId, long timestamp) {
        if (appContext == null) {
            return;
        }
        appContext.getSharedPreferences(PREFS_RAG_TS, Context.MODE_PRIVATE)
                .edit()
                .putLong(projectId, timestamp)
                .apply();
    }

    private int getLastIndexedCount(String projectId) {
        if (appContext == null) {
            return -1;
        }
        return appContext.getSharedPreferences(PREFS_RAG_COUNT, Context.MODE_PRIVATE)
                .getInt(projectId, -1);
    }

    private void saveLastIndexedCount(String projectId, int count) {
        if (appContext == null) {
            return;
        }
        appContext.getSharedPreferences(PREFS_RAG_COUNT, Context.MODE_PRIVATE)
                .edit()
                .putInt(projectId, count)
                .apply();
    }

    private void indexRecursively(String projectId,
                                  File rootDir,
                                  File current,
                                  int[] indexedCount) {
        if (indexedCount[0] >= MAX_INDEXED_FILES || current == null || !current.exists()) {
            return;
        }

        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                indexRecursively(projectId, rootDir, child, indexedCount);
                if (indexedCount[0] >= MAX_INDEXED_FILES) {
                    return;
                }
            }
            return;
        }

        String absolutePath = current.getAbsolutePath();
        String language = languageFromPath(absolutePath);
        if ("text".equals(language)) {
            return;
        }

        String rawContent = FileUtil.readFile(absolutePath);
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return;
        }

        String content = rawContent.length() > MAX_FILE_CHARS
                ? rawContent.substring(0, MAX_FILE_CHARS)
                : rawContent;
        String relativePath = toRelativePath(rootDir, current);

        semanticIndex.indexDocument(new LocalAiSemanticDocument(
                projectId,
                relativePath,
                relativePath,
                language,
                content,
                current.lastModified()
        ));
        indexedCount[0]++;
    }

    private static String languageFromPath(String path) {
        String safePath = safe(path).toLowerCase(Locale.US);
        if (safePath.endsWith(".java")) {
            return "java";
        }
        if (safePath.endsWith(".kt")) {
            return "kotlin";
        }
        if (safePath.endsWith(".xml")) {
            return "xml";
        }
        return "text";
    }

    private static String toRelativePath(String projectId, String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "";
        }

        File projectRoot = new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + projectId + "/files");
        return toRelativePath(projectRoot, new File(absolutePath));
    }

    private static String toRelativePath(File rootDir, File file) {
        if (rootDir == null || file == null) {
            return "";
        }

        String rootPath = rootDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) {
            return file.getName();
        }

        String relative = filePath.substring(rootPath.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative.replace('\\', '/');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
