package pro.sketchware.accessibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import pro.sketchware.accessibility.checkers.MissingLabelAccessibilityChecker;
import pro.sketchware.accessibility.checkers.TextContrastAccessibilityChecker;
import pro.sketchware.accessibility.checkers.TouchTargetAccessibilityChecker;

public final class AccessibilityChecksEngine {

    private final CopyOnWriteArrayList<AccessibilityChecker> checkers = new CopyOnWriteArrayList<>();

    public static AccessibilityChecksEngine createDefault() {
        AccessibilityChecksEngine engine = new AccessibilityChecksEngine();
        engine.registerChecker(new MissingLabelAccessibilityChecker());
        engine.registerChecker(new TextContrastAccessibilityChecker());
        engine.registerChecker(new TouchTargetAccessibilityChecker());
        return engine;
    }

    public void registerChecker(AccessibilityChecker checker) {
        if (checker == null || checker.id() == null || checker.id().trim().isEmpty()) {
            return;
        }

        String checkerId = checker.id().trim();
        checkers.removeIf(existing -> checkerId.equals(existing.id()));
        checkers.add(checker);
        checkers.sort(Comparator.comparing(AccessibilityChecker::id));
    }

    public boolean removeChecker(String checkerId) {
        if (checkerId == null || checkerId.trim().isEmpty()) {
            return false;
        }

        String normalized = checkerId.trim();
        return checkers.removeIf(existing -> normalized.equals(existing.id()));
    }

    public void clearCheckers() {
        checkers.clear();
    }

    public List<String> checkerIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (AccessibilityChecker checker : checkers) {
            ids.add(checker.id());
        }
        return Collections.unmodifiableList(ids);
    }

    public AccessibilityIssueReport run(AccessibilityCheckRequest request) {
        long startedAt = System.currentTimeMillis();
        AccessibilityCheckRequest safeRequest = request == null
                ? new AccessibilityCheckRequest("", Collections.emptyList())
                : request;

        ArrayList<AccessibilityCheckResult> results = new ArrayList<>();
        ArrayList<AccessibilityIssue> allIssues = new ArrayList<>();

        for (AccessibilityChecker checker : checkers) {
            long checkerStartedAt = System.currentTimeMillis();
            try {
                List<AccessibilityIssue> issues = checker.check(safeRequest);
                List<AccessibilityIssue> safeIssues = issues == null ? Collections.emptyList() : issues;
                results.add(AccessibilityCheckResult.success(
                        checker.id(),
                        System.currentTimeMillis() - checkerStartedAt,
                        safeIssues
                ));
                allIssues.addAll(safeIssues);
            } catch (Throwable throwable) {
                AccessibilityIssue failureIssue = new AccessibilityIssue(
                        checker.id(),
                        "checker.execution_failed",
                        AccessibilityIssueSeverity.ERROR,
                        safeRequest.screenId,
                        throwable.getMessage() == null ? "Accessibility checker failed" : throwable.getMessage()
                );
                allIssues.add(failureIssue);
                results.add(AccessibilityCheckResult.failure(
                        checker.id(),
                        System.currentTimeMillis() - checkerStartedAt,
                        failureIssue.message
                ));
            }
        }

        return new AccessibilityIssueReport(
                safeRequest.screenId,
                startedAt,
                System.currentTimeMillis(),
                results,
                allIssues
        );
    }
}
