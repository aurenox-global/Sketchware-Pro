package pro.sketchware.debugger.symbolication;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class NoOpCrashSymbolicationWorkflow implements CrashSymbolicationWorkflow {

    private final ConcurrentMap<String, CrashSymbolicationResult> results = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> requestIdsBySession = new ConcurrentHashMap<>();

    @Override
    public String enqueue(CrashSymbolicationRequest request) {
        CrashSymbolicationRequest safeRequest = request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;

        CrashSymbolicationResult queued = CrashSymbolicationResult.queued(safeRequest);
        results.put(safeRequest.requestId, queued);
        requestIdsBySession
                .computeIfAbsent(normalizeSessionId(safeRequest.sessionId), key -> ConcurrentHashMap.newKeySet())
                .add(safeRequest.requestId);
        return safeRequest.requestId;
    }

    @Override
    public CrashSymbolicationResult symbolicateNow(CrashSymbolicationRequest request) {
        CrashSymbolicationRequest safeRequest = request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;

        CrashSymbolicationResult failure = CrashSymbolicationResult.failure(
                safeRequest,
                "Crash symbolication backend is not connected"
        );
        results.put(safeRequest.requestId, failure);
        requestIdsBySession
                .computeIfAbsent(normalizeSessionId(safeRequest.sessionId), key -> ConcurrentHashMap.newKeySet())
                .add(safeRequest.requestId);
        return failure;
    }

    @Override
    public CrashSymbolicationResult getResult(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return null;
        }
        return results.get(requestId);
    }

    @Override
    public boolean cancel(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }

        return results.computeIfPresent(requestId, (id, existing) -> {
            if (existing.status == CrashSymbolicationStatus.SUCCESS
                    || existing.status == CrashSymbolicationStatus.PARTIAL
                    || existing.status == CrashSymbolicationStatus.FAILED
                    || existing.status == CrashSymbolicationStatus.CANCELED) {
                return existing;
            }
            return existing.canceled();
        }) != null;
    }

    @Override
    public void clearSession(String sessionId) {
        String safeSessionId = normalizeSessionId(sessionId);
        Set<String> requestIds = requestIdsBySession.remove(safeSessionId);
        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }

        for (String requestId : requestIds) {
            results.remove(requestId);
        }
    }

    @Override
    public void clear() {
        results.clear();
        requestIdsBySession.clear();
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null ? "" : sessionId;
    }
}
