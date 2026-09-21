package pro.sketchware.debugger.variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JdwpVariableInspectResult {

    public final long timestampMs;
    public final List<JdwpVariableNode> variables;
    public final boolean partial;
    public final String errorMessage;

    private JdwpVariableInspectResult(long timestampMs,
                                      List<JdwpVariableNode> variables,
                                      boolean partial,
                                      String errorMessage) {
        this.timestampMs = Math.max(timestampMs, 0L);
        this.variables = Collections.unmodifiableList(new ArrayList<>(
                variables == null ? Collections.emptyList() : variables
        ));
        this.partial = partial;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static JdwpVariableInspectResult success(List<JdwpVariableNode> variables, boolean partial) {
        return new JdwpVariableInspectResult(System.currentTimeMillis(), variables, partial, "");
    }

    public static JdwpVariableInspectResult failure(String errorMessage) {
        return new JdwpVariableInspectResult(System.currentTimeMillis(), Collections.emptyList(), false, errorMessage);
    }
}
