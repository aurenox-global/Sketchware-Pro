package pro.sketchware.debugger.symbolication;

public interface CrashSymbolicationWorkflow {

    String enqueue(CrashSymbolicationRequest request);

    CrashSymbolicationResult symbolicateNow(CrashSymbolicationRequest request);

    CrashSymbolicationResult getResult(String requestId);

    boolean cancel(String requestId);

    void clearSession(String sessionId);

    void clear();
}
