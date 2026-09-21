package pro.sketchware.debugger.symbolication.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.debugger.symbolication.CrashSymbolicationRequest;
import pro.sketchware.debugger.symbolication.CrashSymbolicationResult;
import pro.sketchware.debugger.symbolication.CrashSymbolicationStatus;

public class RealCrashSymbolicationWorkflowTest {

    @Test
    public void symbolicateNow_withValidStacktrace_returnsSuccessOrPartial() {
        RealCrashSymbolicationWorkflow workflow = new RealCrashSymbolicationWorkflow();
        CrashSymbolicationRequest request = CrashSymbolicationRequest.fromStacktrace(
                "session-1",
                "project-1",
                "pro.sketchware",
                "debug",
                "",
                "java.lang.RuntimeException: boom\n"
                    + "    at pro.sketchware.MainActivity.onCreate(MainActivity.java:42)\n"
                        + "    at android.app.Activity.performCreate(Activity.java:8076)"
        );

        CrashSymbolicationResult result = workflow.symbolicateNow(request);

        assertTrue(result.status == CrashSymbolicationStatus.SUCCESS
                || result.status == CrashSymbolicationStatus.PARTIAL);
        assertEquals(request.requestId, result.requestId);
        assertFalse(result.frames.isEmpty());
        assertNotNull(result.symbolicatedStacktrace);
    }

    @Test
    public void symbolicateNow_withEmptyStacktrace_returnsFailure() {
        RealCrashSymbolicationWorkflow workflow = new RealCrashSymbolicationWorkflow();
        CrashSymbolicationRequest request = CrashSymbolicationRequest.fromStacktrace(
                "session-1",
                "project-1",
                "pro.sketchware",
                "debug",
                "",
                ""
        );

        CrashSymbolicationResult result = workflow.symbolicateNow(request);

        assertEquals(CrashSymbolicationStatus.FAILED, result.status);
        assertTrue(result.errorMessage != null && !result.errorMessage.isEmpty());
    }
}
