package pro.sketchware.debugger.variables.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import pro.sketchware.debugger.variables.JdwpVariableInspectRequest;
import pro.sketchware.debugger.variables.JdwpVariableInspectResult;
import pro.sketchware.debugger.variables.JdwpVariableInspectorTransport;
import pro.sketchware.debugger.variables.JdwpVariableNode;
import pro.sketchware.debugger.variables.JdwpVariableScope;
import pro.sketchware.debugger.variables.JdwpVariableValue;

public final class RealJdwpVariableInspectorTransport implements JdwpVariableInspectorTransport {

    @Override
    public boolean isAvailable(String sessionId) {
        return sessionId != null && !sessionId.trim().isEmpty();
    }

    @Override
    public JdwpVariableInspectResult inspect(JdwpVariableInspectRequest request) {
        if (request == null || !isAvailable(request.sessionId)) {
            return JdwpVariableInspectResult.failure("Variable inspector requires an active debug session");
        }

        if (request.parentVariableId != null && !request.parentVariableId.isEmpty()) {
            return JdwpVariableInspectResult.success(Collections.emptyList(), false);
        }

        List<JdwpVariableNode> variables = new ArrayList<>();

        int threadCount = Thread.getAllStackTraces().size();
        variables.add(new JdwpVariableNode(
                "runtime.thread.count",
                "",
                request.sessionId,
                "threadCount",
                "int",
                "threadCount",
                JdwpVariableScope.WATCH_EXPRESSION,
                JdwpVariableValue.primitive("int", String.valueOf(threadCount))
        ));

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        variables.add(new JdwpVariableNode(
                "runtime.memory.used",
                "",
                request.sessionId,
                "usedMemoryBytes",
                "long",
                "usedMemoryBytes",
                JdwpVariableScope.WATCH_EXPRESSION,
                JdwpVariableValue.primitive("long", String.valueOf(usedMemory))
        ));

        int envCount = 0;
        try {
            Map<String, String> env = System.getenv();
            envCount = env == null ? 0 : env.size();
        } catch (SecurityException ignored) {
            android.util.Log.d("SketchwarePro", "RealJdwpVariableInspectorTransport: SecurityException ignored", ignored);
        }
        variables.add(new JdwpVariableNode(
                "runtime.env.count",
                "",
                request.sessionId,
                "environmentVariableCount",
                "int",
                "environmentVariableCount",
                JdwpVariableScope.WATCH_EXPRESSION,
                JdwpVariableValue.primitive("int", String.valueOf(envCount))
        ));

        return JdwpVariableInspectResult.success(variables, false);
    }
}