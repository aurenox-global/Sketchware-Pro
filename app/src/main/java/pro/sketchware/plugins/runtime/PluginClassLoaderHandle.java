package pro.sketchware.plugins.runtime;

public interface PluginClassLoaderHandle extends AutoCloseable {

    ClassLoader classLoader();

    String description();

    @Override
    void close();
}
