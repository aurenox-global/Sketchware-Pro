package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpDependencyCompatibilityResolverTest {

    @Test
    public void resolve_withKnownAndUnknownDependencies_returnsDeterministicMatrix() {
        KmpDependencyCompatibilityResult result = KmpDependencyCompatibilityResolver.resolve(
                Arrays.asList(
                        "androidx.appcompat:appcompat:1.7.0",
                        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1",
                        "com.example:legacy-lib:0.1.0"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP, KmpTarget.WASM_JS)
        );

        assertEquals(3, result.entries.size());

        KmpDependencyCompatibilityEntry appcompat = result.findByNormalizedCoordinate("androidx.appcompat:appcompat");
        KmpDependencyCompatibilityEntry coroutines = result.findByNormalizedCoordinate("org.jetbrains.kotlinx:kotlinx-coroutines-core");
        KmpDependencyCompatibilityEntry unknown = result.findByNormalizedCoordinate("com.example:legacy-lib");

        assertNotNull(appcompat);
        assertNotNull(coroutines);
        assertNotNull(unknown);

        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, appcompat.status);
        assertTrue(appcompat.findTarget(KmpTarget.ANDROID).supported);
        assertFalse(appcompat.findTarget(KmpTarget.DESKTOP).supported);
        assertFalse(appcompat.findTarget(KmpTarget.WASM_JS).supported);
        assertEquals(2, appcompat.diagnostics.size());
        assertEquals("UNSUPPORTED_TARGET", appcompat.diagnostics.get(0).code);

        assertEquals(KmpDependencyCompatibilityStatus.COMPATIBLE, coroutines.status);
        assertEquals(0, coroutines.diagnostics.size());

        assertEquals(KmpDependencyCompatibilityStatus.UNKNOWN, unknown.status);
        assertEquals(1, unknown.diagnostics.size());
        assertEquals("UNKNOWN_DEPENDENCY", unknown.diagnostics.get(0).code);
    }

    @Test
    public void resolve_withDuplicateCoordinates_keepsFirstAndIsStable() {
        KmpDependencyCompatibilityResult result = KmpDependencyCompatibilityResolver.resolve(
                Arrays.asList(
                        "androidx.appcompat:appcompat:1.6.1",
                        "androidx.appcompat:appcompat:1.7.0",
                        "io.ktor:ktor-client-core:2.3.0"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP)
        );

        assertEquals(2, result.entries.size());
        assertEquals("androidx.appcompat:appcompat:1.6.1", result.entries.get(0).coordinate);
        assertEquals("androidx.appcompat:appcompat", result.entries.get(0).normalizedCoordinate);
        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, result.entries.get(0).status);
        assertEquals(KmpDependencyCompatibilityStatus.COMPATIBLE, result.entries.get(1).status);
    }

    @Test
    public void resolve_withAndroidOnlyLibraries_providesAlternativeHintsAndActionHook() {
        KmpDependencyCompatibilityResult result = KmpDependencyCompatibilityResolver.resolve(
                Arrays.asList(
                        "com.squareup.retrofit2:retrofit:2.11.0",
                        "androidx.room:room-runtime:2.6.1",
                        "com.google.code.gson:gson:2.11.0",
                        "io.reactivex.rxjava3:rxjava:3.1.9"
                ),
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.IOS_ARM64)
        );

        assertEquals(4, result.entries.size());

        KmpDependencyCompatibilityEntry retrofit = result.findByNormalizedCoordinate("com.squareup.retrofit2:retrofit");
        KmpDependencyCompatibilityEntry room = result.findByNormalizedCoordinate("androidx.room:room-runtime");
        KmpDependencyCompatibilityEntry gson = result.findByNormalizedCoordinate("com.google.code.gson:gson");
        KmpDependencyCompatibilityEntry rxjava = result.findByNormalizedCoordinate("io.reactivex.rxjava3:rxjava");

        assertNotNull(retrofit);
        assertNotNull(room);
        assertNotNull(gson);
        assertNotNull(rxjava);

        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, retrofit.status);
        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, room.status);
        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, gson.status);
        assertEquals(KmpDependencyCompatibilityStatus.PARTIALLY_COMPATIBLE, rxjava.status);

        assertTrue(retrofit.diagnostics.get(0).suggestion.contains("io.ktor:ktor-client-core"));
        assertTrue(room.diagnostics.get(0).suggestion.contains("app.cash.sqldelight:runtime"));
        assertTrue(gson.diagnostics.get(0).suggestion.contains("kotlinx-serialization-json"));
        assertTrue(rxjava.diagnostics.get(0).suggestion.contains("kotlinx-coroutines-core"));

        assertEquals(
                KmpDependencyAlternativeSuggestionCatalog.ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES,
                retrofit.diagnostics.get(0).actionId
        );
        assertEquals(
                KmpDependencyAlternativeSuggestionCatalog.ACTION_OPEN_KMP_DEPENDENCY_ALTERNATIVES,
                room.diagnostics.get(0).actionId
        );
    }
}