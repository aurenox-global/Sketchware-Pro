package pro.sketchware.plugins.runtime;

public interface PluginClassLoaderFactory {

    PluginClassLoaderHandle create(PluginClassLoaderConfig config, ClassLoader parentClassLoader);
}
