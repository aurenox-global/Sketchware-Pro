package mod.jbk.code;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Diagnostico de Java "al vuelo" reutilizando el compilador de Eclipse (ECJ) que la app ya lleva
 * dentro para compilar los proyectos.
 *
 * <p>Compila <b>solo</b> el fichero que se esta editando (con el contenido actual del editor, que
 * puede no estar guardado) contra el classpath del proyecto, y devuelve los problemas encontrados
 * con su linea para poder subrayarlos.
 *
 * <p>Es deliberadamente conservador: si algo falla (no hay android.jar, el compilador tarda...),
 * devuelve una lista vacia y el editor sigue funcionando.
 */
public final class JavaDiagnosticsAnalyzer {

    public static final class Problem {
        public final int line;
        public final int severity;
        public final String message;
        /** Nombres completos de clase que podrian resolver un tipo no resuelto (para "importar"). */
        public final List<String> importCandidates;

        Problem(int line, int severity, String message, List<String> importCandidates) {
            this.line = line;
            this.severity = severity;
            this.message = message;
            this.importCandidates = importCandidates == null
                    ? Collections.emptyList() : importCandidates;
        }
    }

    /**
     * Formato clasico del compilador de Eclipse:
     * <pre>
     * ----------
     * 1. ERROR in /ruta/Fichero.java (at line 12)
     *     mensaje
     * ----------
     * </pre>
     */
    private static final Pattern PROBLEM_PATTERN = Pattern.compile(
            "(\\d+)\\. (ERROR|WARNING) in ([^\\n]*?) \\(at line (\\d+)\\)\\s*\\n\\s*([^\\n]*)");

    /**
     * Mensajes de ECJ para tipos que no encuentra, p. ej. "Button cannot be resolved to a type".
     * De ahi sacamos el nombre simple que buscamos en el classpath para ofrecer el import.
     */
    private static final Pattern UNRESOLVED_TYPE_PATTERN = Pattern.compile(
            "^([A-Z][\\w$]*) cannot be resolved(?: to a type)?$");

    private static final int MAX_IMPORT_CANDIDATES = 3;

    private static final String TAG = "JavaDiagnostics";

    private JavaDiagnosticsAnalyzer() {
    }

