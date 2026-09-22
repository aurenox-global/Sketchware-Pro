package mod.jbk.code;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Indice ligero de simbolos del proyecto (clases, metodos y campos) para el autocompletado
 * del editor de codigo.
 *
 * <p>No usa el compilador: lee los .java/.kt de {@code files/java} del proyecto y extrae las
 * declaraciones con expresiones regulares. Es deliberadamente barato para poder refrescarlo
 * sin que se note en el movil. Se cachea por proyecto y se invalida cuando cambia la fecha de
 * modificacion del directorio de fuentes.
 */
public class ProjectSymbolIndex {

    /** Tipos de simbolo, alineados con los que usa el editor. */
    public static final int KIND_CLASS = 0;
    public static final int KIND_METHOD = 1;
    public static final int KIND_FIELD = 2;

    /** Limites de seguridad para no penalizar proyectos grandes. */
    private static final int MAX_FILES = 400;
    private static final int MAX_SYMBOLS = 4000;
    private static final int MAX_FILE_BYTES = 512 * 1024;

    public static final class Symbol {
        public final String name;
        public final String detail;
        public final int kind;

        Symbol(String name, String detail, int kind) {
            this.name = name;
            this.detail = detail;
            this.kind = kind;
        }
    }

    private static final class Entry {
        final long stamp;
        final List<Symbol> symbols;

        Entry(long stamp, List<Symbol> symbols) {
            this.stamp = stamp;
            this.symbols = symbols;
        }
    }

    private static final Map<String, Entry> cache = new HashMap<>();

    private static final Pattern TYPE_DECL =
            Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHOD_DECL =
            Pattern.compile("\\b([A-Za-z_$][\\w$<>\\[\\],.]*)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]{0,160})\\)\\s*(?:throws\\s+[\\w\\s,.]+)?\\{");
    private static final Pattern FIELD_DECL =
            Pattern.compile("^[\\t ]*(?:(?:public|private|protected|static|final|volatile|transient)\\s+)+([A-Za-z_$][\\w$<>\\[\\],.]*)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=[^;]*)?;",
                    Pattern.MULTILINE);

    private ProjectSymbolIndex() {
    }

    /** Devuelve los simbolos del proyecto, refrescando la cache si las fuentes cambiaron. */
    public static synchronized List<Symbol> getSymbols(String scId) {
        if (scId == null || scId.isEmpty()) {
            return Collections.emptyList();
        }

        File dir;
        try {
            dir = new File(new FilePathUtil().getPathJava(scId));
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        long stamp = lastModified(dir);
        Entry cached = cache.get(scId);
        if (cached != null && cached.stamp == stamp) {
            return cached.symbols;
        }

        List<Symbol> symbols = scan(dir);
        cache.put(scId, new Entry(stamp, symbols));
        return symbols;
    }

    /** Metodos disponibles para la vista previa/depuracion. */
    public static synchronized int size(String scId) {
        return getSymbols(scId).size();
    }

    private static long lastModified(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return -1L;
        }
        long newest = dir.lastModified();
        File[] files = dir.listFiles();
        if (files == null) {
            return newest;
        }
        for (File f : files) {
            long l = f.isDirectory() ? lastModified(f) : f.lastModified();
            if (l > newest) {
                newest = l;
            }
        }
        return newest;
    }

    private static List<Symbol> scan(File dir) {
        Map<String, Symbol> unique = new LinkedHashMap<>();
        List<File> sources = new ArrayList<>();
        collectSources(dir, sources);

        int files = 0;
        for (File file : sources) {
            if (files++ >= MAX_FILES || unique.size() >= MAX_SYMBOLS) {
                break;
            }
            if (file.length() > MAX_FILE_BYTES) {
                continue;
            }
            String text;
            try {
                text = FileUtil.readFile(file.getAbsolutePath());
            } catch (Throwable t) {
                continue;
            }
            if (text == null || text.isEmpty()) {
                continue;
            }
            collectDeclarations(text, unique);
        }
        return new ArrayList<>(unique.values());
    }

    private static void collectSources(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory() || out.size() >= MAX_FILES) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectSources(f, out);
            } else {
                String name = f.getName();
                if (name.endsWith(".java") || name.endsWith(".kt")) {
                    out.add(f);
                }
            }
        }
    }

    private static void collectDeclarations(String text, Map<String, Symbol> out) {
        Matcher m = TYPE_DECL.matcher(text);
        while (m.find()) {
            put(out, m.group(1), "clase del proyecto", KIND_CLASS);
        }

        m = METHOD_DECL.matcher(text);
        while (m.find()) {
            String returnType = m.group(1);
            String name = m.group(2);
            // Evita capturas falsas tipo "if (...)" o "for (...)".
            if (isKeyword(name) || isKeyword(returnType)) {
                continue;
            }
            String params = m.group(3) == null ? "" : m.group(3).trim();
            put(out, name, returnType + " " + name + "(" + params + ")", KIND_METHOD);
        }

        m = FIELD_DECL.matcher(text);
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            if (isKeyword(name) || isKeyword(type)) {
                continue;
            }
            put(out, name, type + " " + name, KIND_FIELD);
        }
    }

    private static void put(Map<String, Symbol> out, String name, String detail, int kind) {
        if (name == null || name.isEmpty() || out.size() >= MAX_SYMBOLS) {
            return;
        }
        Symbol previous = out.get(name);
        // Las clases ganan a metodos y campos con el mismo nombre.
        if (previous == null || previous.kind > kind) {
            out.put(name, new Symbol(name, detail, kind));
        }
    }

    private static boolean isKeyword(String word) {
        switch (word) {
            case "if":
            case "for":
            case "while":
            case "switch":
            case "catch":
            case "return":
            case "new":
            case "else":
            case "do":
            case "try":
            case "synchronized":
            case "super":
            case "this":
            case "class":
            case "interface":
            case "enum":
                return true;
            default:
                return false;
        }
    }
}
