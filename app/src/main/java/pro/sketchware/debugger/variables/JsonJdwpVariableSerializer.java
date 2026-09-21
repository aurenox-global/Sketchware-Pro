package pro.sketchware.debugger.variables;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class JsonJdwpVariableSerializer implements JdwpVariableSerializer {

    @Override
    public String serialize(JdwpVariableInspectResult result) {
        JdwpVariableInspectResult safeResult = result == null
                ? JdwpVariableInspectResult.failure("No result")
                : result;

        JSONObject root = new JSONObject();
        try {
            root.put("timestampMs", safeResult.timestampMs);
            root.put("partial", safeResult.partial);
            root.put("errorMessage", safeResult.errorMessage);

            JSONArray variables = new JSONArray();
            for (JdwpVariableNode node : safeResult.variables) {
                if (node == null) {
                    continue;
                }

                JSONObject variable = new JSONObject();
                variable.put("variableId", node.variableId);
                variable.put("parentVariableId", node.parentVariableId);
                variable.put("sessionId", node.sessionId);
                variable.put("name", node.name);
                variable.put("declaredType", node.declaredType);
                variable.put("evaluateExpression", node.evaluateExpression);
                variable.put("scope", node.scope.name());

                JdwpVariableValue value = node.value == null ? JdwpVariableValue.UNAVAILABLE : node.value;
                JSONObject valueJson = new JSONObject();
                valueJson.put("kind", value.kind.name());
                valueJson.put("typeName", value.typeName);
                valueJson.put("displayValue", value.displayValue);
                valueJson.put("objectId", value.objectId);
                valueJson.put("expandable", value.expandable);
                valueJson.put("childrenCount", value.childrenCount);

                variable.put("value", valueJson);
                variables.put(variable);
            }

            root.put("variables", variables);
        } catch (JSONException ignored) {
            return "{\"timestampMs\":0,\"partial\":false,\"errorMessage\":\"Serialization failed\",\"variables\":[]}";
        }

        return root.toString();
    }

    @Override
    public JdwpVariableInspectResult deserialize(String payload) {
        if (payload == null || payload.isEmpty()) {
            return JdwpVariableInspectResult.failure("Empty payload");
        }

        try {
            JSONObject root = new JSONObject(payload);
            JSONArray variablesArray = root.optJSONArray("variables");
            List<JdwpVariableNode> variables = new ArrayList<>();

            if (variablesArray != null) {
                for (int index = 0; index < variablesArray.length(); index++) {
                    JSONObject variable = variablesArray.optJSONObject(index);
                    if (variable == null) {
                        continue;
                    }

                    JSONObject valueJson = variable.optJSONObject("value");
                    JdwpVariableValue value = valueFromJson(valueJson);

                    JdwpVariableScope scope;
                    try {
                        scope = JdwpVariableScope.valueOf(variable.optString("scope", JdwpVariableScope.LOCAL.name()));
                    } catch (IllegalArgumentException ignored) {
                        scope = JdwpVariableScope.LOCAL;
                    }

                    variables.add(new JdwpVariableNode(
                            variable.optString("variableId", ""),
                            variable.optString("parentVariableId", ""),
                            variable.optString("sessionId", ""),
                            variable.optString("name", ""),
                            variable.optString("declaredType", ""),
                            variable.optString("evaluateExpression", ""),
                            scope,
                            value
                    ));
                }
            }

            boolean partial = root.optBoolean("partial", false);
            String errorMessage = root.optString("errorMessage", "");
            if (!errorMessage.isEmpty()) {
                return JdwpVariableInspectResult.failure(errorMessage);
            }
            return JdwpVariableInspectResult.success(variables, partial);
        } catch (JSONException e) {
            return JdwpVariableInspectResult.failure("Malformed payload: " + e.getMessage());
        }
    }

    private static JdwpVariableValue valueFromJson(JSONObject json) {
        if (json == null) {
            return JdwpVariableValue.UNAVAILABLE;
        }

        JdwpVariableValueKind kind;
        try {
            kind = JdwpVariableValueKind.valueOf(json.optString("kind", JdwpVariableValueKind.UNAVAILABLE.name()));
        } catch (IllegalArgumentException ignored) {
            kind = JdwpVariableValueKind.UNAVAILABLE;
        }

        return new JdwpVariableValue(
                kind,
                json.optString("typeName", ""),
                json.optString("displayValue", ""),
                json.optString("objectId", ""),
                json.optBoolean("expandable", false),
                json.optInt("childrenCount", 0)
        );
    }
}
