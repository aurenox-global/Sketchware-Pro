package pro.sketchware.debugger.jdwp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import pro.sketchware.debugger.breakpoints.JdwpBreakpointManager;
import pro.sketchware.debugger.profiler.JsonJdwpProfilerReportSerializer;
import pro.sketchware.debugger.profiler.JdwpProfilerEventBuffer;
import pro.sketchware.debugger.symbolication.CrashSymbolicationWorkflow;
import pro.sketchware.debugger.variables.JdwpVariableInspectorTransport;

public final class JdwpDebugSessionManager {

    private final JdwpBridge bridge;
    private final JdwpVariableInspectorTransport variableInspectorTransport;
    private final CrashSymbolicationWorkflow crashSymbolicationWorkflow;
    private final JdwpProfilerEventBuffer profilerEventBuffer;
    private final JdwpBreakpointManager breakpointManager = new JdwpBreakpointManager();
    private final ConcurrentMap<String, JdwpDebugSession> sessions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile String activeSessionId = "";

    public JdwpDebugSessionManager(JdwpBridge bridge) {
        this(bridge, null, null, null);
    }

    public JdwpDebugSessionManager(JdwpBridge bridge,
                                   JdwpVariableInspectorTransport variableInspectorTransport) {
        this(bridge, variableInspectorTransport, null, null);
    }

    public JdwpDebugSessionManager(JdwpBridge bridge,
                                   JdwpVariableInspectorTransport variableInspectorTransport,
                                   CrashSymbolicationWorkflow crashSymbolicationWorkflow) {
        this(bridge, variableInspectorTransport, crashSymbolicationWorkflow, null);
    }

    public JdwpDebugSessionManager(JdwpBridge bridge,
                                   JdwpVariableInspectorTransport variableInspectorTransport,
                                   CrashSymbolicationWorkflow crashSymbolicationWorkflow,
                                   JdwpProfilerEventBuffer profilerEventBuffer) {
        this.bridge = JdwpRuntimeFactory.bridgeOrFallback(bridge);
        this.variableInspectorTransport = JdwpRuntimeFactory.variableInspectorOrFallback(
            variableInspectorTransport
        );
        this.crashSymbolicationWorkflow = JdwpRuntimeFactory.symbolicationOrFallback(
            crashSymbolicationWorkflow
        );
        this.profilerEventBuffer = JdwpRuntimeFactory.profilerBufferOrFallback(profilerEventBuffer);
    }

    public JdwpDebugSession createSession(JdwpSessionConfig config) {
        JdwpDebugSession session = bridge.createSession(config);
        sessions.put(session.sessionId(), session);
        return session;
    }

    public boolean startSession(String sessionId, boolean exclusive) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpDebugSession session = sessions.get(sessionId);
            if (session == null) {
                return false;
            }

            if (exclusive) {
                stopActiveSessionLocked();
            }

            boolean started = session.start();
            if (started) {
                activeSessionId = session.sessionId();
            }
            return started;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean stopSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpDebugSession session = sessions.get(sessionId);
            if (session == null) {
                return false;
            }

            boolean stopped = session.stop();
            if (stopped && sessionId.equals(activeSessionId)) {
                activeSessionId = "";
            }
            return stopped;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpDebugSession session = sessions.remove(sessionId);
            if (session == null) {
                return false;
            }

            session.stop();
            breakpointManager.removeSessionBreakpoints(sessionId);
            profilerEventBuffer.clearSession(sessionId);
            crashSymbolicationWorkflow.clearSession(sessionId);
            if (sessionId.equals(activeSessionId)) {
                activeSessionId = "";
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            for (JdwpDebugSession session : sessions.values()) {
                session.stop();
            }
            sessions.clear();
            breakpointManager.clear();
            profilerEventBuffer.clear();
            crashSymbolicationWorkflow.clear();
            activeSessionId = "";
        } finally {
            lock.writeLock().unlock();
        }
    }

    public JdwpBreakpointManager breakpoints() {
        return breakpointManager;
    }

    public JdwpVariableInspectorTransport variableInspector() {
        return variableInspectorTransport;
    }

    public JdwpProfilerEventBuffer profilerEvents() {
        return profilerEventBuffer;
    }

    public boolean recordProfilerSample(String sessionId) {
        String targetSessionId = sessionId;
        if (targetSessionId == null || targetSessionId.isEmpty()) {
            targetSessionId = activeSessionId;
        }
        if (targetSessionId == null || targetSessionId.isEmpty()) {
            return false;
        }

        JdwpDebugSession session = sessions.get(targetSessionId);
        if (session == null || session.state() != JdwpSessionState.ATTACHED) {
            return false;
        }
        return profilerEventBuffer.recordRuntimeSample(targetSessionId);
    }

    public String buildProfilerReportJson(String sessionId, int limit) {
        String safeSessionId = sessionId == null ? "" : sessionId;
        if (safeSessionId.isEmpty()) {
            return "";
        }

        List<pro.sketchware.debugger.profiler.JdwpProfilerEvent> events =
                profilerEventBuffer.snapshot(safeSessionId, Math.max(limit, 1));
        JsonJdwpProfilerReportSerializer serializer = new JsonJdwpProfilerReportSerializer();
        return serializer.serialize(safeSessionId, events);
    }

    public boolean exportProfilerReport(String sessionId, int limit, String outputFilePath) {
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return false;
        }

        String reportJson = buildProfilerReportJson(sessionId, limit);
        if (reportJson.isEmpty()) {
            return false;
        }

        File output = new File(outputFilePath);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (FileWriter writer = new FileWriter(output, false)) {
            writer.write(reportJson);
            writer.flush();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public CrashSymbolicationWorkflow crashSymbolication() {
        return crashSymbolicationWorkflow;
    }

    public JdwpDebugSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public List<JdwpDebugSession> snapshotSessions() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private void stopActiveSessionLocked() {
        if (activeSessionId == null || activeSessionId.isEmpty()) {
            return;
        }

        JdwpDebugSession active = sessions.get(activeSessionId);
        if (active != null) {
            active.stop();
        }
        activeSessionId = "";
    }
}
