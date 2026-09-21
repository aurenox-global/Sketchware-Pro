package pro.sketchware.plugins.security;

import pro.sketchware.plugins.manifest.PluginManifest;

public final class NoOpPluginSignatureValidator implements PluginSignatureValidator {

    @Override
    public PluginSignatureValidationResult validate(PluginManifest manifest, byte[] pluginPayload) {
        return PluginSignatureValidationResult.skipped(
                "No signature validator configured"
        );
    }
}
