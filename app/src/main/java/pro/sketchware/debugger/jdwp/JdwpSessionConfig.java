package pro.sketchware.debugger.jdwp;

public final class JdwpSessionConfig {

    private static final long MIN_ATTACH_TIMEOUT_MS = 200L;

    public final String projectId;
    public final String packageName;
    public final String host;
    public final int port;
    public final long attachTimeoutMs;

    public JdwpSessionConfig(String projectId,
                             String packageName,
                             String host,
                             int port,
                             long attachTimeoutMs) {
        this.projectId = projectId == null ? "" : projectId;
        this.packageName = packageName == null ? "" : packageName;
        this.host = host == null || host.isEmpty() ? "127.0.0.1" : host;
        this.port = Math.max(port, 0);
        this.attachTimeoutMs = Math.max(MIN_ATTACH_TIMEOUT_MS, attachTimeoutMs);
    }

    public static JdwpSessionConfig local(String projectId, String packageName, int port) {
        return new JdwpSessionConfig(projectId, packageName, "127.0.0.1", port, 1500L);
    }
}
