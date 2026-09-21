package pro.sketchware.debugger.symbolication;

import java.util.UUID;

public final class CrashSymbolicationRequest {

    public final String requestId;
    public final String sessionId;
    public final String projectId;
    public final String packageName;
    public final String buildVariant;
    public final String mappingFilePath;
    public final String rawStacktrace;
    public final long timestampMs;

    public CrashSymbolicationRequest(String requestId,
                                     String sessionId,
                                     String projectId,
                                     String packageName,
                                     String buildVariant,
                                     String mappingFilePath,
                                     String rawStacktrace,
                                     long timestampMs) {
        this.requestId = requestId == null || requestId.isEmpty()
                ? UUID.randomUUID().toString()
                : requestId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.projectId = projectId == null ? "" : projectId;
        this.packageName = packageName == null ? "" : packageName;
        this.buildVariant = buildVariant == null ? "" : buildVariant;
        this.mappingFilePath = mappingFilePath == null ? "" : mappingFilePath;
        this.rawStacktrace = rawStacktrace == null ? "" : rawStacktrace;
        this.timestampMs = Math.max(timestampMs, 0L);
    }

    public static CrashSymbolicationRequest fromStacktrace(String sessionId,
                                                           String projectId,
                                                           String packageName,
                                                           String buildVariant,
                                                           String mappingFilePath,
                                                           String rawStacktrace) {
        return new CrashSymbolicationRequest(
                "",
                sessionId,
                projectId,
                packageName,
                buildVariant,
                mappingFilePath,
                rawStacktrace,
                System.currentTimeMillis()
        );
    }
}
