package pro.sketchware.debugger.variables;

import java.util.UUID;

public final class JdwpVariableNode {

    public final String variableId;
    public final String parentVariableId;
    public final String sessionId;
    public final String name;
    public final String declaredType;
    public final String evaluateExpression;
    public final JdwpVariableScope scope;
    public final JdwpVariableValue value;

    public JdwpVariableNode(String variableId,
                            String parentVariableId,
                            String sessionId,
                            String name,
                            String declaredType,
                            String evaluateExpression,
                            JdwpVariableScope scope,
                            JdwpVariableValue value) {
        this.variableId = variableId == null || variableId.isEmpty()
                ? UUID.randomUUID().toString()
                : variableId;
        this.parentVariableId = parentVariableId == null ? "" : parentVariableId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.name = name == null ? "" : name;
        this.declaredType = declaredType == null ? "" : declaredType;
        this.evaluateExpression = evaluateExpression == null ? "" : evaluateExpression;
        this.scope = scope == null ? JdwpVariableScope.LOCAL : scope;
        this.value = value == null ? JdwpVariableValue.UNAVAILABLE : value;
    }

    public JdwpVariableNode withValue(JdwpVariableValue newValue) {
        return new JdwpVariableNode(
                variableId,
                parentVariableId,
                sessionId,
                name,
                declaredType,
                evaluateExpression,
                scope,
                newValue
        );
    }
}
