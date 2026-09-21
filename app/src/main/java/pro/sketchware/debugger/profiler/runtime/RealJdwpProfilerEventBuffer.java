package pro.sketchware.debugger.profiler.runtime;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import pro.sketchware.debugger.profiler.JdwpCpuProfilerEvent;
import pro.sketchware.debugger.profiler.JdwpMemoryProfilerEvent;
import pro.sketchware.debugger.profiler.JdwpNetworkProfilerEvent;
import pro.sketchware.debugger.profiler.JdwpProfilerEventBuffer;

public final class RealJdwpProfilerEventBuffer extends JdwpProfilerEventBuffer {

    private final ConcurrentMap<String, NetworkCounters> networkCountersBySession = new ConcurrentHashMap<>();

    @Override
    public boolean recordRuntimeSample(String sessionId) {
        String safeSessionId = sessionId == null ? "" : sessionId.trim();
        if (safeSessionId.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        record(buildCpuEvent(safeSessionId, now));
        record(buildMemoryEvent(safeSessionId, now));
        record(buildNetworkEvent(safeSessionId, now));
        return true;
    }

    private static JdwpCpuProfilerEvent buildCpuEvent(String sessionId, long now) {
        int threadCount = Math.max(Thread.activeCount(), 0);

        int availableProcessors = Math.max(Runtime.getRuntime().availableProcessors(), 1);
        float processCpuPercent = Math.min((threadCount * 100f) / (availableProcessors * 16f), 100f);
        float appCpuPercent = Math.min(processCpuPercent * 0.75f, 100f);
        return new JdwpCpuProfilerEvent(sessionId, now, processCpuPercent, appCpuPercent, threadCount, 250L);
    }

    private static JdwpMemoryProfilerEvent buildMemoryEvent(String sessionId, long now) {
        Runtime runtime = Runtime.getRuntime();
        long javaHeapUsed = Math.max(runtime.totalMemory() - runtime.freeMemory(), 0L);
        long javaHeapMax = Math.max(runtime.maxMemory(), 0L);
        long nativeHeapUsed = reflectLong("android.os.Debug", "getNativeHeapAllocatedSize");
        long pssKb = reflectLong("android.os.Debug", "getPss");
        long pssBytes = pssKb <= 0L ? 0L : pssKb * 1024L;
        return new JdwpMemoryProfilerEvent(
                sessionId,
                now,
                javaHeapUsed,
                javaHeapMax,
                nativeHeapUsed,
                pssBytes
        );
    }

    private JdwpNetworkProfilerEvent buildNetworkEvent(String sessionId, long now) {
        long totalTx = reflectLong("android.net.TrafficStats", "getTotalTxBytes");
        long totalRx = reflectLong("android.net.TrafficStats", "getTotalRxBytes");

        NetworkCounters previous = networkCountersBySession.put(
                sessionId,
                new NetworkCounters(totalTx, totalRx)
        );
        long txDelta = previous == null ? 0L : Math.max(totalTx - previous.txBytes, 0L);
        long rxDelta = previous == null ? 0L : Math.max(totalRx - previous.rxBytes, 0L);
        int requestCount = txDelta > 0L || rxDelta > 0L ? 1 : 0;

        return new JdwpNetworkProfilerEvent(
                sessionId,
                now,
                txDelta,
                rxDelta,
                0,
                requestCount,
                0
        );
    }

    private static long reflectLong(String className, String methodName) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName);
            Object value = method.invoke(null);
            if (value instanceof Number) {
                return Math.max(((Number) value).longValue(), 0L);
            }
            return 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static final class NetworkCounters {
        private final long txBytes;
        private final long rxBytes;

        private NetworkCounters(long txBytes, long rxBytes) {
            this.txBytes = Math.max(txBytes, 0L);
            this.rxBytes = Math.max(rxBytes, 0L);
        }
    }
}
