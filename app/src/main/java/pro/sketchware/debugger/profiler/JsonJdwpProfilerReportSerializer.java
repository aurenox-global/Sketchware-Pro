package pro.sketchware.debugger.profiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;

public final class JsonJdwpProfilerReportSerializer {

    public String serialize(String sessionId, List<JdwpProfilerEvent> events) {
        String safeSessionId = sessionId == null ? "" : sessionId;
        List<JdwpProfilerEvent> safeEvents = events == null ? Collections.emptyList() : events;

        JsonObject root = new JsonObject();
        root.addProperty("sessionId", safeSessionId);
        root.addProperty("generatedAtMs", System.currentTimeMillis());

        JsonArray serializedEvents = new JsonArray();
        int cpuCount = 0;
        int memoryCount = 0;
        int networkCount = 0;
        long firstEventTimestamp = 0L;
        long lastEventTimestamp = 0L;

        for (JdwpProfilerEvent event : safeEvents) {
            if (event == null) {
                continue;
            }

            if (firstEventTimestamp == 0L || event.timestampMs < firstEventTimestamp) {
                firstEventTimestamp = event.timestampMs;
            }
            if (event.timestampMs > lastEventTimestamp) {
                lastEventTimestamp = event.timestampMs;
            }

            JsonObject eventJson = new JsonObject();
            eventJson.addProperty("eventId", event.eventId);
            eventJson.addProperty("sessionId", event.sessionId);
            eventJson.addProperty("type", event.type.name());
            eventJson.addProperty("timestampMs", event.timestampMs);

            if (event instanceof JdwpCpuProfilerEvent) {
                JdwpCpuProfilerEvent cpu = (JdwpCpuProfilerEvent) event;
                eventJson.addProperty("processCpuPercent", cpu.processCpuPercent);
                eventJson.addProperty("appCpuPercent", cpu.appCpuPercent);
                eventJson.addProperty("threadCount", cpu.threadCount);
                eventJson.addProperty("sampleDurationMs", cpu.sampleDurationMs);
                cpuCount++;
            } else if (event instanceof JdwpMemoryProfilerEvent) {
                JdwpMemoryProfilerEvent memory = (JdwpMemoryProfilerEvent) event;
                eventJson.addProperty("javaHeapUsedBytes", memory.javaHeapUsedBytes);
                eventJson.addProperty("javaHeapMaxBytes", memory.javaHeapMaxBytes);
                eventJson.addProperty("nativeHeapUsedBytes", memory.nativeHeapUsedBytes);
                eventJson.addProperty("pssBytes", memory.pssBytes);
                memoryCount++;
            } else if (event instanceof JdwpNetworkProfilerEvent) {
                JdwpNetworkProfilerEvent network = (JdwpNetworkProfilerEvent) event;
                eventJson.addProperty("txBytes", network.txBytes);
                eventJson.addProperty("rxBytes", network.rxBytes);
                eventJson.addProperty("activeConnections", network.activeConnections);
                eventJson.addProperty("requestCount", network.requestCount);
                eventJson.addProperty("errorCount", network.errorCount);
                networkCount++;
            }

            serializedEvents.add(eventJson);
        }

        root.addProperty("eventCount", serializedEvents.size());
        JsonObject summary = new JsonObject();
        summary.addProperty("cpuEvents", cpuCount);
        summary.addProperty("memoryEvents", memoryCount);
        summary.addProperty("networkEvents", networkCount);
        summary.addProperty("firstEventTimestampMs", firstEventTimestamp);
        summary.addProperty("lastEventTimestampMs", lastEventTimestamp);

        root.add("summary", summary);
        root.add("events", serializedEvents);
        return root.toString();
    }
}
