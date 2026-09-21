package pro.sketchware.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccessibilityCheckResult {

    public final String checkerId;
    public final long durationMs;
    public final List<AccessibilityIssue> issues;
    public final boolean success;
    public final String errorMessage;

    private AccessibilityCheckResult(String checkerId,
                                     long durationMs,
                                     List<AccessibilityIssue> issues,
                                     boolean success,
                                     String errorMessage) {
        this.checkerId = checkerId == null ? "" : checkerId;
        this.durationMs = Math.max(durationMs, 0L);
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues == null ? Collections.emptyList() : issues));
        this.success = success;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static AccessibilityCheckResult success(String checkerId, long durationMs, List<AccessibilityIssue> issues) {
        return new AccessibilityCheckResult(checkerId, durationMs, issues, true, "");
    }

    public static AccessibilityCheckResult failure(String checkerId, long durationMs, String errorMessage) {
        return new AccessibilityCheckResult(checkerId, durationMs, Collections.emptyList(), false, errorMessage);
    }
}
