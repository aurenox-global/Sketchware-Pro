package pro.sketchware.debugger.profiler;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class JsonJdwpProfilerReportSerializerTest {

    @Test
    public void serialize_includesSummaryAndTypedEvents() {
        JsonJdwpProfilerReportSerializer serializer = new JsonJdwpProfilerReportSerializer();
        String report = serializer.serialize("session-1", Arrays.asList(
                new JdwpCpuProfilerEvent("session-1", 1000L, 12.5f, 8.2f, 10, 250L),
                new JdwpMemoryProfilerEvent("session-1", 1100L, 1024L, 4096L, 256L, 8192L),
                new JdwpNetworkProfilerEvent("session-1", 1200L, 64L, 32L, 1, 2, 0)
        ));

        assertTrue(report.contains("\"sessionId\":\"session-1\""));
        assertTrue(report.contains("\"eventCount\":3"));
        assertTrue(report.contains("\"cpuEvents\":1"));
        assertTrue(report.contains("\"memoryEvents\":1"));
        assertTrue(report.contains("\"networkEvents\":1"));
        assertTrue(report.contains("\"type\":\"CPU\""));
        assertTrue(report.contains("\"type\":\"MEMORY\""));
        assertTrue(report.contains("\"type\":\"NETWORK\""));
    }
}
