package pro.sketchware.debugger.variables;

public interface JdwpVariableInspectorTransport {

    boolean isAvailable(String sessionId);

    JdwpVariableInspectResult inspect(JdwpVariableInspectRequest request);
}
