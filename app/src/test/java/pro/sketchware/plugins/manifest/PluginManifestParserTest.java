package pro.sketchware.plugins.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PluginManifestParserTest {

    @Test
    public void parse_validManifest_returnsNoErrors() {
        String json = """
                {
                  "schemaVersion": 1,
                  "pluginId": "sample.plugin.core",
                  "name": "Sample Plugin",
                  "version": "1.0.0",
                  "entryClass": "sample.plugin.core.MainPlugin",
                  "permissions": ["project.read", "editor.write"],
                  "signature": {
                    "algorithm": "SHA256withRSA",
                    "value": "deadbeef"
                  }
                }
                """;

        PluginManifestValidationResult result = new PluginManifestParser().parse(json);
        assertNotNull(result.manifest);
        assertFalse(result.hasErrors());
        assertEquals("sample.plugin.core", result.manifest.pluginId);
        assertEquals("1.0.0", result.manifest.version);
        assertTrue(result.manifest.hasSignature());
    }

    @Test
    public void parse_invalidManifest_reportsErrors() {
        String json = """
                {
                  "schemaVersion": 2,
                  "pluginId": "bad",
                  "name": "",
                  "version": "v1",
                  "entryClass": "Main",
                  "permissions": [12]
                }
                """;

        PluginManifestValidationResult result = new PluginManifestParser().parse(json);
        assertNotNull(result.manifest);
        assertTrue(result.hasErrors());
        assertTrue(result.issues.size() >= 3);
    }
}
