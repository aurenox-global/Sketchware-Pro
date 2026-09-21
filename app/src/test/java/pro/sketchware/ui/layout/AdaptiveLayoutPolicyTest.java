package pro.sketchware.ui.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdaptiveLayoutPolicyTest {

    @Test
    public void widthClass_compactThresholds_workAsExpected() {
        assertEquals(AdaptiveLayoutWidthClass.COMPACT, AdaptiveLayoutPolicy.toWidthClass(0));
        assertEquals(AdaptiveLayoutWidthClass.COMPACT, AdaptiveLayoutPolicy.toWidthClass(599));
        assertEquals(AdaptiveLayoutWidthClass.MEDIUM, AdaptiveLayoutPolicy.toWidthClass(600));
    }

    @Test
    public void widthClass_mediumThresholds_workAsExpected() {
        assertEquals(AdaptiveLayoutWidthClass.MEDIUM, AdaptiveLayoutPolicy.toWidthClass(700));
        assertEquals(AdaptiveLayoutWidthClass.MEDIUM, AdaptiveLayoutPolicy.toWidthClass(839));
        assertEquals(AdaptiveLayoutWidthClass.EXPANDED, AdaptiveLayoutPolicy.toWidthClass(840));
    }

    @Test
    public void snapshot_expandedPrefersTwoPaneAndPadding() {
        AdaptiveLayoutSnapshot snapshot = AdaptiveLayoutPolicy.fromDimensionsDp(900, 600);
        assertEquals(AdaptiveLayoutWidthClass.EXPANDED, snapshot.widthClass);
        assertTrue(snapshot.preferTwoPane);
        assertEquals(32, snapshot.contentHorizontalPaddingDp);
        assertTrue(snapshot.recommendedMaxContentWidthDp > 0);
    }

    @Test
    public void snapshot_mediumAndCompact_doNotPreferTwoPane() {
        AdaptiveLayoutSnapshot medium = AdaptiveLayoutPolicy.fromDimensionsDp(700, 600);
        AdaptiveLayoutSnapshot compact = AdaptiveLayoutPolicy.fromDimensionsDp(400, 700);

        assertEquals(AdaptiveLayoutWidthClass.MEDIUM, medium.widthClass);
        assertFalse(medium.preferTwoPane);
        assertEquals(24, medium.contentHorizontalPaddingDp);

        assertEquals(AdaptiveLayoutWidthClass.COMPACT, compact.widthClass);
        assertFalse(compact.preferTwoPane);
        assertEquals(16, compact.contentHorizontalPaddingDp);
    }
}
