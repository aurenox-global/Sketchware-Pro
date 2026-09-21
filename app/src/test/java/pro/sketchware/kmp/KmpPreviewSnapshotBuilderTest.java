package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KmpPreviewSnapshotBuilderTest {

    @Test
    public void build_generatesSharedSnapshotForAndroidAndDesktop() {
        KmpPreviewSnapshot snapshot = KmpPreviewSnapshotBuilder.build(
                "onClick",
                "MainActivity.java",
                12,
                "ANDROID"
        );

        assertTrue(snapshot.sharedSnapshot.contains("event=onClick"));
        assertTrue(snapshot.sharedSnapshot.contains("blocks=12"));
        assertTrue(snapshot.androidPreview.contains(snapshot.sharedSnapshot));
        assertTrue(snapshot.desktopPreview.contains(snapshot.sharedSnapshot));
        assertTrue(snapshot.webPlaceholder.contains("placeholder"));
        assertTrue(snapshot.iosPlaceholder.contains("placeholder"));
    }

    @Test
    public void build_normalizesMissingInputs() {
        KmpPreviewSnapshot snapshot = KmpPreviewSnapshotBuilder.build(
                "",
                null,
                -1,
                null
        );

        assertEquals("event=unknown_event source=unknown_source blocks=0 target=AUTO", snapshot.sharedSnapshot);
    }
}