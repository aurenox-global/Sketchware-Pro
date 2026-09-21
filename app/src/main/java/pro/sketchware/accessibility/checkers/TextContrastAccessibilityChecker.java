package pro.sketchware.accessibility.checkers;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.accessibility.AccessibilityCheckRequest;
import pro.sketchware.accessibility.AccessibilityChecker;
import pro.sketchware.accessibility.AccessibilityIssue;
import pro.sketchware.accessibility.AccessibilityIssueSeverity;
import pro.sketchware.accessibility.AccessibilityNodeSnapshot;

public final class TextContrastAccessibilityChecker implements AccessibilityChecker {

    public static final double MIN_CONTRAST_RATIO = 4.5;

    @Override
    public String id() {
        return "text_contrast";
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

            if (node.textContrastRatio <= 0.0) {
                continue;
            }

            if (node.textContrastRatio < MIN_CONTRAST_RATIO) {
                issues.add(new AccessibilityIssue(
                        id(),
                        "node.low_text_contrast",
                        AccessibilityIssueSeverity.WARNING,
                        node.id,
                        "Text contrast ratio is below 4.5:1"
                ));
            }
        }

        return issues;
    }
}
