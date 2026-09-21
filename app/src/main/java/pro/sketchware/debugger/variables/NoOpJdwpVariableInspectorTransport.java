package pro.sketchware.debugger.variables;

import java.util.Collections;

public final class NoOpJdwpVariableInspectorTransport implements JdwpVariableInspectorTransport {

    @Override
    public boolean isAvailable(String sessionId) {
        return false;
    }

    @Override
    public JdwpVariableInspectResult inspect(JdwpVariableInspectRequest request) {
        JdwpVariableInspectRequest safeRequest = request == null
                ? JdwpVariableInspectRequest.forFrame("", 0L, 0)
                : request;

        if (safeRequest.parentVariableId != null && !safeRequest.parentVariableId.isEmpty()) {
            return JdwpVariableInspectResult.success(Collections.emptyList(), false);
        }

        return JdwpVariableInspectResult.failure("Variable inspector transport is not connected");
    }
}
