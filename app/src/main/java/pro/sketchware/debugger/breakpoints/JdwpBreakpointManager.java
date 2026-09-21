package pro.sketchware.debugger.breakpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class JdwpBreakpointManager {

    private final ConcurrentMap<String, JdwpBreakpoint> breakpoints = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> breakpointsBySession = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JdwpBreakpoint addLineBreakpoint(String sessionId,
                                            String className,
                                            String sourcePath,
                                            int lineNumber,
                                            JdwpBreakpointCondition condition) {
        return add(JdwpBreakpoint.line(sessionId, className, sourcePath, lineNumber, condition));
    }

    public JdwpBreakpoint addMethodBreakpoint(String sessionId,
                                              String className,
                                              String methodName,
                                              JdwpBreakpointCondition condition) {
        return add(JdwpBreakpoint.method(sessionId, className, methodName, condition));
    }

    public JdwpBreakpoint addExceptionBreakpoint(String sessionId,
                                                 String exceptionClassName,
                                                 JdwpBreakpointCondition condition) {
        return add(JdwpBreakpoint.exception(sessionId, exceptionClassName, condition));
    }

    public JdwpBreakpoint add(JdwpBreakpoint breakpoint) {
        if (breakpoint == null) {
            return null;
        }

        lock.writeLock().lock();
        try {
            breakpoints.put(breakpoint.breakpointId, breakpoint);
            breakpointsBySession
                    .computeIfAbsent(normalizeSessionId(breakpoint.sessionId), key -> ConcurrentHashMap.newKeySet())
                    .add(breakpoint.breakpointId);
            return breakpoint;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public JdwpBreakpoint getBreakpoint(String breakpointId) {
        if (breakpointId == null || breakpointId.isEmpty()) {
            return null;
        }
        return breakpoints.get(breakpointId);
    }

    public boolean setBreakpointEnabled(String breakpointId, boolean enabled) {
        if (breakpointId == null || breakpointId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpBreakpoint current = breakpoints.get(breakpointId);
            if (current == null) {
                return false;
            }
            breakpoints.put(breakpointId, current.withEnabled(enabled));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean updateBreakpointCondition(String breakpointId, JdwpBreakpointCondition condition) {
        if (breakpointId == null || breakpointId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpBreakpoint current = breakpoints.get(breakpointId);
            if (current == null) {
                return false;
            }
            breakpoints.put(breakpointId, current.withCondition(condition));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeBreakpoint(String breakpointId) {
        if (breakpointId == null || breakpointId.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            JdwpBreakpoint removed = breakpoints.remove(breakpointId);
            if (removed == null) {
                return false;
            }

            Set<String> ids = breakpointsBySession.get(normalizeSessionId(removed.sessionId));
            if (ids != null) {
                ids.remove(breakpointId);
                if (ids.isEmpty()) {
                    breakpointsBySession.remove(normalizeSessionId(removed.sessionId));
                }
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int removeSessionBreakpoints(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);

        lock.writeLock().lock();
        try {
            Set<String> ids = breakpointsBySession.remove(safeSessionId);
            if (ids == null || ids.isEmpty()) {
                return 0;
            }

            int removedCount = 0;
            for (String breakpointId : ids) {
                if (breakpoints.remove(breakpointId) != null) {
                    removedCount++;
                }
            }
            return removedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<JdwpBreakpoint> snapshotBreakpoints(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);

        lock.readLock().lock();
        try {
            Set<String> ids = breakpointsBySession.get(safeSessionId);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }

            List<JdwpBreakpoint> snapshot = new ArrayList<>();
            for (String id : ids) {
                JdwpBreakpoint breakpoint = breakpoints.get(id);
                if (breakpoint != null) {
                    snapshot.add(breakpoint);
                }
            }
            snapshot.sort(Comparator.comparingLong(breakpoint -> breakpoint.createdAtMs));
            return Collections.unmodifiableList(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<JdwpBreakpoint> snapshotAllBreakpoints() {
        lock.readLock().lock();
        try {
            List<JdwpBreakpoint> snapshot = new ArrayList<>(breakpoints.values());
            snapshot.sort(Comparator.comparingLong(breakpoint -> breakpoint.createdAtMs));
            return Collections.unmodifiableList(snapshot);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            breakpoints.clear();
            breakpointsBySession.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null ? "" : sessionId;
    }
}
