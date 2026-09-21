package pro.sketchware.debugger.symbolication;

public enum CrashSymbolicationStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELED
}
