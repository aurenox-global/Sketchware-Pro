package pro.sketchware.debugger.profiler;

public final class JdwpNetworkProfilerEvent extends JdwpProfilerEvent {

    public final long txBytes;
    public final long rxBytes;
    public final int activeConnections;
    public final int requestCount;
    public final int errorCount;

    public JdwpNetworkProfilerEvent(String sessionId,
                                    long timestampMs,
                                    long txBytes,
                                    long rxBytes,
                                    int activeConnections,
                                    int requestCount,
                                    int errorCount) {
        super("", sessionId, JdwpProfilerEventType.NETWORK, timestampMs);
        this.txBytes = Math.max(txBytes, 0L);
        this.rxBytes = Math.max(rxBytes, 0L);
        this.activeConnections = Math.max(activeConnections, 0);
        this.requestCount = Math.max(requestCount, 0);
        this.errorCount = Math.max(errorCount, 0);
    }
}
