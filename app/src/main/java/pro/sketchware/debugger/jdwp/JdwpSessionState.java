package pro.sketchware.debugger.jdwp;

public enum JdwpSessionState {
    CREATED,
    STARTING,
    ATTACHED,
    STOPPING,
    TERMINATED,
    FAILED
}
