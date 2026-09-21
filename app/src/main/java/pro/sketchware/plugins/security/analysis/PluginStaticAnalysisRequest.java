package pro.sketchware.plugins.security.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import pro.sketchware.plugins.runtime.PluginClassLoaderConfig;

public final class PluginStaticAnalysisRequest {

    public final String pluginId;
    public final String manifestJson;
    public final byte[] pluginPayload;
    public final PluginClassLoaderConfig classLoaderConfig;
    public final Map<String, String> metadata;

    public PluginStaticAnalysisRequest(String pluginId,
                                       String manifestJson,
                                       byte[] pluginPayload,
                                       PluginClassLoaderConfig classLoaderConfig,
                                       Map<String, String> metadata) {
        this.pluginId = pluginId == null ? "" : pluginId.trim();
        this.manifestJson = manifestJson == null ? "" : manifestJson;
        this.pluginPayload = pluginPayload == null ? new byte[0] : pluginPayload.clone();
        this.classLoaderConfig = classLoaderConfig == null
                ? new PluginClassLoaderConfig("", "", "", "", "")
                : classLoaderConfig;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(
                metadata == null ? Collections.emptyMap() : metadata
        ));
    }
}
