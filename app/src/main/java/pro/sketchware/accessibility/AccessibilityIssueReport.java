package pro.sketchware.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccessibilityIssueReport {

    public final String screenId;
    public final long startedAtMs;
    public final long finishedAtMs;
    public final List<AccessibilityCheckResult> results;
    public final List<AccessibilityIssue> issues;

    public AccessibilityIssueReport(String screenId,
                                    long startedAtMs,
                                    long finishedAtMs,
                                    List<AccessibilityCheckResult> results,
                                    List<AccessibilityIssue> issues) {
        this.screenId = screenId == null ? "" : screenId;
        this.startedAtMs = Math.max(startedAtMs, 0L);
        this.finishedAtMs = Math.max(finishedAtMs, startedAtMs);
        this.results = Collections.unmodifiableList(new ArrayList<>(results == null ? Collections.emptyList() : results));
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues == null ? Collections.emptyList() : issues));
    }
}
