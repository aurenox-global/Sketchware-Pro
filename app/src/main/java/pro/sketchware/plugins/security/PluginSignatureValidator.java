package pro.sketchware.plugins.security;

import pro.sketchware.plugins.manifest.PluginManifest;

public interface PluginSignatureValidator {

    PluginSignatureValidationResult validate(PluginManifest manifest, byte[] pluginPayload);
}
