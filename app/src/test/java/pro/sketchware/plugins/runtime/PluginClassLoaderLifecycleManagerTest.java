package pro.sketchware.plugins.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PluginClassLoaderLifecycleManagerTest {

    @Test
    public void lifecycle_startSession_updatesActivePlugin() {
        PluginClassLoaderLifecycleManager manager =
                new PluginClassLoaderLifecycleManager(new NoOpPluginClassLoaderFactory());

        PluginClassLoaderConfig config = new PluginClassLoaderConfig(
                "plugin.one",
                "java.lang.String",
                "",
                "",
                ""
        );

        PluginClassLoaderSession session = manager.createSession(config, getClass().getClassLoader());
        assertNotNull(session);
        assertTrue(manager.startSession("plugin.one", true));
        assertEquals("plugin.one", manager.getActivePluginId());

        List<PluginClassLoaderSessionSnapshot> snapshots = manager.snapshotSessions();
        assertEquals(1, snapshots.size());
        assertEquals(PluginClassLoaderSessionState.STARTED, snapshots.get(0).state);
        assertTrue(snapshots.get(0).active);
    }

    @Test
    public void lifecycle_exclusiveStart_stopsPreviousSession() {
        PluginClassLoaderLifecycleManager manager =
                new PluginClassLoaderLifecycleManager(new NoOpPluginClassLoaderFactory());

        manager.createSession(new PluginClassLoaderConfig("plugin.a", "java.lang.String", "", "", ""), getClass().getClassLoader());
        manager.createSession(new PluginClassLoaderConfig("plugin.b", "java.lang.Integer", "", "", ""), getClass().getClassLoader());

        assertTrue(manager.startSession("plugin.a", true));
        assertTrue(manager.startSession("plugin.b", true));
        assertEquals("plugin.b", manager.getActivePluginId());

        List<PluginClassLoaderSessionSnapshot> snapshots = manager.snapshotSessions();
        assertEquals(2, snapshots.size());
        assertEquals("plugin.a", snapshots.get(0).pluginId);
        assertEquals(PluginClassLoaderSessionState.STOPPED, snapshots.get(0).state);
        assertEquals("plugin.b", snapshots.get(1).pluginId);
        assertEquals(PluginClassLoaderSessionState.STARTED, snapshots.get(1).state);
    }

    @Test
    public void lifecycle_startWithInvalidEntry_setsFailedState() {
        PluginClassLoaderLifecycleManager manager =
                new PluginClassLoaderLifecycleManager(new NoOpPluginClassLoaderFactory());

        manager.createSession(new PluginClassLoaderConfig("plugin.fail", "not.existing.EntryClass", "", "", ""), getClass().getClassLoader());

        assertFalse(manager.startSession("plugin.fail", true));
        assertEquals("", manager.getActivePluginId());

        List<PluginClassLoaderSessionSnapshot> snapshots = manager.snapshotSessions();
        assertEquals(1, snapshots.size());
        assertEquals(PluginClassLoaderSessionState.FAILED, snapshots.get(0).state);
        assertFalse(snapshots.get(0).lastError.isEmpty());
    }

    @Test
    public void lifecycle_removeAndClear_unloadsSessions() {
        PluginClassLoaderLifecycleManager manager =
                new PluginClassLoaderLifecycleManager(new NoOpPluginClassLoaderFactory());

        manager.createSession(new PluginClassLoaderConfig("plugin.a", "java.lang.String", "", "", ""), getClass().getClassLoader());
        manager.createSession(new PluginClassLoaderConfig("plugin.b", "java.lang.Integer", "", "", ""), getClass().getClassLoader());

        assertTrue(manager.removeSession("plugin.a"));
        assertEquals(1, manager.snapshotSessions().size());

        manager.clear();
        assertEquals(0, manager.snapshotSessions().size());
        assertEquals("", manager.getActivePluginId());
    }
}
