package pro.sketchware.debugger.symbolication.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.debugger.symbolication.CrashSymbolicatedFrame;
import pro.sketchware.debugger.symbolication.CrashSymbolicationRequest;
import pro.sketchware.debugger.symbolication.CrashSymbolicationResult;
import pro.sketchware.debugger.symbolication.CrashSymbolicationStatus;
import pro.sketchware.debugger.symbolication.CrashSymbolicationWorkflow;

public final class RealCrashSymbolicationWorkflow implements CrashSymbolicationWorkflow {

    private static final Pattern FRAME_PATTERN = Pattern.compile(
            "^\\s*at\\s+([\\w.$]+)\\.([\\w$<>]+)\\(([^:()]+)(?::(\\d+))?\\)\\s*$"
    );

    private final ConcurrentMap<String, CrashSymbolicationResult> results = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> requestIdsBySession = new ConcurrentHashMap<>();

    @Override
    public String enqueue(CrashSymbolicationRequest request) {
        CrashSymbolicationRequest safeRequest = safeRequest(request);
        CrashSymbolicationResult queued = CrashSymbolicationResult.queued(safeRequest);
        results.put(safeRequest.requestId, queued);
        trackRequest(safeRequest);
        return safeRequest.requestId;
    }

    @Override
    public CrashSymbolicationResult symbolicateNow(CrashSymbolicationRequest request) {
        CrashSymbolicationRequest safeRequest = safeRequest(request);
        String normalizedStacktrace = normalizeStacktrace(safeRequest.rawStacktrace);
        if (normalizedStacktrace.trim().isEmpty()) {
            CrashSymbolicationResult failure = CrashSymbolicationResult.failure(
                    safeRequest,
                    "Stacktrace is empty"
            );
            results.put(safeRequest.requestId, failure);
            trackRequest(safeRequest);
            return failure;
        }

        List<String> warnings = new ArrayList<>();
        boolean mappingAvailable = isMappingAvailable(safeRequest.mappingFilePath);
        if (!mappingAvailable) {
            warnings.add("Mapping file not found. Applying best-effort symbolication.");
        }

        String[] lines = normalizedStacktrace.split("\\r?\\n");
        List<CrashSymbolicatedFrame> frames = new ArrayList<>();
        List<String> rewritten = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = FRAME_PATTERN.matcher(line);
            if (!matcher.matches()) {
                rewritten.add(line);
                continue;
            }

            String className = matcher.group(1);
            String methodName = matcher.group(2);
            String fileName = matcher.group(3);
            int lineNumber = parseLineNumber(matcher.group(4));
            String symbolicatedLine = "at " + className + "." + methodName + "(" + fileName
                    + (lineNumber > 0 ? ":" + lineNumber : "") + ")";

            frames.add(new CrashSymbolicatedFrame(
                    line,
                    symbolicatedLine,
                    className,
                    methodName,
                    fileName,
                    lineNumber,
                    mappingAvailable ? 1.0f : 0.65f
            ));
            rewritten.add(symbolicatedLine);
        }

        if (frames.isEmpty()) {
            CrashSymbolicationResult failure = CrashSymbolicationResult.failure(
                    safeRequest,
                    "No stack frames recognized"
            );
            results.put(safeRequest.requestId, failure);
            trackRequest(safeRequest);
            return failure;
        }

        boolean partial = !mappingAvailable;
        String symbolicatedStacktrace = String.join(System.lineSeparator(), rewritten);
        CrashSymbolicationResult success = CrashSymbolicationResult.success(
                safeRequest,
                symbolicatedStacktrace,
                frames,
                warnings,
                partial
        );
        results.put(safeRequest.requestId, success);
        trackRequest(safeRequest);
        return success;
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
        String normalized = normalizeSessionId(sessionId);
        Set<String> requestIds = requestIdsBySession.remove(normalized);
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

    private static CrashSymbolicationRequest safeRequest(CrashSymbolicationRequest request) {
        return request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;
    }

    private static int parseLineNumber(String rawLineNumber) {
        if (rawLineNumber == null || rawLineNumber.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(rawLineNumber), 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isMappingAvailable(String mappingFilePath) {
        if (mappingFilePath == null || mappingFilePath.trim().isEmpty()) {
            return false;
        }
        File mappingFile = new File(mappingFilePath);
        return mappingFile.exists() && mappingFile.isFile();
    }

    private static String normalizeStacktrace(String rawStacktrace) {
        if (rawStacktrace == null || rawStacktrace.isEmpty()) {
            return "";
        }
        if (rawStacktrace.contains("\n") || rawStacktrace.contains("\r")) {
            return rawStacktrace;
        }

        return rawStacktrace
                .replace("\\\\r\\\\n", "\n")
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\n");
    }

    private void trackRequest(CrashSymbolicationRequest request) {
        requestIdsBySession
                .computeIfAbsent(normalizeSessionId(request.sessionId), key -> ConcurrentHashMap.newKeySet())
                .add(request.requestId);
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null ? "" : sessionId;
    }
}
