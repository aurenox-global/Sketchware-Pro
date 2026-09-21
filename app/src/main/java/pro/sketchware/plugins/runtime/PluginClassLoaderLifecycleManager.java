package pro.sketchware.plugins.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PluginClassLoaderLifecycleManager {

    private final PluginClassLoaderFactory classLoaderFactory;
    private final ConcurrentMap<String, PluginClassLoaderSession> sessions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile String activePluginId = "";

    public PluginClassLoaderLifecycleManager() {
        this(new UrlPluginClassLoaderFactory());
    }

    public PluginClassLoaderLifecycleManager(PluginClassLoaderFactory classLoaderFactory) {
        this.classLoaderFactory = classLoaderFactory == null
                ? new UrlPluginClassLoaderFactory()
                : classLoaderFactory;
    }

    public PluginClassLoaderSession createSession(PluginClassLoaderConfig config,
                                                  ClassLoader parentClassLoader) {
        if (config == null || config.pluginId.isEmpty()) {
            return null;
        }

        PluginClassLoaderSession session = new PluginClassLoaderSession(
                config,
                classLoaderFactory.create(config, parentClassLoader)
        );

        lock.writeLock().lock();
        try {
            PluginClassLoaderSession previous = sessions.put(config.pluginId, session);
            if (previous != null) {
                previous.unload();
            }
            if (config.pluginId.equals(activePluginId)) {
                activePluginId = "";
            }
            return session;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean startSession(String pluginId, boolean exclusive) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            PluginClassLoaderSession session = sessions.get(pluginId);
            if (session == null) {
                return false;
            }

            if (exclusive) {
                stopActiveSessionLocked();
            }

            boolean started = session.start();
            if (started) {
                activePluginId = pluginId;
            }
            return started;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean stopSession(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            PluginClassLoaderSession session = sessions.get(pluginId);
            if (session == null) {
                return false;
            }

            boolean stopped = session.stop();
            if (stopped && pluginId.equals(activePluginId)) {
                activePluginId = "";
            }
            return stopped;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeSession(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            PluginClassLoaderSession removed = sessions.remove(pluginId);
            if (removed == null) {
                return false;
            }
            removed.unload();
            if (pluginId.equals(activePluginId)) {
                activePluginId = "";
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            for (PluginClassLoaderSession session : sessions.values()) {
                session.unload();
            }
            sessions.clear();
            activePluginId = "";
        } finally {
            lock.writeLock().unlock();
        }
    }

    public PluginClassLoaderSession getSession(String pluginId) {
        return sessions.get(pluginId);
    }

    public String getActivePluginId() {
        return activePluginId;
    }

    public List<PluginClassLoaderSessionSnapshot> snapshotSessions() {
        lock.readLock().lock();
        try {
            ArrayList<PluginClassLoaderSessionSnapshot> snapshot = new ArrayList<>();
            for (PluginClassLoaderSession session : sessions.values()) {
                boolean active = session.pluginId().equals(activePluginId);
                snapshot.add(session.snapshot(active));
            }
            snapshot.sort(Comparator.comparing(item -> item.pluginId));
            return Collections.unmodifiableList(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void stopActiveSessionLocked() {
        if (activePluginId == null || activePluginId.isEmpty()) {
            return;
        }

        PluginClassLoaderSession activeSession = sessions.get(activePluginId);
        if (activeSession != null) {
            activeSession.stop();
        }
        activePluginId = "";
    }
}
