package pro.sketchware.debugger.profiler.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import pro.sketchware.debugger.profiler.JdwpProfilerEvent;
import pro.sketchware.debugger.profiler.JdwpProfilerEventType;

public class RealJdwpProfilerEventBufferTest {

    @Test
    public void recordRuntimeSample_withSession_recordsCpuMemoryNetworkEvents() {
        RealJdwpProfilerEventBuffer buffer = new RealJdwpProfilerEventBuffer();

        boolean recorded = buffer.recordRuntimeSample("session-1");
        List<JdwpProfilerEvent> events = buffer.snapshot("session-1", 10);

        assertTrue(recorded);
        assertEquals(3, events.size());
        assertEquals(JdwpProfilerEventType.CPU, events.get(0).type);
        assertEquals(JdwpProfilerEventType.MEMORY, events.get(1).type);
        assertEquals(JdwpProfilerEventType.NETWORK, events.get(2).type);
    }

    @Test
    public void recordRuntimeSample_withEmptySession_returnsFalse() {
        RealJdwpProfilerEventBuffer buffer = new RealJdwpProfilerEventBuffer();

        boolean recorded = buffer.recordRuntimeSample("   ");

        assertFalse(recorded);
    }
}
