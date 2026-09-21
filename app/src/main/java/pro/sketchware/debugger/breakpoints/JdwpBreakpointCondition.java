package pro.sketchware.debugger.breakpoints;

public final class JdwpBreakpointCondition {

    public static final JdwpBreakpointCondition NONE = new JdwpBreakpointCondition("", 0);

    public final String expression;
    public final int hitCount;

    private JdwpBreakpointCondition(String expression, int hitCount) {
        this.expression = expression == null ? "" : expression;
        this.hitCount = Math.max(hitCount, 0);
    }

    public static JdwpBreakpointCondition of(String expression, int hitCount) {
        String safeExpression = expression == null ? "" : expression.trim();
        if (safeExpression.isEmpty() && hitCount <= 0) {
            return NONE;
        }
        return new JdwpBreakpointCondition(safeExpression, hitCount);
    }

    public boolean isEmpty() {
        return expression.isEmpty() && hitCount == 0;
    }
}
