package pro.sketchware.accessibility;

import java.util.List;

public interface AccessibilityChecker {
    String id();

    List<AccessibilityIssue> check(AccessibilityCheckRequest request) throws Exception;
}
