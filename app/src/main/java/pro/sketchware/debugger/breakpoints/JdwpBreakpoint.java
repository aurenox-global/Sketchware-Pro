package pro.sketchware.debugger.breakpoints;

import java.util.UUID;

public final class JdwpBreakpoint {

    public final String breakpointId;
    public final String sessionId;
    public final JdwpBreakpointType type;
    public final String className;
    public final String sourcePath;
    public final int lineNumber;
    public final String symbolName;
    public final boolean enabled;
    public final JdwpBreakpointCondition condition;
    public final long createdAtMs;
    public final long updatedAtMs;

    private JdwpBreakpoint(String breakpointId,
                           String sessionId,
                           JdwpBreakpointType type,
                           String className,
                           String sourcePath,
                           int lineNumber,
                           String symbolName,
                           boolean enabled,
                           JdwpBreakpointCondition condition,
                           long createdAtMs,
                           long updatedAtMs) {
        this.breakpointId = breakpointId == null || breakpointId.isEmpty()
                ? UUID.randomUUID().toString()
                : breakpointId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.type = type == null ? JdwpBreakpointType.LINE : type;
        this.className = className == null ? "" : className;
        this.sourcePath = sourcePath == null ? "" : sourcePath;
        this.lineNumber = Math.max(0, lineNumber);
        this.symbolName = symbolName == null ? "" : symbolName;
        this.enabled = enabled;
        this.condition = condition == null ? JdwpBreakpointCondition.NONE : condition;
        this.createdAtMs = Math.max(0L, createdAtMs);
        this.updatedAtMs = Math.max(this.createdAtMs, updatedAtMs);
    }

    public static JdwpBreakpoint line(String sessionId,
                                      String className,
                                      String sourcePath,
                                      int lineNumber,
                                      JdwpBreakpointCondition condition) {
        long now = System.currentTimeMillis();
        return new JdwpBreakpoint(
                "",
                sessionId,
                JdwpBreakpointType.LINE,
                className,
                sourcePath,
                lineNumber,
                "",
                true,
                condition,
                now,
                now
        );
    }

    public static JdwpBreakpoint method(String sessionId,
                                        String className,
                                        String methodName,
                                        JdwpBreakpointCondition condition) {
        long now = System.currentTimeMillis();
        return new JdwpBreakpoint(
                "",
                sessionId,
                JdwpBreakpointType.METHOD,
                className,
                "",
                0,
                methodName,
                true,
                condition,
                now,
                now
        );
    }

    public static JdwpBreakpoint exception(String sessionId,
                                           String exceptionClassName,
                                           JdwpBreakpointCondition condition) {
        long now = System.currentTimeMillis();
        return new JdwpBreakpoint(
                "",
                sessionId,
                JdwpBreakpointType.EXCEPTION,
                "",
                "",
                0,
                exceptionClassName,
                true,
                condition,
                now,
                now
        );
    }

    public JdwpBreakpoint withEnabled(boolean enabled) {
        return new JdwpBreakpoint(
                breakpointId,
                sessionId,
                type,
                className,
                sourcePath,
                lineNumber,
                symbolName,
                enabled,
                condition,
                createdAtMs,
                System.currentTimeMillis()
        );
    }

    public JdwpBreakpoint withCondition(JdwpBreakpointCondition condition) {
        return new JdwpBreakpoint(
                breakpointId,
                sessionId,
                type,
                className,
                sourcePath,
                lineNumber,
                symbolName,
                enabled,
                condition,
                createdAtMs,
                System.currentTimeMillis()
        );
    }
}
