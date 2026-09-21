package pro.sketchware.debugger.jdwp;

public interface JdwpBridge {

    JdwpDebugSession createSession(JdwpSessionConfig config);
}
