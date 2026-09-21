package pro.sketchware.plugins.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginManifest {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public final int schemaVersion;
    public final String pluginId;
    public final String name;
    public final String version;
    public final String entryClass;
    public final String minHostVersion;
    public final String maxHostVersion;
    public final List<PluginManifestPermission> permissions;
    public final String signatureAlgorithm;
    public final String signatureValue;

    public PluginManifest(int schemaVersion,
                          String pluginId,
                          String name,
                          String version,
                          String entryClass,
                          String minHostVersion,
                          String maxHostVersion,
                          List<PluginManifestPermission> permissions,
                          String signatureAlgorithm,
                          String signatureValue) {
        this.schemaVersion = schemaVersion;
        this.pluginId = pluginId == null ? "" : pluginId;
        this.name = name == null ? "" : name;
        this.version = version == null ? "" : version;
        this.entryClass = entryClass == null ? "" : entryClass;
        this.minHostVersion = minHostVersion == null ? "" : minHostVersion;
        this.maxHostVersion = maxHostVersion == null ? "" : maxHostVersion;
        this.permissions = Collections.unmodifiableList(new ArrayList<>(
                permissions == null ? Collections.emptyList() : permissions
        ));
        this.signatureAlgorithm = signatureAlgorithm == null ? "" : signatureAlgorithm;
        this.signatureValue = signatureValue == null ? "" : signatureValue;
    }

    public boolean hasSignature() {
        return !signatureAlgorithm.isEmpty() && !signatureValue.isEmpty();
    }
}
