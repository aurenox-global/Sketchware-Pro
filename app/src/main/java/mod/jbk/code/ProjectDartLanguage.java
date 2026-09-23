package mod.jbk.code;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

/**
 * Lenguaje del editor para ficheros Dart (Fase 7, soporte Flutter): resaltado (TextMate) mas
 * autocompletado con los simbolos del proyecto, las palabras clave de Dart y los tipos/funciones
 * mas comunes del core.
 *
 * <p>Todo lo que no es el autocompletado se delega en el lenguaje TextMate de Dart, asi que si algo
 * falla el editor se comporta igual que antes (peor caso: no aparece la lista). Igual que
 * {@link ProjectKotlinLanguage}: nunca debe romper el editor.
 */
public class ProjectDartLanguage implements Language {

    private static final int MAX_ITEMS = 200;
    private static final int MAX_PREFIX_LENGTH = 64;
    private static final int MIN_PREFIX_FOR_CORE = 2;

    /** Palabras clave y operadores de Dart (incluye las de asincronia y null-safety). */
    private static final String[] KEYWORDS = {
            "abstract", "as", "assert", "async", "await", "base", "break", "case", "catch", "class",
            "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else", "enum",
            "export", "extends", "extension", "external", "factory", "false", "final", "finally",
            "for", "Function", "get", "hide", "if", "implements", "import", "in", "interface", "is",
            "late", "library", "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "sealed", "set", "show", "static", "super", "switch", "sync", "this", "throw",
            "true", "try", "typedef", "var", "void", "when", "while", "with", "yield"
    };

    /** Tipos y miembros habituales de dart:core, para que el editor sea util desde el minuto uno. */
    private static final String[] CORE_SYMBOLS = {
            "print", "Object", "String", "int", "double", "num", "bool", "List", "Set", "Map",
            "Iterable", "Future", "Stream", "Duration", "DateTime", "Uri", "RegExp", "Exception",
            "Error", "ArgumentError", "StateError", "FormatException", "Stopwatch", "StringBuffer",
            "BigInt", "Type", "Symbol", "Function", "Comparable", "Iterator", "Null", "Never",
            "identical", "runtimeType", "toString", "hashCode"
    };

    /** Tipos de Flutter mas usados, para el autocompletado basico de widgets. */
    private static final String[] FLUTTER_SYMBOLS = {
            "Widget", "StatelessWidget", "StatefulWidget", "State", "BuildContext", "Container",
            "Column", "Row", "Stack", "Center", "Padding", "SizedBox", "Expanded", "Flexible",
            "Scaffold", "AppBar", "Text", "Icon", "Image", "ListView", "GridView", "TextField",
            "ElevatedButton", "TextButton", "IconButton", "MaterialApp", "CupertinoApp", "Navigator",
            "runApp", "setState", "build", "initState", "dispose", "FutureBuilder", "StreamBuilder"
    };

    private final Language base;
    private final String scId;

    public ProjectDartLanguage(@Nullable String scId) {
        this.scId = scId;
        Language textMate;
        try {
            textMate = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_DART);
        } catch (Throwable t) {
            textMate = new io.github.rosemoe.sora.lang.EmptyLanguage();
        }
        this.base = textMate;
    }

    // ------------------------------------------------------------------ delegados

    @Override
    public AnalyzeManager getAnalyzeManager() {
        return base.getAnalyzeManager();
    }

    @Override
    public int getInterruptionLevel() {
        return base.getInterruptionLevel();
    }

    @Override
    public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
        return base.getIndentAdvance(content, line, column);
    }

    @Override
    public boolean useTab() {
        return base.useTab();
    }

    @Nullable
    @Override
    public Formatter getFormatter() {
        return base.getFormatter();
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return base.getSymbolPairs();
    }

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return base.getNewlineHandlers();
    }

    @Override
    public QuickQuoteHandler getQuickQuoteHandler() {
        return base.getQuickQuoteHandler();
    }

    @Override
    public void destroy() {
        base.destroy();
    }

    // ------------------------------------------------------------------ autocompletado

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position,
                                    @NonNull CompletionPublisher publisher, @Nullable Bundle extraArguments) {
        String prefix = prefixAt(content, position);
        if (prefix.isEmpty() || prefix.length() > MAX_PREFIX_LENGTH) {
            return;
        }

        int prefixLength = prefix.length();
        int added = 0;

        for (String keyword : KEYWORDS) {
            if (added >= MAX_ITEMS) {
                break;
            }
            if (startsWithIgnoreCase(keyword, prefix)) {
                publisher.addItem(new SimpleCompletionItem(keyword, "palabra clave de Dart", prefixLength, keyword)
                        .kind(CompletionItemKind.Keyword));
                added++;
            }
        }

        for (String core : CORE_SYMBOLS) {
            if (added >= MAX_ITEMS) {
                break;
            }
            if (startsWithIgnoreCase(core, prefix)) {
                publisher.addItem(new SimpleCompletionItem(core, "dart:core", prefixLength, core)
                        .kind(CompletionItemKind.Class));
                added++;
            }
        }

        for (String flutter : FLUTTER_SYMBOLS) {
            if (added >= MAX_ITEMS) {
                break;
            }
            if (startsWithIgnoreCase(flutter, prefix)) {
                publisher.addItem(new SimpleCompletionItem(flutter, "Flutter", prefixLength, flutter)
                        .kind(CompletionItemKind.Class));
                added++;
            }
        }

        List<ProjectSymbolIndex.Symbol> symbols;
        try {
            symbols = ProjectSymbolIndex.getSymbols(scId);
        } catch (Throwable t) {
            symbols = new ArrayList<>();
        }

        for (ProjectSymbolIndex.Symbol symbol : symbols) {
            if (added >= MAX_ITEMS) {
                break;
            }
            if (symbol.name == null || symbol.name.equals(prefix) || !startsWithIgnoreCase(symbol.name, prefix)) {
                continue;
            }
            publisher.addItem(toItem(symbol, prefixLength));
            added++;
        }
    }

    private static SimpleCompletionItem toItem(ProjectSymbolIndex.Symbol symbol, int prefixLength) {
        CompletionItemKind kind;
        switch (symbol.kind) {
            case ProjectSymbolIndex.KIND_CLASS:
                kind = CompletionItemKind.Class;
                break;
            case ProjectSymbolIndex.KIND_METHOD:
                kind = CompletionItemKind.Method;
                break;
            default:
                kind = CompletionItemKind.Field;
                break;
        }
        return new SimpleCompletionItem(symbol.name, symbol.detail, prefixLength, symbol.name).kind(kind);
    }

    private static String prefixAt(ContentReference content, CharPosition position) {
        int line = position.line;
        int column = position.column;
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= MAX_PREFIX_LENGTH && column - i >= 0; i++) {
            char c;
            try {
                c = content.charAt(line, column - i);
            } catch (Throwable t) {
                break;
            }
            if (!isIdentifierPart(c)) {
                break;
            }
            builder.append(c);
        }
        return builder.reverse().toString();
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
