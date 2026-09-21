package pro.sketchware.debugger.jdwp;

public interface JdwpDebugSession {

    String sessionId();

    JdwpSessionConfig config();

    JdwpSessionState state();

    long createdAtMs();

    long startedAtMs();

    long stoppedAtMs();

    String lastError();

    boolean start();

    boolean stop();
}
