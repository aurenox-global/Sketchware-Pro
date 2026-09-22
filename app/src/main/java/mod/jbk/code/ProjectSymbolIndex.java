package mod.jbk.code;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Indice ligero de simbolos del proyecto (clases, metodos y campos) con su ubicacion, para el
 * autocompletado y la navegacion del editor de codigo.
 *
 * <p>No usa el compilador: lee los .java/.kt de {@code files/java} del proyecto y extrae las
 * declaraciones con expresiones regulares. Es deliberadamente barato para poder refrescarlo sin que
 * se note en el movil. Se cachea por proyecto y se invalida cuando cambia la fecha de modificacion
 * del directorio de fuentes.
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
        /** Ruta absoluta del fichero donde esta declarado. */
        public final String filePath;
        /** Linea de la declaracion (0-based). */
        public final int line;

        Symbol(String name, String detail, int kind, String filePath, int line) {
            this.name = name;
            this.detail = detail;
            this.kind = kind;
            this.filePath = filePath == null ? "" : filePath;
            this.line = Math.max(0, line);
        }
    }

    private static final class Entry {
        final long stamp;
        final List<Symbol> symbols;
        final List<String> sourceFiles;

        Entry(long stamp, List<Symbol> symbols, List<String> sourceFiles) {
            this.stamp = stamp;
            this.symbols = symbols;
            this.sourceFiles = sourceFiles;
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

    /** Kotlin: class / interface / object / enum class / data class / sealed class. */
    private static final Pattern KOTLIN_TYPE_DECL = Pattern.compile(
            "\\b(?:(?:data|sealed|abstract|open|internal|private|public|enum|annotation|value)\\s+)*" +
                    "(?:class|interface|object)\\s+([A-Za-z_$][\\w$]*)");
    /** Kotlin: fun nombre(...), con o sin receptor y con genéricos. */
    private static final Pattern KOTLIN_FUN_DECL = Pattern.compile(
            "\\bfun\\s+(?:<[^>]{0,80}>\\s*)?(?:[\\w.<>?,]+\\.)?([A-Za-z_$][\\w$]*)\\s*\\(");
    /** Kotlin: val/var nombre (propiedades de clase y de nivel superior). */
    private static final Pattern KOTLIN_PROPERTY_DECL = Pattern.compile(
            "\\b(?:val|var)\\s+([A-Za-z_$][\\w$]*)\\s*(?::[^=\\n]{0,60})?(?:=|$)", Pattern.MULTILINE);

    private ProjectSymbolIndex() {
    }

    /** Devuelve los simbolos del proyecto, refrescando la cache si las fuentes cambiaron. */
    public static synchronized List<Symbol> getSymbols(String scId) {
        return entry(scId).symbols;
    }

    /** Ficheros de codigo del proyecto (rutas absolutas). */
    public static synchronized List<String> getSourceFiles(String scId) {
        return entry(scId).sourceFiles;
    }

    /** Metodos disponibles para la vista previa/depuracion. */
    public static synchronized int size(String scId) {
        return getSymbols(scId).size();
    }

    private static Entry entry(String scId) {
        if (scId == null || scId.isEmpty()) {
            return new Entry(-1L, Collections.emptyList(), Collections.emptyList());
        }

        File dir;
        try {
            dir = new File(new FilePathUtil().getPathJava(scId));
        } catch (Throwable t) {
            return new Entry(-1L, Collections.emptyList(), Collections.emptyList());
        }

        long stamp = lastModified(dir);
        Entry cached = cache.get(scId);
        if (cached != null && cached.stamp == stamp) {
            return cached;
        }

        List<File> sources = new ArrayList<>();
        collectSources(dir, sources);
        List<String> paths = new ArrayList<>(sources.size());
        for (File file : sources) {
            paths.add(file.getAbsolutePath());
        }

        Entry fresh = new Entry(stamp, scan(sources), paths);
        cache.put(scId, fresh);
        return fresh;
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

    private static List<Symbol> scan(List<File> sources) {
        Map<String, Symbol> unique = new LinkedHashMap<>();
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
            collectDeclarations(text, file.getAbsolutePath(), file.getName().endsWith(".kt"), unique);
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

    private static void collectDeclarations(String text, String filePath, boolean kotlin, Map<String, Symbol> out) {
        if (kotlin) {
            collectKotlinDeclarations(text, filePath, out);
        }

        Matcher m = TYPE_DECL.matcher(text);
        int searchFrom = 0;
        int line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            put(out, m.group(1), "clase del proyecto", KIND_CLASS, filePath, line - 1);
        }

        m = METHOD_DECL.matcher(text);
        searchFrom = 0;
        line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            String returnType = m.group(1);
            String name = m.group(2);
            // Evita capturas falsas tipo "if (...)" o "for (...)".
            if (isKeyword(name) || isKeyword(returnType)) {
                continue;
            }
            String params = m.group(3) == null ? "" : m.group(3).trim();
            put(out, name, returnType + " " + name + "(" + params + ")", KIND_METHOD, filePath, line - 1);
        }

        m = FIELD_DECL.matcher(text);
        searchFrom = 0;
        line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            String type = m.group(1);
            String name = m.group(2);
            if (isKeyword(name) || isKeyword(type)) {
                continue;
            }
            put(out, name, type + " " + name, KIND_FIELD, filePath, line - 1);
        }
    }

    /** Declaraciones propias de Kotlin: clases/objetos, funciones y propiedades. */
    private static void collectKotlinDeclarations(String text, String filePath, Map<String, Symbol> out) {
        Matcher m = KOTLIN_TYPE_DECL.matcher(text);
        int searchFrom = 0;
        int line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            put(out, m.group(1), "clase de Kotlin del proyecto", KIND_CLASS, filePath, line - 1);
        }

        m = KOTLIN_FUN_DECL.matcher(text);
        searchFrom = 0;
        line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            String name = m.group(1);
            if (isKeyword(name)) {
                continue;
            }
            put(out, name, "fun " + name + "(...)", KIND_METHOD, filePath, line - 1);
        }

        m = KOTLIN_PROPERTY_DECL.matcher(text);
        searchFrom = 0;
        line = 1;
        while (m.find()) {
            line = advance(text, searchFrom, m.start(), line);
            searchFrom = m.start();
            String name = m.group(1);
            if (isKeyword(name)) {
                continue;
            }
            put(out, name, "propiedad de Kotlin: " + name, KIND_FIELD, filePath, line - 1);
        }
    }

    /** Avanza el contador de linea desde {@code from} hasta {@code to} (exclusive). */
    private static int advance(String text, int from, int to, int currentLine) {
        int line = currentLine;
        for (int i = Math.max(0, from); i < to && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static void put(Map<String, Symbol> out, String name, String detail, int kind,
                            String filePath, int line) {
        if (name == null || name.isEmpty() || out.size() >= MAX_SYMBOLS) {
            return;
        }
        Symbol previous = out.get(name);
        // Las clases ganan a metodos y campos con el mismo nombre.
        if (previous == null || previous.kind > kind) {
            out.put(name, new Symbol(name, detail, kind, filePath, line));
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

    /** Nombres de simbolos como conjunto, para comprobaciones rapidas. */
    public static synchronized Set<String> getSymbolNames(String scId) {
        Set<String> names = new HashSet<>();
        for (Symbol symbol : getSymbols(scId)) {
            names.add(symbol.name);
        }
        return names;
    }
}
