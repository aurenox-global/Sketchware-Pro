package pro.sketchware.plugins.runtime;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public final class UrlPluginClassLoaderFactory implements PluginClassLoaderFactory {

    private final PluginClassLoaderFactory fallbackFactory;

    public UrlPluginClassLoaderFactory() {
        this(new NoOpPluginClassLoaderFactory());
    }

    public UrlPluginClassLoaderFactory(PluginClassLoaderFactory fallbackFactory) {
        this.fallbackFactory = fallbackFactory == null
                ? new NoOpPluginClassLoaderFactory()
                : fallbackFactory;
    }

    @Override
    public PluginClassLoaderHandle create(PluginClassLoaderConfig config, ClassLoader parentClassLoader) {
        PluginClassLoaderConfig safeConfig = config == null
                ? new PluginClassLoaderConfig("", "", "", "", "")
                : config;
        if (safeConfig.classpath.isEmpty()) {
            return fallbackFactory.create(safeConfig, parentClassLoader);
        }

        try {
            URL[] urls = toUrls(safeConfig.classpath);
            ClassLoader parent = parentClassLoader == null
                    ? UrlPluginClassLoaderFactory.class.getClassLoader()
                    : parentClassLoader;
            URLClassLoader classLoader = new URLClassLoader(urls, parent);
            return new BasicPluginClassLoaderHandle(classLoader, "url:" + safeConfig.classpath);
        } catch (Throwable throwable) {
            return fallbackFactory.create(safeConfig, parentClassLoader);
        }
    }

    private static URL[] toUrls(String classpath) throws MalformedURLException {
        String[] entries = classpath.split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        for (String entry : entries) {
            String trimmed = entry == null ? "" : entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            urls.add(new File(trimmed).toURI().toURL());
        }
        return urls.toArray(new URL[0]);
    }
}
