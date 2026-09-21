package pro.sketchware.blocks.typing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TypedBlockTypeCheckResult {

    public final int analyzedBlockCount;
    public final long checkedAtMs;
    public final List<TypedBlockTypeDiagnostic> diagnostics;

    public TypedBlockTypeCheckResult(int analyzedBlockCount,
                                     long checkedAtMs,
                                     List<TypedBlockTypeDiagnostic> diagnostics) {
        this.analyzedBlockCount = Math.max(analyzedBlockCount, 0);
        this.checkedAtMs = Math.max(checkedAtMs, 0L);
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(
                diagnostics == null ? Collections.emptyList() : diagnostics
        ));
    }

    public boolean hasErrors() {
        for (TypedBlockTypeDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity.isError()) {
                return true;
            }
        }
        return false;
    }

    public int errorCount() {
        int count = 0;
        for (TypedBlockTypeDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity.isError()) {
                count++;
            }
        }
        return count;
    }

    public int warningCount() {
        int count = 0;
        for (TypedBlockTypeDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && !diagnostic.severity.isError()) {
                count++;
            }
        }
        return count;
    }

    public static TypedBlockTypeCheckResult empty() {
        return new TypedBlockTypeCheckResult(0, System.currentTimeMillis(), Collections.emptyList());
    }
}
