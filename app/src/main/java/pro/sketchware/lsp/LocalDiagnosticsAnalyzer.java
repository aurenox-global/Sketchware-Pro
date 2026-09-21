package pro.sketchware.lsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalDiagnosticsAnalyzer {

    private static final Pattern XML_TAG_PATTERN = Pattern.compile("<(/?)([A-Za-z_][\\w.-]*)([^>]*)>");

    private LocalDiagnosticsAnalyzer() {
    }

    public static List<LspDiagnostic> analyze(LspLanguage language, String documentText) {
        String text = documentText == null ? "" : documentText;
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        List<LspDiagnostic> diagnostics = new ArrayList<>();
        analyzeBrackets(text, diagnostics);

        if (language == LspLanguage.XML) {
            analyzeXmlTags(text, diagnostics);
        }

        return diagnostics;
    }

    private static void analyzeBrackets(String text, List<LspDiagnostic> diagnostics) {
        ArrayDeque<BracketToken> stack = new ArrayDeque<>();
        int line = 0;
        int character = 0;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (current == '\n') {
                line++;
                character = 0;
                continue;
            }

            if (isOpenBracket(current)) {
                stack.push(new BracketToken(current, line, character));
            } else if (isCloseBracket(current)) {
                if (stack.isEmpty()) {
                    diagnostics.add(new LspDiagnostic(
                            LspSeverity.ERROR,
                            "Unexpected closing '" + current + "'",
                            new LspRange(line, character, line, character + 1),
                            "local-diagnostics"
                    ));
                } else {
                    BracketToken token = stack.pop();
                    if (!isMatchingBracket(token.value, current)) {
                        diagnostics.add(new LspDiagnostic(
                                LspSeverity.ERROR,
                                "Mismatched bracket: '" + token.value + "' closed by '" + current + "'",
                                new LspRange(line, character, line, character + 1),
                                "local-diagnostics"
                        ));
                    }
                }
            }

            character++;
        }

        while (!stack.isEmpty()) {
            BracketToken token = stack.pop();
            diagnostics.add(new LspDiagnostic(
                    LspSeverity.ERROR,
                    "Unclosed bracket '" + token.value + "'",
                    new LspRange(token.line, token.character, token.line, token.character + 1),
                    "local-diagnostics"
            ));
        }
    }

    private static void analyzeXmlTags(String text, List<LspDiagnostic> diagnostics) {
        ArrayDeque<XmlTagToken> stack = new ArrayDeque<>();
        Matcher matcher = XML_TAG_PATTERN.matcher(text);

        while (matcher.find()) {
            String full = matcher.group(0);
            String slash = matcher.group(1);
            String tagName = matcher.group(2);
            String trailing = matcher.group(3) == null ? "" : matcher.group(3);

            if (full.startsWith("<?") || full.startsWith("<!")) {
                continue;
            }

            if (trailing.trim().endsWith("/")) {
                continue;
            }

            LineColumn startPosition = toLineColumn(text, matcher.start());
            if ("/".equals(slash)) {
                if (stack.isEmpty()) {
                    diagnostics.add(new LspDiagnostic(
                            LspSeverity.ERROR,
                            "Unexpected closing tag </" + tagName + ">",
                            new LspRange(startPosition.line, startPosition.character,
                                    startPosition.line, startPosition.character + full.length()),
                            "local-diagnostics"
                    ));
                    continue;
                }

                XmlTagToken openTag = stack.pop();
                if (!openTag.name.equals(tagName)) {
                    diagnostics.add(new LspDiagnostic(
                            LspSeverity.ERROR,
                            "Mismatched XML closing tag </" + tagName + "> for <" + openTag.name + ">",
                            new LspRange(startPosition.line, startPosition.character,
                                    startPosition.line, startPosition.character + full.length()),
                            "local-diagnostics"
                    ));
                }
            } else {
                stack.push(new XmlTagToken(tagName, startPosition.line, startPosition.character));
            }
        }

        while (!stack.isEmpty()) {
            XmlTagToken openTag = stack.pop();
            diagnostics.add(new LspDiagnostic(
                    LspSeverity.ERROR,
                    "Unclosed XML tag <" + openTag.name + ">",
                    new LspRange(openTag.line, openTag.character, openTag.line,
                            openTag.character + openTag.name.length() + 2),
                    "local-diagnostics"
            ));
        }
    }

    private static LineColumn toLineColumn(String text, int index) {
        int line = 0;
        int character = 0;
        int safeIndex = Math.max(0, Math.min(index, text.length()));

        for (int i = 0; i < safeIndex; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                character = 0;
            } else {
                character++;
            }
        }

        return new LineColumn(line, character);
    }

    private static boolean isOpenBracket(char c) {
        return c == '(' || c == '{' || c == '[';
    }

    private static boolean isCloseBracket(char c) {
        return c == ')' || c == '}' || c == ']';
    }

    private static boolean isMatchingBracket(char open, char close) {
        return (open == '(' && close == ')')
                || (open == '{' && close == '}')
                || (open == '[' && close == ']');
    }

    private static final class BracketToken {
        private final char value;
        private final int line;
        private final int character;

        private BracketToken(char value, int line, int character) {
            this.value = value;
            this.line = line;
            this.character = character;
        }
    }

    private static final class XmlTagToken {
        private final String name;
        private final int line;
        private final int character;

        private XmlTagToken(String name, int line, int character) {
            this.name = name;
            this.line = line;
            this.character = character;
        }
    }

    private static final class LineColumn {
        private final int line;
        private final int character;

        private LineColumn(int line, int character) {
            this.line = line;
            this.character = character;
        }
    }
}
