package pro.sketchware.plugins.runtime;

import java.util.UUID;

import pro.sketchware.plugins.manifest.PluginManifest;

public final class PluginClassLoaderConfig {

    public final String pluginId;
    public final String entryClassName;
    public final String classpath;
    public final String optimizedDirectory;
    public final String nativeLibraryDirectory;

    public PluginClassLoaderConfig(String pluginId,
                                   String entryClassName,
                                   String classpath,
                                   String optimizedDirectory,
                                   String nativeLibraryDirectory) {
        this.pluginId = pluginId == null || pluginId.trim().isEmpty()
                ? "plugin-" + UUID.randomUUID()
                : pluginId.trim();
        this.entryClassName = entryClassName == null ? "" : entryClassName.trim();
        this.classpath = classpath == null ? "" : classpath.trim();
        this.optimizedDirectory = optimizedDirectory == null ? "" : optimizedDirectory.trim();
        this.nativeLibraryDirectory = nativeLibraryDirectory == null ? "" : nativeLibraryDirectory.trim();
    }

    public static PluginClassLoaderConfig fromManifest(PluginManifest manifest,
                                                       String classpath,
                                                       String optimizedDirectory,
                                                       String nativeLibraryDirectory) {
        PluginManifest safeManifest = manifest == null
                ? new PluginManifest(PluginManifest.SUPPORTED_SCHEMA_VERSION, "", "", "", "", "", "", null, "", "")
                : manifest;
        return new PluginClassLoaderConfig(
                safeManifest.pluginId,
                safeManifest.entryClass,
                classpath,
                optimizedDirectory,
                nativeLibraryDirectory
        );
    }

    public boolean hasEntryClass() {
        return !entryClassName.isEmpty();
    }
}
