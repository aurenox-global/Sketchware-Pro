package pro.sketchware.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AccessibilityChecksEngineTest {

    @Test
    public void registerChecker_replacesDuplicateIdAndSortsIds() {
        AccessibilityChecksEngine engine = new AccessibilityChecksEngine();

        engine.registerChecker(checker("zeta", Collections.emptyList()));
        engine.registerChecker(checker("alpha", Collections.emptyList()));
        engine.registerChecker(checker("alpha", Collections.singletonList(
                new AccessibilityIssue("alpha", "new", AccessibilityIssueSeverity.INFO, "n", "msg")
        )));

        assertEquals(Arrays.asList("alpha", "zeta"), engine.checkerIds());

        AccessibilityIssueReport report = engine.run(new AccessibilityCheckRequest("screen", Collections.emptyList()));
        assertEquals(1, report.issues.size());
        assertEquals("new", report.issues.get(0).code);
    }

    @Test
    public void run_containsFailuresAndContinues() {
        AccessibilityChecksEngine engine = new AccessibilityChecksEngine();

        engine.registerChecker(checker("ok", Collections.singletonList(
                new AccessibilityIssue("ok", "issue.ok", AccessibilityIssueSeverity.WARNING, "a", "ok")
        )));
        engine.registerChecker(new AccessibilityChecker() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public List<AccessibilityIssue> check(AccessibilityCheckRequest request) {
                throw new IllegalStateException("boom");
            }
        });

        AccessibilityIssueReport report = engine.run(new AccessibilityCheckRequest("main", Collections.emptyList()));

        assertEquals(2, report.results.size());
        assertEquals(2, report.issues.size());
        assertTrue(report.issues.stream().anyMatch(issue -> "issue.ok".equals(issue.code)));
        assertTrue(report.issues.stream().anyMatch(issue -> "checker.execution_failed".equals(issue.code)));
        assertTrue(report.results.stream().anyMatch(result -> !result.success));
        assertFalse(report.results.stream().allMatch(result -> result.success));
    }

    private static AccessibilityChecker checker(String id, List<AccessibilityIssue> issues) {
        return new AccessibilityChecker() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<AccessibilityIssue> check(AccessibilityCheckRequest request) {
                return issues;
            }
        };
    }
}