    /**
     * Analiza el contenido indicado. Nunca lanza: en caso de problema devuelve lista vacia.
     *
     * @param fileName nombre del fichero (para que ECJ y los mensajes sean coherentes)
     */
    public static List<Problem> analyze(Context context, String scId, String fileName, String source) {
        if (context == null || scId == null || scId.isEmpty() || fileName == null || source == null) {
            return Collections.emptyList();
        }
        if (source.length() > 512 * 1024) {
            return Collections.emptyList();
        }

        File workDir = new File(context.getCacheDir(), "ide-diagnostics/" + scId);
        File outDir = new File(workDir, "classes");
        try {
            if (!outDir.exists() && !outDir.mkdirs()) {
                return Collections.emptyList();
            }
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        File tempSource = new File(workDir, fileName);
        try {
            FileUtil.writeFile(tempSource.getAbsolutePath(), source);
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        StringWriter outBuffer = new StringWriter();
        StringWriter errBuffer = new StringWriter();
        try (PrintWriter out = new PrintWriter(outBuffer); PrintWriter err = new PrintWriter(errBuffer)) {
            List<String> args = new ArrayList<>();
            args.add("-proc:none");
            args.add("-d");
            args.add(outDir.getAbsolutePath());
            args.add("-cp");
            args.add(classpath(scId));
            String sourcePath = new FilePathUtil().getPathJava(scId);
            if (FileUtil.isExistFile(sourcePath)) {
                args.add("-sourcepath");
                args.add(sourcePath);
            }
            args.add("-encoding");
            args.add("UTF-8");
            args.add(tempSource.getAbsolutePath());

            org.eclipse.jdt.internal.compiler.batch.Main main =
                    new org.eclipse.jdt.internal.compiler.batch.Main(out, err, false, null, null);
            main.compile(args.toArray(new String[0]));
            err.flush();
            out.flush();
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        return enrichWithImportCandidates(context, scId, parse(errBuffer.toString(), tempSource.getAbsolutePath()));
    }

    /** Anade, a los problemas de tipos no resueltos, las clases del classpath que los resolverian. */
    private static List<Problem> enrichWithImportCandidates(Context context, String scId, List<Problem> problems) {
        if (problems.isEmpty()) {
            return problems;
        }
        List<Problem> enriched = new ArrayList<>(problems.size());
        for (Problem problem : problems) {
            List<String> candidates = importCandidates(context, scId, problem.message);
            enriched.add(candidates.isEmpty()
                    ? problem
                    : new Problem(problem.line, problem.severity, problem.message, candidates));
        }
        return enriched;
    }

    private static List<String> importCandidates(Context context, String scId, String message) {
        if (context == null || message == null || message.isEmpty()) {
            return Collections.emptyList();
        }
        Matcher matcher = UNRESOLVED_TYPE_PATTERN.matcher(message.trim());
        if (!matcher.find()) {
            return Collections.emptyList();
        }
        String simpleName = matcher.group(1);
        String suffix = "." + simpleName;

        List<String> candidates = new ArrayList<>();
        try {
            collectCandidates(SdkSymbolIndex.getSdkClasses(context), suffix, candidates);
            if (candidates.size() < MAX_IMPORT_CANDIDATES) {
                collectCandidates(SdkSymbolIndex.getLibraryClasses(context, scId), suffix, candidates);
            }
        } catch (Throwable ignored) {
            // Sin indice: nos quedamos sin sugerencia de import.
        }
        return candidates;
    }

    private static void collectCandidates(List<String> classNames, String suffix, List<String> out) {
        for (String fullName : classNames) {
            if (out.size() >= MAX_IMPORT_CANDIDATES) {
                return;
            }
            if (fullName.endsWith(suffix) && !out.contains(fullName)) {
                out.add(fullName);
            }
        }
    }

    /** Classpath del proyecto: android.jar, lambda stubs y las librerias locales del proyecto. */
    private static String classpath(String scId) {
        StringBuilder classpath = new StringBuilder();

        File androidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        classpath.append(androidJar.getAbsolutePath());

        File lambdaStubs = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar");
        if (lambdaStubs.exists()) {
            classpath.append(File.pathSeparator).append(lambdaStubs.getAbsolutePath());
        }

        try {
            File localLibs = new File(new FilePathUtil().getJarPathLocalLibraryUser(scId));
            File[] jars = localLibs.isDirectory() ? localLibs.listFiles() : null;
            if (jars != null) {
                for (File jar : jars) {
                    if (jar.isFile() && jar.getName().endsWith(".jar")) {
                        classpath.append(File.pathSeparator).append(jar.getAbsolutePath());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Sin librerias locales: el diagnostico del proyecto sigue siendo util.
        }

        return classpath.toString();
    }

    private static List<Problem> parse(String compilerOutput, String tempFilePath) {
        if (compilerOutput == null || compilerOutput.isEmpty()) {
            return Collections.emptyList();
        }

        List<Problem> problems = new ArrayList<>();
        Matcher matcher = PROBLEM_PATTERN.matcher(compilerOutput);
        while (matcher.find()) {
            String file = matcher.group(3);
            // Solo nos interesa el fichero que estamos editando: ECJ puede reportar tambien
            // problemas de otras clases del proyecto resueltas por el sourcepath.
            if (file == null || !file.trim().equals(tempFilePath)) {
                continue;
            }
            int line;
            try {
                line = Integer.parseInt(matcher.group(4));
            } catch (NumberFormatException e) {
                continue;
            }
            short severity = "ERROR".contentEquals(matcher.group(2))
                    ? DiagnosticRegion.SEVERITY_ERROR
                    : DiagnosticRegion.SEVERITY_WARNING;
            String message = matcher.group(5) == null ? "" : matcher.group(5).trim();
            problems.add(new Problem(line, severity, message, Collections.emptyList()));
            if (problems.size() >= 200) {
                break;
            }
        }
        return problems;
    }
}
