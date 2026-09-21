package pro.sketchware.kmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class KmpProjectSerializerTest {

    @Test
    public void parse_validJson_returnsProject() {
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "sample-kmp",
                  "name": "Sample KMP",
                  "packageName": "pro.sketchware.sample.kmp",
                  "kotlinVersion": "2.0.21",
                  "composeMultiplatformVersion": "1.7.0",
                  "enabledTargets": ["ANDROID", "DESKTOP"],
                  "sourceSetHierarchy": {
                    "commonMain": [],
                    "androidMain": ["commonMain"],
                    "desktopMain": ["commonMain"]
                  }
                }
                """;

        KmpProjectParseResult result = KmpProjectSerializer.parse(json);

        assertNotNull(result.project);
        assertFalse(result.hasErrors());
        assertEquals("sample-kmp", result.project.id);
        assertEquals(2, result.project.enabledTargets.size());
    }

    @Test
    public void parse_invalidJson_reportsError() {
        KmpProjectParseResult result = KmpProjectSerializer.parse("{bad json}");

        assertNull(result.project);
        assertTrue(result.hasErrors());
    }

    @Test
    public void parse_invalidHierarchy_reportsError() {
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "sample-kmp",
                  "name": "Sample KMP",
                  "packageName": "pro.sketchware.sample.kmp",
                  "enabledTargets": ["ANDROID"],
                  "sourceSetHierarchy": {
                    "commonMain": [],
                    "androidMain": ["missingMain"]
                  }
                }
                """;

        KmpProjectParseResult result = KmpProjectSerializer.parse(json);

        assertNotNull(result.project);
        assertTrue(result.hasErrors());
        assertTrue(containsCode(result, "MISSING_PARENT"));
    }

    @Test
    public void toJson_roundTrip_preservesCoreFields() {
        KmpProject project = new KmpProject(
                1,
                "round-trip",
                "RoundTrip",
                "pro.sketchware.roundtrip",
                "2.0.21",
                "1.7.0",
                Arrays.asList(KmpTarget.ANDROID, KmpTarget.DESKTOP),
                KmpProjectDefaults.defaultHierarchy(),
                100L,
                200L
        );

        String json = KmpProjectSerializer.toJson(project);
        KmpProjectParseResult result = KmpProjectSerializer.parse(json);

        assertNotNull(result.project);
        assertEquals(project.id, result.project.id);
        assertEquals(project.name, result.project.name);
        assertEquals(project.packageName, result.project.packageName);
        assertEquals(project.enabledTargets.size(), result.project.enabledTargets.size());
    }

    private static boolean containsCode(KmpProjectParseResult result, String code) {
        for (KmpProjectIssue issue : result.issues) {
            if (code.equals(issue.code)) {
                return true;
            }
        }
        return false;
    }
}
