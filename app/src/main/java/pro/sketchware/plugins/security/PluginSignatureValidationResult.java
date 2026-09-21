package pro.sketchware.plugins.security;

public final class PluginSignatureValidationResult {

    public final PluginSignatureValidationStatus status;
    public final String algorithm;
    public final String message;

    public PluginSignatureValidationResult(PluginSignatureValidationStatus status,
                                           String algorithm,
                                           String message) {
        this.status = status == null ? PluginSignatureValidationStatus.SKIPPED : status;
        this.algorithm = algorithm == null ? "" : algorithm;
        this.message = message == null ? "" : message;
    }

    public static PluginSignatureValidationResult valid(String algorithm) {
        return new PluginSignatureValidationResult(
                PluginSignatureValidationStatus.VALID,
                algorithm,
                "Signature validation succeeded"
        );
    }

    public static PluginSignatureValidationResult invalid(String algorithm, String message) {
        return new PluginSignatureValidationResult(
                PluginSignatureValidationStatus.INVALID,
                algorithm,
                message
        );
    }

    public static PluginSignatureValidationResult skipped(String message) {
        return new PluginSignatureValidationResult(
                PluginSignatureValidationStatus.SKIPPED,
                "",
                message
        );
    }
}
