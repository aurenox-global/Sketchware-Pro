package pro.sketchware.blocks.generator.components.printers;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockToSourceMvpPrinter {

    public String print(List<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        Map<Integer, BlockBean> byId = new HashMap<>();
        Set<Integer> referenced = new HashSet<>();

        for (BlockBean block : blocks) {
            Integer id = parseId(block == null ? null : block.id);
            if (id == null || block == null) {
                continue;
            }
            byId.put(id, block);

            if (block.nextBlock >= 0) {
                referenced.add(block.nextBlock);
            }
            if (block.subStack1 >= 0) {
                referenced.add(block.subStack1);
            }
            if (block.subStack2 >= 0) {
                referenced.add(block.subStack2);
            }
        }

        List<Integer> roots = new ArrayList<>();
        for (Integer id : byId.keySet()) {
            if (!referenced.contains(id)) {
                roots.add(id);
            }
        }

        if (roots.isEmpty()) {
            roots.addAll(byId.keySet());
        }

        sortIds(roots);

        StringBuilder source = new StringBuilder();
        Set<Integer> visited = new HashSet<>();
        for (Integer root : roots) {
            appendChain(root, 0, byId, visited, source);
        }

        return source.toString().trim();
    }

    private void appendChain(int startId,
                             int indent,
                             Map<Integer, BlockBean> byId,
                             Set<Integer> visited,
                             StringBuilder out) {
        Integer current = startId;

        while (current != null && current >= 0) {
            if (visited.contains(current)) {
                return;
            }

            BlockBean block = byId.get(current);
            if (block == null) {
                return;
            }

            visited.add(current);
            appendBlock(block, indent, byId, visited, out);

            current = block.nextBlock >= 0 ? block.nextBlock : null;
        }
    }

    private void appendBlock(BlockBean block,
                             int indent,
                             Map<Integer, BlockBean> byId,
                             Set<Integer> visited,
                             StringBuilder out) {
        String opcode = safe(block.opCode);

        switch (opcode) {
            case "mvp_if" -> {
                line(out, indent, "if (/* " + sanitizeComment(block.spec) + " */ true) {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "}");
                if (block.subStack2 >= 0) {
                    line(out, indent, "else {");
                    appendChain(block.subStack2, indent + 1, byId, visited, out);
                    line(out, indent, "}");
                }
            }
            case "mvp_while" -> {
                line(out, indent, "while (/* " + sanitizeComment(block.spec) + " */ true) {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "}");
            }
            case "mvp_do_while" -> {
                line(out, indent, "do {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "} while (/* " + sanitizeComment(block.spec) + " */ true);");
            }
            case "mvp_for" -> {
                line(out, indent, "for (/* " + sanitizeComment(block.spec) + " */; ; ) {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "}");
            }
            case "mvp_for_each" -> {
                line(out, indent, "for (Object item : java.util.Collections.emptyList()) {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "}");
            }
            case "mvp_switch" -> {
                line(out, indent, "switch (0) {");
                line(out, indent + 1, "case 0:");
                appendChain(block.subStack1, indent + 2, byId, visited, out);
                line(out, indent + 2, "break;");
                line(out, indent, "}");
            }
            case "mvp_try" -> {
                line(out, indent, "try {");
                appendChain(block.subStack1, indent + 1, byId, visited, out);
                line(out, indent, "} catch (Exception e) {");
                appendChain(block.subStack2, indent + 1, byId, visited, out);
                line(out, indent, "}");
            }
            case "mvp_return" -> line(out, indent, "return;");
            case "mvp_break" -> line(out, indent, "break;");
            case "mvp_continue" -> line(out, indent, "continue;");
            case "mvp_assign", "mvp_declare", "mvp_call", "mvp_expression" ->
                    line(out, indent, normalizeAsStatement(block.spec));
            case "mvp_unsupported" ->
                    line(out, indent, "/* unsupported: " + sanitizeComment(block.spec) + " */");
            default -> line(out, indent, "/* " + sanitizeComment(block.spec) + " */");
        }
    }

    private static void line(StringBuilder out, int indent, String text) {
        out.append("    ".repeat(Math.max(indent, 0))).append(text).append('\n');
    }

    private static String normalizeAsStatement(String raw) {
        String text = safe(raw);
        if (text.isEmpty()) {
            return ";";
        }
        if (text.endsWith(";")) {
            return text;
        }
        return text + ";";
    }

    private static String sanitizeComment(String value) {
        return safe(value).replace("/*", "").replace("*/", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static Integer parseId(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void sortIds(Collection<Integer> ids) {
        if (ids instanceof List<Integer> list) {
            list.sort(Comparator.naturalOrder());
        } else {
            List<Integer> ordered = new ArrayList<>(ids);
            ordered.sort(Comparator.naturalOrder());
            ids.clear();
            ids.addAll(ordered);
        }
    }
}
