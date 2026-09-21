package pro.sketchware.accessibility.checkers;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.accessibility.AccessibilityCheckRequest;
import pro.sketchware.accessibility.AccessibilityChecker;
import pro.sketchware.accessibility.AccessibilityIssue;
import pro.sketchware.accessibility.AccessibilityIssueSeverity;
import pro.sketchware.accessibility.AccessibilityNodeSnapshot;

public final class TouchTargetAccessibilityChecker implements AccessibilityChecker {

    public static final int MIN_TOUCH_TARGET_DP = 48;

    @Override
    public String id() {
        return "touch_target";
    }

    @Override
    public List<AccessibilityIssue> check(AccessibilityCheckRequest request) {
        ArrayList<AccessibilityIssue> issues = new ArrayList<>();
        if (request == null || request.nodes.isEmpty()) {
            return issues;
        }

        for (AccessibilityNodeSnapshot node : request.nodes) {
            if (node == null || !node.clickable || !node.enabled) {
                continue;
            }

            if (node.widthDp < MIN_TOUCH_TARGET_DP || node.heightDp < MIN_TOUCH_TARGET_DP) {
                issues.add(new AccessibilityIssue(
                        id(),
                        "node.small_touch_target",
                        AccessibilityIssueSeverity.WARNING,
                        node.id,
                        "Clickable node touch target is below 48dp"
                ));
            }
        }

        return issues;
    }
}
