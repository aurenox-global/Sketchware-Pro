package pro.sketchware.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.code.ProjectSymbolIndex;
import pro.sketchware.utility.FileUtil;

/**
 * Navegacion real dentro del proyecto: resuelve la definicion de un simbolo y busca sus usos
 * apoyandose en el indice de simbolos del proyecto ( {@link ProjectSymbolIndex} ), que ya se usa
 * para el autocompletado.
 *
 * <p>A diferencia de {@link LocalSymbolNavigationProvider}, que solo mira el fichero abierto, este
 * proveedor busca en <b>todas las fuentes del proyecto</b>.
 *
 * <p>Es tolerante a fallos: si un fichero no se puede leer se ignora, y si algo va mal devuelve
 * lista vacia (el coordinador seguira con el proveedor de reserva).
 */
public final class ProjectSymbolNavigationProvider implements LspNavigationProvider {

    private static final int MAX_REFERENCES = 256;
    private static final int MAX_DEFINITIONS = 20;
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_PREVIEW_LENGTH = 200;

    @Override
    public String id() {
        return "project-symbol-index";
    }

    @Override
    public List<LspNavigationLocation> findDefinition(LspSessionConfig config, LspNavigationRequest request) {
        String projectId = config == null ? "" : config.projectId;
        String symbol = resolveSymbol(request);
        if (projectId.isEmpty() || symbol.isEmpty()) {
            return Collections.emptyList();
        }

        List<LspNavigationLocation> locations = new ArrayList<>();
        for (ProjectSymbolIndex.Symbol declaration : ProjectSymbolIndex.getSymbols(projectId)) {
            if (!symbol.equals(declaration.name)) {
                continue;
            }
            LspNavigationLocation location = locationFor(declaration.filePath, declaration.line, symbol,
                    declaration.detail);
            if (location != null) {
                locations.add(location);
            }
            if (locations.size() >= MAX_DEFINITIONS) {
                break;
            }
        }
        return locations;
    }

    @Override
    public List<LspNavigationLocation> findReferences(LspSessionConfig config, LspNavigationRequest request) {
        String projectId = config == null ? "" : config.projectId;
        String symbol = resolveSymbol(request);
        if (projectId.isEmpty() || symbol.isEmpty()) {
            return Collections.emptyList();
        }

        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");
        List<LspNavigationLocation> locations = new ArrayList<>();

        for (String filePath : ProjectSymbolIndex.getSourceFiles(projectId)) {
            if (locations.size() >= MAX_REFERENCES) {
                break;
            }
            String text;
            try {
                if (FileUtil.getFileLength(filePath) > MAX_FILE_BYTES) {
                    continue;
                }
                text = FileUtil.readFile(filePath);
            } catch (Throwable t) {
                continue;
            }
            if (text == null || text.isEmpty()) {
                continue;
            }

            String[] lines = text.split("\n", -1);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                Matcher matcher = pattern.matcher(lines[lineIndex]);
                while (matcher.find()) {
                    locations.add(new LspNavigationLocation(filePath,
                            new LspRange(lineIndex, matcher.start(), lineIndex, matcher.end()),
                            preview(lines[lineIndex])));
                    if (locations.size() >= MAX_REFERENCES) {
                        return locations;
                    }
                }
            }
        }
        return locations;
    }

    /** Localizacion precisa del simbolo dentro de la linea de su declaracion. */
    private static LspNavigationLocation locationFor(String filePath, int line, String symbol, String detail) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        String lineText = readLine(filePath, line);
        int start = lineText.indexOf(symbol);
        if (start < 0) {
            start = 0;
        }
        int end = start + symbol.length();
        return new LspNavigationLocation(filePath,
                new LspRange(line, start, line, end),
                lineText.isEmpty() ? detail : preview(lineText));
    }

    private static String readLine(String filePath, int line) {
        try {
            if (FileUtil.getFileLength(filePath) > MAX_FILE_BYTES) {
                return "";
            }
            String text = FileUtil.readFile(filePath);
            if (text == null || text.isEmpty()) {
                return "";
            }
            String[] lines = text.split("\n", -1);
            if (line < 0 || line >= lines.length) {
                return "";
            }
            return lines[line];
        } catch (Throwable t) {
            return "";
        }
    }

    private static String preview(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        return trimmed.length() <= MAX_PREVIEW_LENGTH ? trimmed : trimmed.substring(0, MAX_PREVIEW_LENGTH);
    }

    /** Simbolo bajo el cursor: del propio peticion o resolviendolo desde la posicion indicada. */
    private static String resolveSymbol(LspNavigationRequest request) {
        if (request == null) {
            return "";
        }
        if (request.symbol != null && !request.symbol.trim().isEmpty()) {
            return request.symbol.trim();
        }

        String text = request.documentText;
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        if (request.line < 0 || request.line >= lines.length) {
            return "";
        }
        String line = lines[request.line];
        if (line.isEmpty()) {
            return "";
        }

        int position = Math.max(0, Math.min(request.character, line.length() - 1));
        int start = position;
        int end = position;
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
