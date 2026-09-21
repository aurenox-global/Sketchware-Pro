package pro.sketchware.plugins.manifest;

import pro.sketchware.plugins.security.PluginSignatureValidationResult;
import pro.sketchware.plugins.security.PluginSignatureValidationStatus;

public final class PluginManifestVerificationResult {

    public final PluginManifestValidationResult manifestValidation;
    public final PluginSignatureValidationResult signatureValidation;

    public PluginManifestVerificationResult(PluginManifestValidationResult manifestValidation,
                                            PluginSignatureValidationResult signatureValidation) {
        this.manifestValidation = manifestValidation == null
                ? new PluginManifestValidationResult(null, System.currentTimeMillis(), null)
                : manifestValidation;
        this.signatureValidation = signatureValidation == null
                ? PluginSignatureValidationResult.skipped("No signature validation result")
                : signatureValidation;
    }

    public boolean isAccepted() {
        if (manifestValidation.hasErrors()) {
            return false;
        }
        return signatureValidation.status != PluginSignatureValidationStatus.INVALID;
    }
}
