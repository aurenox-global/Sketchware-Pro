package pro.sketchware.plugins.runtime;

public final class PluginClassLoaderSessionSnapshot {

    public final String pluginId;
    public final String entryClassName;
    public final PluginClassLoaderSessionState state;
    public final String loadedEntryClassName;
    public final String classLoaderDescription;
    public final String lastError;
    public final boolean active;
    public final long createdAtMs;
    public final long updatedAtMs;

    public PluginClassLoaderSessionSnapshot(String pluginId,
                                            String entryClassName,
                                            PluginClassLoaderSessionState state,
                                            String loadedEntryClassName,
                                            String classLoaderDescription,
                                            String lastError,
                                            boolean active,
                                            long createdAtMs,
                                            long updatedAtMs) {
        this.pluginId = pluginId == null ? "" : pluginId;
        this.entryClassName = entryClassName == null ? "" : entryClassName;
        this.state = state == null ? PluginClassLoaderSessionState.FAILED : state;
        this.loadedEntryClassName = loadedEntryClassName == null ? "" : loadedEntryClassName;
        this.classLoaderDescription = classLoaderDescription == null ? "" : classLoaderDescription;
        this.lastError = lastError == null ? "" : lastError;
        this.active = active;
        this.createdAtMs = Math.max(createdAtMs, 0L);
        this.updatedAtMs = Math.max(updatedAtMs, this.createdAtMs);
    }
}
