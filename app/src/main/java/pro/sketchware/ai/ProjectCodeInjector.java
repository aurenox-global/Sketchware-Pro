package pro.sketchware.ai;

import java.util.ArrayList;

import com.besome.sketch.beans.BlockBean;

import a.a.a.eC;
import a.a.a.jC;

public final class ProjectCodeInjector {

    public static final String INITIALIZE_LOGIC_EVENT = "initializeLogic_initializeLogic";

    private ProjectCodeInjector() {
    }

    public static boolean injectIntoInitializeLogic(String scId, String javaName, String code) {
        return injectIntoEvent(scId, javaName, INITIALIZE_LOGIC_EVENT, code);
    }

    public static boolean injectIntoEvent(String scId, String javaName, String eventKey, String code) {
        if (scId == null || scId.isEmpty() || eventKey == null || eventKey.isEmpty()
                || code == null || code.trim().isEmpty()) {
            return false;
        }
        try {
            eC projectData = jC.a(scId);
            ArrayList<BlockBean> blocks = projectData.a(javaName, eventKey);
            if (blocks == null) {
                blocks = new ArrayList<>();
            }
            BlockBean block = new BlockBean();
            // Block ids MUST be integers: LogicEditorActivity parses them with
            // Integer.parseInt when loading the event editor.
            block.id = String.valueOf(nextBlockId(blocks));
            block.opCode = "addSourceDirectly";
            // Official addSourceDirectly spec; a null spec crashes code generation
            // (Fx.extractParamsTypes NPEs on a null input).
            block.spec = "add source directly %s.inputOnly";
            block.type = " ";
            block.typeName = "";
            block.parameters = new ArrayList<>();
            block.parameters.add("// ===== Codigo aplicado desde Local AI =====\n" + code + "\n");
            blocks.add(block);
            projectData.a(javaName, eventKey, blocks);
            projectData.k();
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static long nextBlockId(ArrayList<BlockBean> blocks) {
        long max = 0L;
        for (BlockBean block : blocks) {
            if (block == null || block.id == null || block.id.isEmpty()) {
                continue;
            }
            try {
                long parsed = Long.parseLong(block.id);
                if (parsed > max) {
                    max = parsed;
                }
            } catch (NumberFormatException ignored) {
                android.util.Log.d("SketchwarePro", "ProjectCodeInjector: NumberFormatException ignored", ignored);
            }
        }
        return max + 1L;
    }

    public static String getMainJavaName() {
        return com.besome.sketch.beans.ProjectFileBean.getJavaName("main");
    }

    public static String buildProjectContext(String scId) {
        if (scId == null || scId.isEmpty()) {
            return "";
        }
        try {
            java.io.File filesDir = new java.io.File(
                    pro.sketchware.utility.FileUtil.getExternalStorageDir(),
                    ".sketchware/data/" + scId + "/files");
            if (!filesDir.isDirectory()) {
                return "";
            }
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            collectFiles(filesDir, filesDir, entries, new int[]{0});
            if (entries.isEmpty()) {
                return "";
            }
            return String.join("\n", entries) + "\n";
        } catch (Throwable throwable) {
            return "";
        }
    }

    private static void collectFiles(java.io.File root,
                                     java.io.File current,
                                     java.util.ArrayList<String> out,
                                     int[] count) {
        if (count[0] >= 15 || current == null || !current.exists()) {
            return;
        }
        if (current.isDirectory()) {
            java.io.File[] children = current.listFiles();
            if (children == null) {
                return;
            }
            for (java.io.File child : children) {
                collectFiles(root, child, out, count);
                if (count[0] >= 15) {
                    return;
                }
            }
            return;
        }
        String name = current.getName();
        if (name.endsWith(".java") || name.endsWith(".xml")) {
            String relative = current.getAbsolutePath().substring(root.getAbsolutePath().length())
                    .replace('\\', '/');
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            out.add("• " + relative + " (" + pro.sketchware.utility.FileUtil.formatFileSize(current.length()) + ")");
            count[0]++;
        }
    }
}
