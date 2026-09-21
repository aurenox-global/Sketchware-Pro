package pro.sketchware.plugins.runtime;

public final class PluginClassLoaderSession {

    private final PluginClassLoaderConfig config;
    private final PluginClassLoaderHandle classLoaderHandle;
    private final long createdAtMs;

    private volatile long updatedAtMs;
    private volatile PluginClassLoaderSessionState state;
    private volatile String loadedEntryClassName;
    private volatile String lastError;

    public PluginClassLoaderSession(PluginClassLoaderConfig config,
                                    PluginClassLoaderHandle classLoaderHandle) {
        this.config = config == null
                ? new PluginClassLoaderConfig("", "", "", "", "")
                : config;
        this.classLoaderHandle = classLoaderHandle == null
                ? new BasicPluginClassLoaderHandle(
                        PluginClassLoaderSession.class.getClassLoader(),
                        "default"
                )
                : classLoaderHandle;
        this.createdAtMs = System.currentTimeMillis();
        this.updatedAtMs = createdAtMs;
        this.state = PluginClassLoaderSessionState.CREATED;
        this.loadedEntryClassName = "";
        this.lastError = "";
    }

    public synchronized boolean start() {
        if (state == PluginClassLoaderSessionState.UNLOADED) {
            return false;
        }

        if (!config.hasEntryClass()) {
            state = PluginClassLoaderSessionState.FAILED;
            lastError = "Entry class is empty";
            touch();
            return false;
        }

        try {
            Class<?> entryClass = classLoaderHandle.classLoader().loadClass(config.entryClassName);
            loadedEntryClassName = entryClass.getName();
            lastError = "";
            state = PluginClassLoaderSessionState.STARTED;
            touch();
            return true;
        } catch (Throwable throwable) {
            loadedEntryClassName = "";
            lastError = throwable.getMessage() == null
                    ? "Unable to load entry class"
                    : throwable.getMessage();
            state = PluginClassLoaderSessionState.FAILED;
            touch();
            return false;
        }
    }

    public synchronized boolean stop() {
        if (state == PluginClassLoaderSessionState.UNLOADED) {
            return false;
        }
        if (state == PluginClassLoaderSessionState.STARTED) {
            state = PluginClassLoaderSessionState.STOPPED;
            touch();
            return true;
        }
        return state == PluginClassLoaderSessionState.STOPPED
                || state == PluginClassLoaderSessionState.CREATED
                || state == PluginClassLoaderSessionState.FAILED;
    }

    public synchronized boolean unload() {
        if (state == PluginClassLoaderSessionState.UNLOADED) {
            return true;
        }

        stop();
        try {
            classLoaderHandle.close();
            state = PluginClassLoaderSessionState.UNLOADED;
            touch();
            return true;
        } catch (Throwable throwable) {
            lastError = throwable.getMessage() == null
                    ? "Unable to unload classloader"
                    : throwable.getMessage();
            state = PluginClassLoaderSessionState.FAILED;
            touch();
            return false;
        }
    }

    public String pluginId() {
        return config.pluginId;
    }

    public PluginClassLoaderSessionState state() {
        return state;
    }

    public PluginClassLoaderSessionSnapshot snapshot(boolean active) {
        return new PluginClassLoaderSessionSnapshot(
                config.pluginId,
                config.entryClassName,
                state,
                loadedEntryClassName,
                classLoaderHandle.description(),
                lastError,
                active,
                createdAtMs,
                updatedAtMs
        );
    }

    private void touch() {
        updatedAtMs = System.currentTimeMillis();
    }
}
