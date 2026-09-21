package pro.sketchware.ai;

import java.util.Locale;
import java.util.regex.Pattern;

public class ThinkModeParser {

    public enum Architecture {
        DEEPSEEK,
        QWEN3,
        QWEN35,
        GEMMA4,
        DETAILED,
        UNKNOWN
    }

    public static class ThinkResult {
        public final String thinkingText;
        public final String cleanResponse;
        public final boolean hasThinking;

        public ThinkResult(String thinkingText, String cleanResponse, boolean hasThinking) {
            this.thinkingText = thinkingText;
            this.cleanResponse = cleanResponse;
            this.hasThinking = hasThinking;
        }
    }

    public static Architecture detectArchitecture(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            return Architecture.UNKNOWN;
        }
        String lower = modelPath.toLowerCase(Locale.US);
        if (lower.contains("deepseek")) {
            return Architecture.DEEPSEEK;
        }
        if (lower.contains("lfm") || lower.contains("liquid")) {
            // LFM2.5 opens the assistant turn with "<think>" (reasoning model).
            return Architecture.QWEN3;
        }
        if (lower.contains("ornith") || lower.contains("qwen3.5") || lower.contains("qwen35")) {
            return Architecture.QWEN35;
        }
        if (lower.contains("qwen3")) {
            return Architecture.QWEN3;
        }
        if (lower.contains("gemma-4") || lower.contains("gemma4") || lower.contains("gemma_4")) {
            return Architecture.GEMMA4;
        }
        if (lower.contains("detailed")) {
            return Architecture.DETAILED;
        }
        return Architecture.UNKNOWN;
    }

    public static ThinkResult extractThinking(String rawResponse, Architecture arch) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return new ThinkResult(null, rawResponse == null ? "" : rawResponse, false);
        }

        String[] tags = getTagsForArchitecture(arch);
        ThinkResult result = tryExtract(rawResponse, tags[0], tags[1]);
        if (result != null) {
            return result;
        }

        if (arch == Architecture.UNKNOWN) {
            for (String[] pattern : TAG_PATTERNS) {
                if (pattern[0].equals(tags[0])) continue;
                result = tryExtract(rawResponse, pattern[0], pattern[1]);
                if (result != null) return result;
            }
        }

        return new ThinkResult(null, rawResponse, false);
    }

    private static ThinkResult tryExtract(String rawResponse, String openTag, String closeTag) {
        int openIdx = rawResponse.indexOf(openTag);
        if (openIdx < 0) {
            // The thinking block was opened by the chat template prompt itself (e.g. Qwen3.5
            // appends "<think>\n"), so the model's output only contains the closing tag.
            int closeOnlyIdx = rawResponse.indexOf(closeTag);
            if (closeOnlyIdx < 0) {
                return null;
            }
            String thinking = rawResponse.substring(0, closeOnlyIdx).trim();
            String clean = rawResponse.substring(closeOnlyIdx + closeTag.length()).trim();
            if (thinking.isEmpty()) {
                return null;
            }
            return new ThinkResult(thinking, clean, true);
        }

        int contentStart = openIdx + openTag.length();
        int closeIdx = rawResponse.indexOf(closeTag, contentStart);
        if (closeIdx < 0) return null;

        String thinking = rawResponse.substring(contentStart, closeIdx).trim();
        String clean = rawResponse.substring(closeIdx + closeTag.length()).trim();

        if (thinking.isEmpty()) return null;

        return new ThinkResult(thinking, clean, true);
    }

    private static final String[][] TAG_PATTERNS = {
        {"<think>", "</think>"},
        {"<thought>", "</thought>"},
        {"<detailed>", "</detailed>"},
        {"[think]", "[/think]"},
    };

    public static String removeThinkingLoops(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return rawResponse == null ? "" : rawResponse;
        }

        String text = removeFirstBlock(rawResponse);
        int nextThinkStart = findNextThinkStart(text);
        if (nextThinkStart >= 0) {
            text = text.substring(0, nextThinkStart);
        }
        text = STRAY_THINK_TAG.matcher(text).replaceAll(" ");
        return text.trim();
    }

    private static final Pattern STRAY_THINK_TAG = Pattern.compile(
            "</?\\s*(?:think|thought|detailed)\\s*>|\\[/?\\s*think\\s*\\]", Pattern.CASE_INSENSITIVE);

    private static String removeFirstBlock(String text) {
        int bestOpen = -1;
        String bestOpenTag = null;
        String bestCloseTag = null;
        for (String[] pattern : TAG_PATTERNS) {
            int open = text.indexOf(pattern[0]);
            if (open >= 0 && (bestOpen == -1 || open < bestOpen)) {
                bestOpen = open;
                bestOpenTag = pattern[0];
                bestCloseTag = pattern[1];
            }
        }
        if (bestOpen < 0) {
            return text;
        }
        int close = text.indexOf(bestCloseTag, bestOpen + bestOpenTag.length());
        if (close < 0) {
            return text;
        }
        return text.substring(0, bestOpen) + text.substring(close + bestCloseTag.length());
    }

    private static int findNextThinkStart(String text) {
        int earliest = -1;
        for (String[] pattern : TAG_PATTERNS) {
            int open = text.indexOf(pattern[0]);
            if (open >= 0 && (earliest == -1 || open < earliest)) {
                earliest = open;
            }
        }
        return earliest;
    }

    private static String[] getTagsForArchitecture(Architecture arch) {
        switch (arch) {
            case QWEN35:
            case QWEN3:
            default:
                return new String[]{"<think>", "</think>"};
            case DETAILED:
                return new String[]{"<detailed>", "</detailed>"};
        }
    }
}
