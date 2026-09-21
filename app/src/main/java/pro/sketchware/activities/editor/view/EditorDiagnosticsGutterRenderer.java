package pro.sketchware.activities.editor.view;

import java.util.Collections;

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.widget.CodeEditor;
import pro.sketchware.lsp.LspDiagnostic;
import pro.sketchware.lsp.LspDiagnosticsSnapshot;
import pro.sketchware.lsp.LspSeverity;

public final class EditorDiagnosticsGutterRenderer {

    private EditorDiagnosticsGutterRenderer() {
    }

    public static void render(CodeEditor editor, LspDiagnosticsSnapshot snapshot) {
        if (editor == null) {
            return;
        }

        DiagnosticsContainer container = new DiagnosticsContainer();
        String text = editor.getText().toString();

        if (snapshot != null) {
            for (LspDiagnostic diagnostic : snapshot.diagnostics) {
                if (diagnostic == null) {
                    continue;
                }
                int startIndex = toIndex(text, diagnostic.range.startLine, diagnostic.range.startCharacter);
                int endIndex = toIndex(text, diagnostic.range.endLine, diagnostic.range.endCharacter);
                if (endIndex <= startIndex) {
                    endIndex = Math.min(text.length(), startIndex + 1);
                }

                DiagnosticDetail detail = new DiagnosticDetail(
                        diagnostic.message,
                        diagnostic.message,
                        Collections.emptyList(),
                        diagnostic.source
                );

                container.addDiagnostic(new DiagnosticRegion(
                        startIndex,
                        endIndex,
                        toSoraSeverity(diagnostic.severity),
                        0L,
                        detail
                ));
            }
        }

        editor.setDiagnostics(container);
    }

    private static int toIndex(String text, int targetLine, int targetColumn) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int line = 0;
        int column = 0;
        int index = 0;

        int safeLine = Math.max(0, targetLine);
        int safeColumn = Math.max(0, targetColumn);

        while (index < text.length()) {
            if (line == safeLine && column >= safeColumn) {
                break;
            }

            char c = text.charAt(index);
            if (c == '\n') {
                line++;
                column = 0;
                index++;
                if (line > safeLine) {
                    break;
                }
                continue;
            }

            column++;
            index++;
        }

        return Math.max(0, Math.min(index, text.length()));
    }

    private static short toSoraSeverity(LspSeverity severity) {
        if (severity == null) {
            return DiagnosticRegion.SEVERITY_WARNING;
        }

        return switch (severity) {
            case ERROR -> DiagnosticRegion.SEVERITY_ERROR;
            case WARNING -> DiagnosticRegion.SEVERITY_WARNING;
            case INFO, HINT -> DiagnosticRegion.SEVERITY_TYPO;
        };
    }
}
