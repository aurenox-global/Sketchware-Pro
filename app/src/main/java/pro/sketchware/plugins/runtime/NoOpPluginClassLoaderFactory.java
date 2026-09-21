package pro.sketchware.plugins.runtime;

public final class NoOpPluginClassLoaderFactory implements PluginClassLoaderFactory {

    @Override
    public PluginClassLoaderHandle create(PluginClassLoaderConfig config, ClassLoader parentClassLoader) {
        ClassLoader fallbackParent = parentClassLoader == null
                ? NoOpPluginClassLoaderFactory.class.getClassLoader()
                : parentClassLoader;
        String pluginId = config == null ? "" : config.pluginId;
        return new BasicPluginClassLoaderHandle(fallbackParent, "noop:" + pluginId);
    }
}
