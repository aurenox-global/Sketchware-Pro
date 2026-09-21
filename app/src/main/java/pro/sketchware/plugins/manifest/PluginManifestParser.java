package pro.sketchware.plugins.manifest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PluginManifestParser {

    private static final Pattern PLUGIN_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_.-]{2,127}$");
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+][a-zA-Z0-9.-]+)?$");
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z\\d_$]*(?:\\.[a-zA-Z_$][a-zA-Z\\d_$]*)+$");

    public PluginManifestValidationResult parse(String manifestJson) {
        ArrayList<PluginManifestIssue> issues = new ArrayList<>();
        String source = manifestJson == null ? "" : manifestJson.trim();

        if (source.isEmpty()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.INVALID_JSON,
                    PluginManifestIssueSeverity.ERROR,
                    "$",
                    "Manifest content is empty"
            ));
            return new PluginManifestValidationResult(null, System.currentTimeMillis(), issues);
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(source);
        } catch (Exception e) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.INVALID_JSON,
                    PluginManifestIssueSeverity.ERROR,
                    "$",
                    "Invalid JSON: " + safeMessage(e)
            ));
            return new PluginManifestValidationResult(null, System.currentTimeMillis(), issues);
        }

        if (!root.isJsonObject()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.ROOT_NOT_OBJECT,
                    PluginManifestIssueSeverity.ERROR,
                    "$",
                    "Manifest root must be a JSON object"
            ));
            return new PluginManifestValidationResult(null, System.currentTimeMillis(), issues);
        }

        JsonObject jsonObject = root.getAsJsonObject();

        int schemaVersion = readInt(jsonObject, "schemaVersion", PluginManifest.SUPPORTED_SCHEMA_VERSION);
        if (schemaVersion != PluginManifest.SUPPORTED_SCHEMA_VERSION) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.UNSUPPORTED_SCHEMA_VERSION,
                    PluginManifestIssueSeverity.ERROR,
                    "schemaVersion",
                    "Unsupported schema version: " + schemaVersion
            ));
        }

        String pluginId = readRequiredString(jsonObject, "pluginId", issues);
        String name = readRequiredString(jsonObject, "name", issues);
        String version = readRequiredString(jsonObject, "version", issues);
        String entryClass = readRequiredString(jsonObject, "entryClass", issues);

        validateRegex(pluginId, PLUGIN_ID_PATTERN, "pluginId", "Invalid plugin id format", issues);
        validateRegex(version, SEMVER_PATTERN, "version", "Version must follow semantic versioning (x.y.z)", issues);
        validateRegex(entryClass, CLASS_PATTERN, "entryClass", "Entry class must be a fully qualified class name", issues);

        String minHostVersion = readOptionalString(jsonObject, "minHostVersion");
        String maxHostVersion = readOptionalString(jsonObject, "maxHostVersion");

        List<PluginManifestPermission> permissions = readPermissions(jsonObject, issues);

        JsonObject signature = jsonObject.has("signature") && jsonObject.get("signature").isJsonObject()
                ? jsonObject.getAsJsonObject("signature")
                : null;
        String signatureAlgorithm = signature == null ? "" : readOptionalString(signature, "algorithm");
        String signatureValue = signature == null ? "" : readOptionalString(signature, "value");

        if (signatureAlgorithm.isEmpty() || signatureValue.isEmpty()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.SIGNATURE_MISSING,
                    PluginManifestIssueSeverity.WARNING,
                    "signature",
                    "Signature fields are missing or incomplete"
            ));
        }

        PluginManifest manifest = new PluginManifest(
                schemaVersion,
                pluginId,
                name,
                version,
                entryClass,
                minHostVersion,
                maxHostVersion,
                permissions,
                signatureAlgorithm,
                signatureValue
        );

        return new PluginManifestValidationResult(manifest, System.currentTimeMillis(), issues);
    }

    private static List<PluginManifestPermission> readPermissions(JsonObject object,
                                                                  List<PluginManifestIssue> issues) {
        ArrayList<PluginManifestPermission> permissions = new ArrayList<>();
        JsonArray array = object.has("permissions") && object.get("permissions").isJsonArray()
                ? object.getAsJsonArray("permissions")
                : null;

        if (array == null) {
            return permissions;
        }

        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                issues.add(new PluginManifestIssue(
                        PluginManifestIssueCode.INVALID_FIELD_VALUE,
                        PluginManifestIssueSeverity.ERROR,
                        "permissions[" + i + "]",
                        "Permission values must be strings"
                ));
                continue;
            }

            String permissionId = item.getAsString().trim();
            PluginManifestPermission permission = PluginManifestPermission.fromId(permissionId);
            if (permission == null) {
                issues.add(new PluginManifestIssue(
                        PluginManifestIssueCode.UNKNOWN_PERMISSION,
                        PluginManifestIssueSeverity.WARNING,
                        "permissions[" + i + "]",
                        "Unknown permission: " + permissionId
                ));
                continue;
            }
            permissions.add(permission);
        }

        return permissions;
    }

    private static void validateRegex(String value,
                                      Pattern pattern,
                                      String fieldPath,
                                      String message,
                                      List<PluginManifestIssue> issues) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!pattern.matcher(value).matches()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.INVALID_FIELD_VALUE,
                    PluginManifestIssueSeverity.ERROR,
                    fieldPath,
                    message
            ));
        }
    }

    private static String readRequiredString(JsonObject object,
                                             String key,
                                             List<PluginManifestIssue> issues) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.MISSING_REQUIRED_FIELD,
                    PluginManifestIssueSeverity.ERROR,
                    key,
                    "Missing required field: " + key
            ));
            return "";
        }

        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            issues.add(new PluginManifestIssue(
                    PluginManifestIssueCode.INVALID_FIELD_VALUE,
                    PluginManifestIssueSeverity.ERROR,
                    key,
                    "Field must be a string: " + key
            ));
            return "";
        }

        return element.getAsString().trim();
    }

    private static String readOptionalString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return "";
        }
        return element.getAsString().trim();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "unknown error";
        }
        return throwable.getMessage();
    }
}
