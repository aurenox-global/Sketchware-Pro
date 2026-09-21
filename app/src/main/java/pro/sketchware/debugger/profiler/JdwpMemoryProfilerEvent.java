package pro.sketchware.debugger.profiler;

public final class JdwpMemoryProfilerEvent extends JdwpProfilerEvent {

    public final long javaHeapUsedBytes;
    public final long javaHeapMaxBytes;
    public final long nativeHeapUsedBytes;
    public final long pssBytes;

    public JdwpMemoryProfilerEvent(String sessionId,
                                   long timestampMs,
                                   long javaHeapUsedBytes,
                                   long javaHeapMaxBytes,
                                   long nativeHeapUsedBytes,
                                   long pssBytes) {
        super("", sessionId, JdwpProfilerEventType.MEMORY, timestampMs);
        this.javaHeapUsedBytes = Math.max(javaHeapUsedBytes, 0L);
        this.javaHeapMaxBytes = Math.max(javaHeapMaxBytes, 0L);
        this.nativeHeapUsedBytes = Math.max(nativeHeapUsedBytes, 0L);
        this.pssBytes = Math.max(pssBytes, 0L);
    }
}
