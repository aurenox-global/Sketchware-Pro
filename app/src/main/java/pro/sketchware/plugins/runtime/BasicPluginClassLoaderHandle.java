package pro.sketchware.plugins.runtime;

public final class BasicPluginClassLoaderHandle implements PluginClassLoaderHandle {

    private final ClassLoader classLoader;
    private final String description;
    private volatile boolean closed;

    public BasicPluginClassLoaderHandle(ClassLoader classLoader, String description) {
        this.classLoader = classLoader == null
                ? PluginClassLoaderHandle.class.getClassLoader()
                : classLoader;
        this.description = description == null ? "" : description;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (classLoader instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception ignored) {
                // Close failures are non-fatal for lifecycle state transitions.
            }
        }
    }
}
