package pro.sketchware.debugger.profiler;

import java.util.UUID;

public abstract class JdwpProfilerEvent {

    public final String eventId;
    public final String sessionId;
    public final JdwpProfilerEventType type;
    public final long timestampMs;

    protected JdwpProfilerEvent(String eventId,
                                String sessionId,
                                JdwpProfilerEventType type,
                                long timestampMs) {
        this.eventId = eventId == null || eventId.isEmpty()
                ? UUID.randomUUID().toString()
                : eventId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.type = type == null ? JdwpProfilerEventType.CPU : type;
        this.timestampMs = Math.max(timestampMs, 0L);
    }
}
