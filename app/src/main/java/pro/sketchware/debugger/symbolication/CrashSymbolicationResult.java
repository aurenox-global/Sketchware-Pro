package pro.sketchware.debugger.symbolication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CrashSymbolicationResult {

    public final String requestId;
    public final String sessionId;
    public final CrashSymbolicationStatus status;
    public final long timestampMs;
    public final String symbolicatedStacktrace;
    public final List<CrashSymbolicatedFrame> frames;
    public final List<String> warnings;
    public final String errorMessage;

    private CrashSymbolicationResult(String requestId,
                                     String sessionId,
                                     CrashSymbolicationStatus status,
                                     long timestampMs,
                                     String symbolicatedStacktrace,
                                     List<CrashSymbolicatedFrame> frames,
                                     List<String> warnings,
                                     String errorMessage) {
        this.requestId = requestId == null ? "" : requestId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.status = status == null ? CrashSymbolicationStatus.FAILED : status;
        this.timestampMs = Math.max(timestampMs, 0L);
        this.symbolicatedStacktrace = symbolicatedStacktrace == null ? "" : symbolicatedStacktrace;
        this.frames = Collections.unmodifiableList(new ArrayList<>(
                frames == null ? Collections.emptyList() : frames
        ));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(
                warnings == null ? Collections.emptyList() : warnings
        ));
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static CrashSymbolicationResult queued(CrashSymbolicationRequest request) {
        CrashSymbolicationRequest safeRequest = request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;
        return new CrashSymbolicationResult(
                safeRequest.requestId,
                safeRequest.sessionId,
                CrashSymbolicationStatus.QUEUED,
                System.currentTimeMillis(),
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                ""
        );
    }

    public static CrashSymbolicationResult success(CrashSymbolicationRequest request,
                                                    String symbolicatedStacktrace,
                                                    List<CrashSymbolicatedFrame> frames,
                                                    List<String> warnings,
                                                    boolean partial) {
        CrashSymbolicationRequest safeRequest = request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;
        return new CrashSymbolicationResult(
                safeRequest.requestId,
                safeRequest.sessionId,
                partial ? CrashSymbolicationStatus.PARTIAL : CrashSymbolicationStatus.SUCCESS,
                System.currentTimeMillis(),
                symbolicatedStacktrace,
                frames,
                warnings,
                ""
        );
    }

    public static CrashSymbolicationResult failure(CrashSymbolicationRequest request, String errorMessage) {
        CrashSymbolicationRequest safeRequest = request == null
                ? CrashSymbolicationRequest.fromStacktrace("", "", "", "", "", "")
                : request;
        return new CrashSymbolicationResult(
                safeRequest.requestId,
                safeRequest.sessionId,
                CrashSymbolicationStatus.FAILED,
                System.currentTimeMillis(),
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                errorMessage
        );
    }

    public CrashSymbolicationResult canceled() {
        return new CrashSymbolicationResult(
                requestId,
                sessionId,
                CrashSymbolicationStatus.CANCELED,
                System.currentTimeMillis(),
                symbolicatedStacktrace,
                frames,
                warnings,
                errorMessage
        );
    }
}
