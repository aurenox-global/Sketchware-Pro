package pro.sketchware.debugger.profiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JdwpProfilerEventBuffer {

    private static final int DEFAULT_MAX_EVENTS_PER_SESSION = 512;

    private final int maxEventsPerSession;
    private final ConcurrentMap<String, Deque<JdwpProfilerEvent>> eventsBySession = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JdwpProfilerEventBuffer() {
        this(DEFAULT_MAX_EVENTS_PER_SESSION);
    }

    public JdwpProfilerEventBuffer(int maxEventsPerSession) {
        this.maxEventsPerSession = Math.max(maxEventsPerSession, 32);
    }

    public void record(JdwpProfilerEvent event) {
        if (event == null) {
            return;
        }

        String sessionId = normalizeSessionId(event.sessionId);
        lock.writeLock().lock();
        try {
            Deque<JdwpProfilerEvent> events =
                    eventsBySession.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
            events.addLast(event);
            while (events.size() > maxEventsPerSession) {
                events.removeFirst();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<JdwpProfilerEvent> snapshot(String sessionId, int limit) {
        String safeSessionId = normalizeSessionId(sessionId);
        int safeLimit = Math.max(limit, 1);

        lock.readLock().lock();
        try {
            Deque<JdwpProfilerEvent> events = eventsBySession.get(safeSessionId);
            if (events == null || events.isEmpty()) {
                return Collections.emptyList();
            }

            List<JdwpProfilerEvent> snapshot = new ArrayList<>(events);
            if (snapshot.size() > safeLimit) {
                snapshot = snapshot.subList(snapshot.size() - safeLimit, snapshot.size());
            }
            return Collections.unmodifiableList(new ArrayList<>(snapshot));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);
        lock.readLock().lock();
        try {
            Deque<JdwpProfilerEvent> events = eventsBySession.get(safeSessionId);
            return events == null ? 0 : events.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearSession(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);
        lock.writeLock().lock();
        try {
            eventsBySession.remove(safeSessionId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            eventsBySession.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean recordRuntimeSample(String sessionId) {
        return false;
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null ? "" : sessionId;
    }
}
