package pro.sketchware.debugger.jdwp;

import android.util.Log;

import pro.sketchware.debugger.profiler.JdwpProfilerEventBuffer;
import pro.sketchware.debugger.symbolication.CrashSymbolicationWorkflow;
import pro.sketchware.debugger.symbolication.NoOpCrashSymbolicationWorkflow;
import pro.sketchware.debugger.variables.JdwpVariableInspectorTransport;
import pro.sketchware.debugger.variables.NoOpJdwpVariableInspectorTransport;

public final class JdwpRuntimeFactory {

    private static final String TAG = "JdwpRuntimeFactory";

    private static final String[] BRIDGE_CANDIDATES = new String[] {
            "pro.sketchware.debugger.jdwp.runtime.RealJdwpBridge",
            "pro.sketchware.debugger.jdwp.RealJdwpBridge"
    };

    private static final String[] VARIABLE_INSPECTOR_CANDIDATES = new String[] {
            "pro.sketchware.debugger.variables.runtime.RealJdwpVariableInspectorTransport",
            "pro.sketchware.debugger.variables.RealJdwpVariableInspectorTransport"
    };

    private static final String[] SYMBOLICATION_CANDIDATES = new String[] {
            "pro.sketchware.debugger.symbolication.runtime.RealCrashSymbolicationWorkflow",
            "pro.sketchware.debugger.symbolication.RealCrashSymbolicationWorkflow"
    };

        private static final String[] PROFILER_CANDIDATES = new String[] {
            "pro.sketchware.debugger.profiler.runtime.RealJdwpProfilerEventBuffer",
            "pro.sketchware.debugger.profiler.RealJdwpProfilerEventBuffer"
        };

    private JdwpRuntimeFactory() {
    }

    public static JdwpBridge bridgeOrFallback(JdwpBridge bridge) {
        if (bridge != null) {
            return bridge;
        }

        JdwpBridge realBridge = instantiateFirst(JdwpBridge.class, BRIDGE_CANDIDATES);
        return realBridge == null ? new NoOpJdwpBridge() : realBridge;
    }

    public static JdwpVariableInspectorTransport variableInspectorOrFallback(
            JdwpVariableInspectorTransport transport) {
        if (transport != null) {
            return transport;
        }

        JdwpVariableInspectorTransport realTransport = instantiateFirst(
                JdwpVariableInspectorTransport.class,
                VARIABLE_INSPECTOR_CANDIDATES
        );
        return realTransport == null ? new NoOpJdwpVariableInspectorTransport() : realTransport;
    }

    public static CrashSymbolicationWorkflow symbolicationOrFallback(
            CrashSymbolicationWorkflow workflow) {
        if (workflow != null) {
            return workflow;
        }

        CrashSymbolicationWorkflow realWorkflow = instantiateFirst(
                CrashSymbolicationWorkflow.class,
                SYMBOLICATION_CANDIDATES
        );
        return realWorkflow == null ? new NoOpCrashSymbolicationWorkflow() : realWorkflow;
    }

    public static JdwpProfilerEventBuffer profilerBufferOrFallback(JdwpProfilerEventBuffer buffer) {
        if (buffer != null) {
            return buffer;
        }

        JdwpProfilerEventBuffer realBuffer = instantiateFirst(
                JdwpProfilerEventBuffer.class,
                PROFILER_CANDIDATES
        );
        return realBuffer == null ? new JdwpProfilerEventBuffer() : realBuffer;
    }

    private static <T> T instantiateFirst(Class<T> expectedType, String[] candidates) {
        for (String className : candidates) {
            T instance = instantiate(expectedType, className);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    private static <T> T instantiate(Class<T> expectedType, String className) {
        try {
            Class<?> rawClass = Class.forName(className);
            if (!expectedType.isAssignableFrom(rawClass)) {
                Log.w(TAG, "Ignoring incompatible candidate: " + className);
                return null;
            }
            Object instance = rawClass.getConstructor().newInstance();
            return expectedType.cast(instance);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to initialize candidate: " + className, throwable);
            return null;
        }
    }
}