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
import pro.sketchware.SketchApplication;

/**
 * Lenguaje del editor para ficheros Kotlin: resaltado (TextMate) mas autocompletado con los
 * simbolos del proyecto, las palabras clave de Kotlin y las clases del SDK y de las librerias.
 *
 * <p>Todo lo que no es el autocompletado se delega en el lenguaje TextMate de Kotlin, asi que si
 * algo falla el editor se comporta igual que antes (peor caso: no aparece la lista).
 */
public class ProjectKotlinLanguage implements Language {

    private static final int MAX_ITEMS = 200;
    private static final int MAX_PREFIX_LENGTH = 64;
    private static final int MAX_CLASSPATH_ITEMS = 120;
    private static final int MIN_PREFIX_FOR_CLASSPATH = 2;

    private static final String[] KEYWORDS = {
            "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed", "abstract",
            "open", "override", "private", "protected", "public", "internal", "companion", "init",
            "constructor", "suspend", "inline", "reified", "tailrec", "operator", "infix", "vararg",
            "const", "lateinit", "lazy", "by", "out", "in", "is", "as", "where", "when", "if", "else",
            "for", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw",
            "package", "import", "typealias", "annotation", "this", "super", "true", "false", "null",
            "Null", "Unit", "Any", "Nothing", "String", "Int", "Long", "Double", "Float", "Boolean",
            "Char", "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet"
    };

    private final Language base;
    private final String scId;

    public ProjectKotlinLanguage(@Nullable String scId) {
        this.scId = scId;
        Language textMate;
        try {
            textMate = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN);
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
                publisher.addItem(new SimpleCompletionItem(keyword, "palabra clave de Kotlin", prefixLength, keyword)
                        .kind(CompletionItemKind.Text));
                added++;
            }
        }

        List<ProjectSymbolIndex.Symbol> symbols;
        try {
            symbols = ProjectSymbolIndex.getSymbols(scId);
        } catch (Throwable t) {
            symbols = new ArrayList<>();
        }

        List<ProjectSymbolIndex.Symbol> preferred = new ArrayList<>();
        List<ProjectSymbolIndex.Symbol> secondary = new ArrayList<>();
        for (ProjectSymbolIndex.Symbol symbol : symbols) {
            if (symbol.name == null || symbol.name.equals(prefix)) {
                continue;
            }
            if (startsWithIgnoreCase(symbol.name, prefix)) {
                preferred.add(symbol);
            } else if (containsIgnoreCase(symbol.name, prefix)) {
                secondary.add(symbol);
            }
        }
        for (ProjectSymbolIndex.Symbol symbol : preferred) {
            if (added >= MAX_ITEMS) {
                break;
            }
            publisher.addItem(toItem(symbol, prefixLength));
            added++;
        }
        for (ProjectSymbolIndex.Symbol symbol : secondary) {
            if (added >= MAX_ITEMS) {
                break;
            }
            publisher.addItem(toItem(symbol, prefixLength));
            added++;
        }

        if (prefixLength >= MIN_PREFIX_FOR_CLASSPATH && added < MAX_ITEMS) {
            addClasspathClasses(publisher, prefix, prefixLength, added);
        }
    }

    private void addClasspathClasses(@NonNull CompletionPublisher publisher, String prefix,
                                     int prefixLength, int alreadyAdded) {
        int added = alreadyAdded;
        try {
            List<String> classNames = SdkSymbolIndex.getSdkClasses(SketchApplication.getContext());
            classNames = new ArrayList<>(classNames);
            classNames.addAll(SdkSymbolIndex.getLibraryClasses(SketchApplication.getContext(), scId));

            int fromClasspath = 0;
            for (String fullName : classNames) {
                if (added >= MAX_ITEMS || fromClasspath >= MAX_CLASSPATH_ITEMS) {
                    break;
                }
                int lastDot = fullName.lastIndexOf('.');
                String simpleName = lastDot < 0 ? fullName : fullName.substring(lastDot + 1);
                if (simpleName.isEmpty() || simpleName.equals(prefix)
                        || !startsWithIgnoreCase(simpleName, prefix)) {
                    continue;
                }
                publisher.addItem(new SimpleCompletionItem(simpleName, fullName, prefixLength, simpleName)
                        .kind(CompletionItemKind.Class));
                added++;
                fromClasspath++;
            }
        } catch (Throwable ignored) {
            // Sin indice de classpath seguimos con lo del proyecto y las palabras clave.
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

    private static boolean containsIgnoreCase(String value, String prefix) {
        return value.toLowerCase().contains(prefix.toLowerCase());
    }
}
