package pro.sketchware.debugger.jdwp;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class NoOpJdwpBridge implements JdwpBridge {

    @Override
    public JdwpDebugSession createSession(JdwpSessionConfig config) {
        return new InMemoryJdwpDebugSession(config);
    }

    private static final class InMemoryJdwpDebugSession implements JdwpDebugSession {

        private final String sessionId = UUID.randomUUID().toString();
        private final JdwpSessionConfig config;
        private final long createdAtMs = System.currentTimeMillis();
        private final AtomicLong startedAtMs = new AtomicLong(0L);
        private final AtomicLong stoppedAtMs = new AtomicLong(0L);
        private final AtomicReference<JdwpSessionState> state =
                new AtomicReference<>(JdwpSessionState.CREATED);
        private final AtomicReference<String> lastError = new AtomicReference<>("");

        private InMemoryJdwpDebugSession(JdwpSessionConfig config) {
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
            state.set(JdwpSessionState.ATTACHED);
            return true;
        }

        @Override
        public boolean stop() {
            JdwpSessionState current = state.get();
            if (current == JdwpSessionState.TERMINATED || current == JdwpSessionState.FAILED) {
                return false;
            }

            state.set(JdwpSessionState.STOPPING);
            stoppedAtMs.set(System.currentTimeMillis());
            state.set(JdwpSessionState.TERMINATED);
            return true;
        }
    }
}
