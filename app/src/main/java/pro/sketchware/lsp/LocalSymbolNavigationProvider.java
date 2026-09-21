package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalSymbolNavigationProvider implements LspNavigationProvider {

    private static final int MAX_REFERENCES = 256;
    private static final Pattern JAVA_KOTLIN_DECL_KEYWORDS = Pattern.compile("\\b(class|interface|enum|record|fun|val|var|void|boolean|byte|short|int|long|float|double|char|String)\\b");

    @Override
    public String id() {
        return "local-symbol-fallback";
    }

    @Override
    public List<LspNavigationLocation> findDefinition(LspSessionConfig config, LspNavigationRequest request) {
        String text = request == null ? "" : request.documentText;
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        String symbol = resolveSymbol(text, request);
        if (symbol.isEmpty()) {
            return Collections.emptyList();
        }

        String[] lines = text.split("\\n", -1);
        Pattern symbolPattern = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");

        LspNavigationLocation declaration = findDeclaration(config, lines, symbolPattern);
        if (declaration != null) {
            return Collections.singletonList(declaration);
        }

        LspNavigationLocation firstUsage = findFirstUsage(config, lines, symbolPattern);
        if (firstUsage != null) {
            return Collections.singletonList(firstUsage);
        }

        return Collections.emptyList();
    }

    @Override
    public List<LspNavigationLocation> findReferences(LspSessionConfig config, LspNavigationRequest request) {
        String text = request == null ? "" : request.documentText;
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        String symbol = resolveSymbol(text, request);
        if (symbol.isEmpty()) {
            return Collections.emptyList();
        }

        String[] lines = text.split("\\n", -1);
        Pattern symbolPattern = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");
        List<LspNavigationLocation> locations = new ArrayList<>();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            Matcher matcher = symbolPattern.matcher(lines[lineIndex]);
            while (matcher.find()) {
                locations.add(new LspNavigationLocation(
                        config == null ? "" : config.documentPath,
                        new LspRange(lineIndex, matcher.start(), lineIndex, matcher.end()),
                        lines[lineIndex].trim()
                ));

                if (locations.size() >= MAX_REFERENCES) {
                    return locations;
                }
            }
        }

        return locations;
    }

    private static LspNavigationLocation findDeclaration(LspSessionConfig config,
                                                         String[] lines,
                                                         Pattern symbolPattern) {
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = symbolPattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }

            String leftSide = line.substring(0, matcher.start());
            if (!JAVA_KOTLIN_DECL_KEYWORDS.matcher(leftSide).find()) {
                continue;
            }

            return new LspNavigationLocation(
                    config == null ? "" : config.documentPath,
                    new LspRange(lineIndex, matcher.start(), lineIndex, matcher.end()),
                    line.trim()
            );
        }
        return null;
    }

    private static LspNavigationLocation findFirstUsage(LspSessionConfig config,
                                                        String[] lines,
                                                        Pattern symbolPattern) {
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            Matcher matcher = symbolPattern.matcher(lines[lineIndex]);
            if (!matcher.find()) {
                continue;
            }
            return new LspNavigationLocation(
                    config == null ? "" : config.documentPath,
                    new LspRange(lineIndex, matcher.start(), lineIndex, matcher.end()),
                    lines[lineIndex].trim()
            );
        }
        return null;
    }

    private static String resolveSymbol(String text, LspNavigationRequest request) {
        if (request != null && request.symbol != null && !request.symbol.trim().isEmpty()) {
            return request.symbol.trim();
        }
        if (request == null) {
            return "";
        }

        String[] lines = text.split("\\n", -1);
        if (request.line < 0 || request.line >= lines.length) {
            return "";
        }

        String line = lines[request.line];
        if (line.isEmpty()) {
            return "";
        }

        int clampedCharacter = Math.max(0, Math.min(request.character, line.length() - 1));
        int start = clampedCharacter;
        int end = clampedCharacter;

        while (start > 0 && isIdentifierChar(line.charAt(start - 1))) {
            start--;
        }
        while (end < line.length() && isIdentifierChar(line.charAt(end))) {
            end++;
        }

        if (end <= start) {
            return "";
        }

        return line.substring(start, end).trim();
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
