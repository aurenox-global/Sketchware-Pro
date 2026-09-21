package pro.sketchware.accessibility.checkers;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.accessibility.AccessibilityCheckRequest;
import pro.sketchware.accessibility.AccessibilityChecker;
import pro.sketchware.accessibility.AccessibilityIssue;
import pro.sketchware.accessibility.AccessibilityIssueSeverity;
import pro.sketchware.accessibility.AccessibilityNodeSnapshot;

public final class MissingLabelAccessibilityChecker implements AccessibilityChecker {

    @Override
    public String id() {
        return "missing_label";
    }

    @Override
    public List<AccessibilityIssue> check(AccessibilityCheckRequest request) {
        ArrayList<AccessibilityIssue> issues = new ArrayList<>();
        if (request == null || request.nodes.isEmpty()) {
            return issues;
        }

        for (AccessibilityNodeSnapshot node : request.nodes) {
            if (node == null) {
                continue;
            }

            if (!node.importantForAccessibility || !node.enabled) {
                continue;
            }

            boolean missingLabel = node.label.isEmpty() && node.contentDescription.isEmpty();
            if (missingLabel) {
                issues.add(new AccessibilityIssue(
                        id(),
                        "node.missing_label",
                        AccessibilityIssueSeverity.WARNING,
                        node.id,
                        "Important accessibility node is missing visible label and content description"
                ));
            }
        }

        return issues;
    }
}
