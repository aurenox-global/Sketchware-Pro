package pro.sketchware.lsp;

import android.content.Context;
import android.util.Log;

public final class LspClientFactory {

    private static final String TAG = "LspClientFactory";
    private static final String[] REAL_CLIENT_CANDIDATES = new String[] {
            "pro.sketchware.lsp.runtime.RealLspClient",
            "pro.sketchware.lsp.RealLspClient"
    };

    private LspClientFactory() {
    }

    public static LspClient createDefault(Context context) {
        LspClient realClient = tryCreateRealClient(context);
        if (realClient != null) {
            return realClient;
        }
        return new NoOpLspClient(context);
    }

    public static LspClient createDefault() {
        LspClient realClient = tryCreateRealClient(null);
        if (realClient != null) {
            return realClient;
        }
        return new NoOpLspClient(null);
    }

    private static LspClient tryCreateRealClient(Context context) {
        for (String className : REAL_CLIENT_CANDIDATES) {
            LspClient client = tryInstantiateClient(className, context);
            if (client != null) {
                return client;
            }
        }
        return null;
    }

    private static LspClient tryInstantiateClient(String className, Context context) {
        try {
            Class<?> candidateClass = Class.forName(className);
            if (!LspClient.class.isAssignableFrom(candidateClass)) {
                Log.w(TAG, "Ignoring non-LspClient candidate: " + className);
                return null;
            }

            try {
                return (LspClient) candidateClass.getConstructor(Context.class).newInstance(context);
            } catch (NoSuchMethodException ignored) {
                return (LspClient) candidateClass.getConstructor().newInstance();
            }
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to initialize real LSP client " + className, throwable);
            return null;
        }
    }
}
