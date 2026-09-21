package pro.sketchware.lsp;

import java.util.Locale;

public enum LspLanguage {
    JAVA,
    KOTLIN,
    XML,
    PLAIN_TEXT;

    public static LspLanguage fromScheme(String scheme) {
        if (scheme == null) {
            return PLAIN_TEXT;
        }

        String normalized = scheme.trim().toLowerCase(Locale.US);
        if ("java".equals(normalized)) {
            return JAVA;
        }
        if ("kotlin".equals(normalized) || "kt".equals(normalized)) {
            return KOTLIN;
        }
        if ("xml".equals(normalized)) {
            return XML;
        }
        return PLAIN_TEXT;
    }
}
