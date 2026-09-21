package pro.sketchware.debugger.profiler;

public final class JdwpCpuProfilerEvent extends JdwpProfilerEvent {

    public final float processCpuPercent;
    public final float appCpuPercent;
    public final int threadCount;
    public final long sampleDurationMs;

    public JdwpCpuProfilerEvent(String sessionId,
                                long timestampMs,
                                float processCpuPercent,
                                float appCpuPercent,
                                int threadCount,
                                long sampleDurationMs) {
        super("", sessionId, JdwpProfilerEventType.CPU, timestampMs);
        this.processCpuPercent = clampPercent(processCpuPercent);
        this.appCpuPercent = clampPercent(appCpuPercent);
        this.threadCount = Math.max(threadCount, 0);
        this.sampleDurationMs = Math.max(sampleDurationMs, 0L);
    }

    private static float clampPercent(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, 100f);
    }
}
