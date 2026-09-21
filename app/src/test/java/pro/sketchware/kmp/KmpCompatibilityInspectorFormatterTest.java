package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KmpCompatibilityInspectorFormatterTest {

    @Test
    public void build_withWarnings_aggregatesMessagesAndQuickFixes() {
        KmpCompatibilityInspectorSummary summary = KmpCompatibilityInspectorFormatter.build(
                "Desktop",
                Arrays.asList(
                        "Block A is not supported on DESKTOP.",
                        "Block A is not supported on DESKTOP.",
                        "Block B is not supported on DESKTOP."
                )
        );

        assertEquals(3, summary.warningCount);
        assertEquals(2, summary.uniqueWarningCount);
        assertEquals(2, summary.quickFixes.size());
        assertTrue(summary.message.contains("(x2)"));
        assertTrue(summary.message.contains("Switch target context"));
        assertTrue(summary.message.contains("Open multi-target preview"));
    }

    @Test
    public void build_withoutWarnings_reportsCleanState() {
        KmpCompatibilityInspectorSummary summary = KmpCompatibilityInspectorFormatter.build(
                "Android",
                Collections.emptyList()
        );

        assertEquals(0, summary.warningCount);
        assertEquals(0, summary.uniqueWarningCount);
        assertTrue(summary.message.contains("No compatibility warnings"));
    }
}