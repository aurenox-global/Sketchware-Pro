package pro.sketchware.accessibility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import pro.sketchware.accessibility.checkers.MissingLabelAccessibilityChecker;
import pro.sketchware.accessibility.checkers.TextContrastAccessibilityChecker;
import pro.sketchware.accessibility.checkers.TouchTargetAccessibilityChecker;

public class AccessibilityCheckersTest {

    @Test
    public void missingLabelChecker_reportsImportantUnlabeledNode() throws Exception {
        MissingLabelAccessibilityChecker checker = new MissingLabelAccessibilityChecker();

        AccessibilityNodeSnapshot unlabeled = new AccessibilityNodeSnapshot(
                "button_run",
                "",
                "",
                true,
                true,
                true,
                56,
                56,
                0.0
        );

        AccessibilityCheckRequest request = new AccessibilityCheckRequest("main", Collections.singletonList(unlabeled));
        assertEquals(1, checker.check(request).size());
    }

    @Test
    public void touchTargetChecker_reportsTooSmallClickableNode() throws Exception {
        TouchTargetAccessibilityChecker checker = new TouchTargetAccessibilityChecker();

        AccessibilityNodeSnapshot small = new AccessibilityNodeSnapshot(
                "icon_button",
                "Icon",
                "",
                true,
                true,
                true,
                32,
                40,
                0.0
        );

        AccessibilityCheckRequest request = new AccessibilityCheckRequest("main", Collections.singletonList(small));
        assertEquals(1, checker.check(request).size());
    }

    @Test
    public void contrastChecker_reportsLowContrastText() throws Exception {
        TextContrastAccessibilityChecker checker = new TextContrastAccessibilityChecker();

        AccessibilityNodeSnapshot lowContrastText = new AccessibilityNodeSnapshot(
                "title",
                "Title",
                "",
                false,
                true,
                true,
                120,
                20,
                3.2
        );

        AccessibilityNodeSnapshot goodContrastText = new AccessibilityNodeSnapshot(
                "subtitle",
                "Subtitle",
                "",
                false,
                true,
                true,
                120,
                20,
                7.0
        );

        AccessibilityCheckRequest request = new AccessibilityCheckRequest(
                "main",
                Arrays.asList(lowContrastText, goodContrastText)
        );
        assertEquals(1, checker.check(request).size());
    }
}
