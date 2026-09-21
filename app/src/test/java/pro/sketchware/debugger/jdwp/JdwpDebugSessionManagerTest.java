package pro.sketchware.debugger.jdwp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import pro.sketchware.debugger.symbolication.NoOpCrashSymbolicationWorkflow;
import pro.sketchware.debugger.variables.NoOpJdwpVariableInspectorTransport;

public class JdwpDebugSessionManagerTest {

    @Test
    public void createStartStopSession_withFallbackRuntimeComponents_isStable() {
        JdwpDebugSessionManager manager = new JdwpDebugSessionManager(
            new NoOpJdwpBridge(),
            new NoOpJdwpVariableInspectorTransport(),
            new NoOpCrashSymbolicationWorkflow()
        );

        JdwpDebugSession session = manager.createSession(
                JdwpSessionConfig.local("project-1", "pro.sketchware", 8700)
        );

        assertNotNull(session);
        assertNotNull(manager.variableInspector());
        assertNotNull(manager.crashSymbolication());

        boolean started = manager.startSession(session.sessionId(), true);
        assertTrue(started);
        assertEquals(session.sessionId(), manager.getActiveSessionId());

        boolean stopped = manager.stopSession(session.sessionId());
        assertTrue(stopped);
    }

    @Test
    public void recordProfilerSample_withAttachedSession_recordsEvents() {
        JdwpDebugSessionManager manager = new JdwpDebugSessionManager(
                new NoOpJdwpBridge(),
                new NoOpJdwpVariableInspectorTransport(),
                new NoOpCrashSymbolicationWorkflow()
        );

        JdwpDebugSession session = manager.createSession(
                JdwpSessionConfig.local("project-1", "pro.sketchware", 8700)
        );
        assertTrue(manager.startSession(session.sessionId(), true));

        boolean recorded = manager.recordProfilerSample(session.sessionId());

        assertTrue(recorded);
        assertFalse(manager.profilerEvents().snapshot(session.sessionId(), 10).isEmpty());
    }

    @Test
    public void exportProfilerReport_withAttachedSession_writesJsonFile() throws Exception {
        JdwpDebugSessionManager manager = new JdwpDebugSessionManager(
                new NoOpJdwpBridge(),
                new NoOpJdwpVariableInspectorTransport(),
                new NoOpCrashSymbolicationWorkflow()
        );

        JdwpDebugSession session = manager.createSession(
                JdwpSessionConfig.local("project-1", "pro.sketchware", 8700)
        );
        assertTrue(manager.startSession(session.sessionId(), true));
        assertTrue(manager.recordProfilerSample(session.sessionId()));

        File outputFile = File.createTempFile("jdwp-profiler-report", ".json");
        assertTrue(manager.exportProfilerReport(session.sessionId(), 20, outputFile.getAbsolutePath()));

        String content = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"sessionId\":\"" + session.sessionId() + "\""));
        assertTrue(content.contains("\"eventCount\":"));
        assertTrue(content.contains("\"summary\":"));
        outputFile.delete();
    }
}
