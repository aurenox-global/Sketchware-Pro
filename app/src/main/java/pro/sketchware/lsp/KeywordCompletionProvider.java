package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class KeywordCompletionProvider implements LspCompletionProvider {

    private static final int MAX_ITEMS = 64;

    @Override
    public String id() {
        return "keyword-fallback";
    }

    @Override
    public List<LspCompletionItem> getCompletions(LspSessionConfig config, LspCompletionRequest request) {
        if (config == null || request == null) {
            return Collections.emptyList();
        }

        List<String> keywords = getKeywords(config.language);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedPrefix = request.prefix.trim().toLowerCase(Locale.US);
        List<LspCompletionItem> items = new ArrayList<>();

        for (String keyword : keywords) {
            if (!normalizedPrefix.isEmpty() && !keyword.toLowerCase(Locale.US).startsWith(normalizedPrefix)) {
                continue;
            }
            items.add(new LspCompletionItem(keyword, keyword, "keyword", id()));
            if (items.size() >= MAX_ITEMS) {
                break;
            }
        }

        return items;
    }

    private static List<String> getKeywords(LspLanguage language) {
        if (language == null) {
            return Collections.emptyList();
        }

        return switch (language) {
            case JAVA -> Arrays.asList(
                    "abstract", "boolean", "break", "case", "catch", "class", "continue",
                    "default", "do", "else", "enum", "extends", "final", "finally",
                    "for", "if", "implements", "import", "instanceof", "interface", "new",
                    "null", "package", "private", "protected", "public", "return", "static",
                    "super", "switch", "this", "throw", "throws", "try", "void", "while"
            );
            case KOTLIN -> Arrays.asList(
                    "as", "break", "class", "continue", "data", "do", "else", "false", "for",
                    "fun", "if", "in", "interface", "is", "null", "object", "override",
                    "package", "private", "protected", "public", "return", "super", "this",
                    "throw", "true", "try", "typealias", "val", "var", "when", "while"
            );
            case XML -> Arrays.asList(
                    "android:layout_width", "android:layout_height", "android:id", "android:text",
                    "android:orientation", "android:padding", "android:layout_margin",
                    "android:src", "android:contentDescription", "tools:context"
            );
            case PLAIN_TEXT -> Collections.emptyList();
        };
    }
}
