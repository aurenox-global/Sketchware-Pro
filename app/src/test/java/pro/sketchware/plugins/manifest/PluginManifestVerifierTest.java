package pro.sketchware.plugins.manifest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.plugins.security.PluginSignatureValidationResult;
import pro.sketchware.plugins.security.PluginSignatureValidator;

public class PluginManifestVerifierTest {

    @Test
    public void verify_withoutSignatureHook_acceptsWhenManifestIsValid() {
        PluginManifestVerificationResult result = new PluginManifestVerifier().verify(validManifest(), new byte[0]);
        assertTrue(result.isAccepted());
    }

    @Test
    public void verify_invalidSignature_rejectsManifest() {
        PluginSignatureValidator validator = (manifest, payload) ->
                PluginSignatureValidationResult.invalid(manifest.signatureAlgorithm, "bad signature");

        PluginManifestVerifier verifier = new PluginManifestVerifier(new PluginManifestParser(), validator);
        PluginManifestVerificationResult result = verifier.verify(validManifest(), new byte[0]);

        assertFalse(result.isAccepted());
    }

    private String validManifest() {
        return """
                {
                  "schemaVersion": 1,
                  "pluginId": "sample.plugin.core",
                  "name": "Sample Plugin",
                  "version": "1.0.0",
                  "entryClass": "sample.plugin.core.MainPlugin",
                  "permissions": ["project.read"],
                  "signature": {
                    "algorithm": "SHA256withRSA",
                    "value": "deadbeef"
                  }
                }
                """;
    }
}
