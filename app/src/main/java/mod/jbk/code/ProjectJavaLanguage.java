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
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import pro.sketchware.SketchApplication;

/**
 * Variante del lenguaje Java del editor que ademas ofrece autocompletado con los simbolos del
 * proyecto (clases, metodos y campos) y las palabras clave del lenguaje.
 *
 * <p>Todo lo demas (resaltado, indentado, emparejado de simbolos, auto-indentado) se delega en el
 * {@link JavaLanguage} de sora-editor, asi que el comportamiento del editor no cambia si algo
 * falla aqui: en el peor caso, simplemente no aparece la lista de sugerencias.
 */
public class ProjectJavaLanguage implements Language {

    private static final int MAX_ITEMS = 200;
    private static final int MAX_PREFIX_LENGTH = 64;
    /** Cuantas sugerencias como maximo vienen del SDK y de las librerias. */
    private static final int MAX_EXTERNAL_ITEMS = 120;

    private static final String[] KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "sealed", "yield"
    };

    private final JavaLanguage base = new JavaLanguage();
    private final String scId;

    public ProjectJavaLanguage(@Nullable String scId) {
        this.scId = scId;
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

        int added = 0;
        int prefixLength = prefix.length();

        // Palabras clave del lenguaje.
        for (String keyword : KEYWORDS) {
            if (added >= MAX_ITEMS) {
                break;
            }
            if (matches(keyword, prefix)) {
                publisher.addItem(new SimpleCompletionItem(keyword, "palabra clave", prefixLength, keyword)
                        .kind(CompletionItemKind.Text));
                added++;
            }
        }

        // Simbolos del proyecto (clases, metodos y campos).
        List<ProjectSymbolIndex.Symbol> symbols;
        try {
            symbols = ProjectSymbolIndex.getSymbols(scId);
        } catch (Throwable t) {
            return;
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

        // Clases del SDK de Android y de las librerias que usa el proyecto.
        try {
            addExternalSymbols(prefix, prefixLength, publisher, added);
        } catch (Throwable ignored) {
            // Sin SDK o sin librerias: nos quedamos con lo del proyecto.
        }
    }

    /**
     * Anade clases del SDK (android.jar) y de las librerias del proyecto. Se inserta el nombre simple
     * de la clase (como haria un IDE), y en la descripcion va el nombre completo.
     */
    private void addExternalSymbols(String prefix, int prefixLength, CompletionPublisher publisher, int alreadyAdded) {
        if (alreadyAdded >= MAX_ITEMS) {
            return;
        }
        android.content.Context context = SketchApplication.getContext();
        if (context == null) {
            return;
        }

        List<String> candidates = new ArrayList<>();
        candidates.addAll(SdkSymbolIndex.getSdkClasses(context));
        candidates.addAll(SdkSymbolIndex.getLibraryClasses(context, scId));
        if (candidates.isEmpty()) {
            return;
        }

        int added = alreadyAdded;
        int externalAdded = 0;
        for (String fullName : candidates) {
            if (added >= MAX_ITEMS || externalAdded >= MAX_EXTERNAL_ITEMS) {
                break;
            }
            String simpleName = simpleName(fullName);
            if (simpleName.isEmpty() || !startsWithIgnoreCase(simpleName, prefix)) {
                continue;
            }
            publisher.addItem(new SimpleCompletionItem(simpleName, fullName, prefixLength, simpleName)
                    .kind(CompletionItemKind.Class));
            added++;
            externalAdded++;
        }
    }

    private static String simpleName(String fullName) {
        int index = fullName.lastIndexOf('.');
        return index < 0 ? fullName : fullName.substring(index + 1);
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

    /** Palabra que se esta escribiendo justo antes del cursor. */
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

    private static boolean matches(String candidate, String prefix) {
        return startsWithIgnoreCase(candidate, prefix);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean containsIgnoreCase(String value, String prefix) {
        return value.toLowerCase().contains(prefix.toLowerCase());
    }
}
