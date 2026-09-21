package pro.sketchware.debugger.jdwp.runtime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import pro.sketchware.debugger.jdwp.JdwpBridge;
import pro.sketchware.debugger.jdwp.JdwpDebugSession;
import pro.sketchware.debugger.jdwp.JdwpSessionConfig;
import pro.sketchware.debugger.jdwp.JdwpSessionState;

public final class RealJdwpBridge implements JdwpBridge {

    @Override
    public JdwpDebugSession createSession(JdwpSessionConfig config) {
        return new SocketBackedJdwpDebugSession(config);
    }

    private static final class SocketBackedJdwpDebugSession implements JdwpDebugSession {

        private final String sessionId = UUID.randomUUID().toString();
        private final JdwpSessionConfig config;
        private final long createdAtMs = System.currentTimeMillis();
        private final AtomicLong startedAtMs = new AtomicLong(0L);
        private final AtomicLong stoppedAtMs = new AtomicLong(0L);
        private final AtomicReference<JdwpSessionState> state =
                new AtomicReference<>(JdwpSessionState.CREATED);
        private final AtomicReference<String> lastError = new AtomicReference<>("");
        private final Object socketLock = new Object();
        private Socket socket;

        private SocketBackedJdwpDebugSession(JdwpSessionConfig config) {
            this.config = config == null
                    ? JdwpSessionConfig.local("", "", 0)
                    : config;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public JdwpSessionConfig config() {
            return config;
        }

        @Override
        public JdwpSessionState state() {
            return state.get();
        }

        @Override
        public long createdAtMs() {
            return createdAtMs;
        }

        @Override
        public long startedAtMs() {
            return startedAtMs.get();
        }

        @Override
        public long stoppedAtMs() {
            return stoppedAtMs.get();
        }

        @Override
        public String lastError() {
            return lastError.get();
        }

        @Override
        public boolean start() {
            if (!state.compareAndSet(JdwpSessionState.CREATED, JdwpSessionState.STARTING)
                    && !state.compareAndSet(JdwpSessionState.TERMINATED, JdwpSessionState.STARTING)) {
                return false;
            }

            startedAtMs.set(System.currentTimeMillis());
            Socket candidate = new Socket();
            try {
                int timeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(200L, config.attachTimeoutMs));
                candidate.connect(new InetSocketAddress(config.host, config.port), timeoutMs);
                synchronized (socketLock) {
                    closeSocketLocked();
                    socket = candidate;
                }
                lastError.set("");
                state.set(JdwpSessionState.ATTACHED);
                return true;
            } catch (IOException ioException) {
                closeQuietly(candidate);
                lastError.set(ioException.getMessage() == null
                        ? "Failed to attach JDWP session"
                        : ioException.getMessage());
                state.set(JdwpSessionState.FAILED);
                return false;
            }
        }

        @Override
        public boolean stop() {
            JdwpSessionState current = state.get();
            if (current == JdwpSessionState.TERMINATED || current == JdwpSessionState.FAILED) {
                return false;
            }

            state.set(JdwpSessionState.STOPPING);
            synchronized (socketLock) {
                closeSocketLocked();
            }
            stoppedAtMs.set(System.currentTimeMillis());
            state.set(JdwpSessionState.TERMINATED);
            return true;
        }

        private void closeSocketLocked() {
            if (socket == null) {
                return;
            }
            closeQuietly(socket);
            socket = null;
        }

        private static void closeQuietly(Socket target) {
            if (target == null) {
                return;
            }
            try {
                target.close();
            } catch (IOException ignored) {
                android.util.Log.d("SketchwarePro", "RealJdwpBridge: IOException ignored", ignored);
            }
        }
    }
}