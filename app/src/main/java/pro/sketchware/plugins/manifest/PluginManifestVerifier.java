package pro.sketchware.plugins.manifest;

import pro.sketchware.plugins.security.NoOpPluginSignatureValidator;
import pro.sketchware.plugins.security.PluginSignatureValidationResult;
import pro.sketchware.plugins.security.PluginSignatureValidator;

public final class PluginManifestVerifier {

    private final PluginManifestParser parser;
    private final PluginSignatureValidator signatureValidator;

    public PluginManifestVerifier() {
        this(new PluginManifestParser(), new NoOpPluginSignatureValidator());
    }

    public PluginManifestVerifier(PluginManifestParser parser,
                                  PluginSignatureValidator signatureValidator) {
        this.parser = parser == null ? new PluginManifestParser() : parser;
        this.signatureValidator = signatureValidator == null
                ? new NoOpPluginSignatureValidator()
                : signatureValidator;
    }

    public PluginManifestVerificationResult verify(String manifestJson, byte[] pluginPayload) {
        PluginManifestValidationResult validationResult = parser.parse(manifestJson);
        if (validationResult.hasErrors() || validationResult.manifest == null) {
            return new PluginManifestVerificationResult(
                    validationResult,
                    PluginSignatureValidationResult.skipped("Manifest has validation errors")
            );
        }

        PluginSignatureValidationResult signatureResult;
        if (!validationResult.manifest.hasSignature()) {
            signatureResult = PluginSignatureValidationResult.skipped(
                    "Manifest has no complete signature block"
            );
        } else {
            try {
                signatureResult = signatureValidator.validate(validationResult.manifest, pluginPayload);
            } catch (Throwable throwable) {
                signatureResult = PluginSignatureValidationResult.invalid(
                        validationResult.manifest.signatureAlgorithm,
                        throwable.getMessage() == null
                                ? "Signature validation failed"
                                : throwable.getMessage()
                );
            }
        }

        return new PluginManifestVerificationResult(validationResult, signatureResult);
    }
}
