package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KmpDependencyAlternativeSuggestionCatalogTest {

    @Test
    public void findSuggestion_returnsExpectedMappingsForCoreMigrationLibraries() {
        KmpDependencyAlternativeSuggestion retrofit = KmpDependencyAlternativeSuggestionCatalog.findSuggestion(
                "com.squareup.retrofit2:retrofit"
        );
        KmpDependencyAlternativeSuggestion room = KmpDependencyAlternativeSuggestionCatalog.findSuggestion(
                "androidx.room:room-runtime"
        );
        KmpDependencyAlternativeSuggestion gson = KmpDependencyAlternativeSuggestionCatalog.findSuggestion(
                "com.google.code.gson:gson"
        );
        KmpDependencyAlternativeSuggestion rxjava = KmpDependencyAlternativeSuggestionCatalog.findSuggestion(
                "io.reactivex.rxjava3:rxjava"
        );

        assertNotNull(retrofit);
        assertNotNull(room);
        assertNotNull(gson);
        assertNotNull(rxjava);

        assertTrue(retrofit.alternatives.contains("io.ktor:ktor-client-core"));
        assertTrue(room.alternatives.contains("app.cash.sqldelight:runtime"));
        assertTrue(gson.alternatives.contains("org.jetbrains.kotlinx:kotlinx-serialization-json"));
        assertTrue(rxjava.alternatives.contains("org.jetbrains.kotlinx:kotlinx-coroutines-core"));
        assertEquals(
                KmpDependencyAlternativeSuggestionCatalog.ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES,
                retrofit.actionId
        );
    }

    @Test
    public void buildSuggestionText_unknownCoordinate_returnsNull() {
        assertNull(KmpDependencyAlternativeSuggestionCatalog.buildSuggestionText("com.example:legacy"));
    }
}