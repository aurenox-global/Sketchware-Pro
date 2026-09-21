package pro.sketchware.debugger.variables;

public interface JdwpVariableSerializer {

    String serialize(JdwpVariableInspectResult result);

    JdwpVariableInspectResult deserialize(String payload);
}
