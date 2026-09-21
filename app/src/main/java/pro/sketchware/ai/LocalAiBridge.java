package pro.sketchware.ai;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocalAiBridge implements AutoCloseable {
    private static boolean loadAttempted;
    private static Throwable loadError;
    private static final Map<String, String> ARCHITECTURE_CACHE = new HashMap<>();

    private long handle;

    public static synchronized boolean isNativeAvailable() {
        try {
            ensureNativeLoaded();
            return true;
        } catch (LocalAiException ignored) {
            return false;
        }
    }

    public static synchronized String getNativeStatus() {
        if (isNativeAvailable()) {
            return "Native engine ready: lib" + LocalAiConfig.NATIVE_LIBRARY_NAME + ".so";
        }
        String detail = loadError == null ? "unknown error" : loadError.getMessage();
        return "Native engine missing: lib" + LocalAiConfig.NATIVE_LIBRARY_NAME + ".so (" + detail + ")";
    }

    private static synchronized void ensureNativeLoaded() throws LocalAiException {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                System.loadLibrary(LocalAiConfig.NATIVE_LIBRARY_NAME);
            } catch (Throwable throwable) {
                loadError = throwable;
            }
        }
        if (loadError != null) {
            throw new LocalAiException("Missing llama.cpp native engine. Add lib"
                    + LocalAiConfig.NATIVE_LIBRARY_NAME
                    + ".so for this device ABI, then rebuild/install Sketchware Pro.", loadError);
        }
    }

    public void load(LocalAiConfig config) throws LocalAiException {
        ensureNativeLoaded();
        LocalAiModelInfo.fromPath(config.getModelPath());
        handle = nativeLoadModel(config.getModelPath(), config.getContextSize(), config.getThreads());
        if (handle == 0L) {
            throw new LocalAiException("llama.cpp couldn't load the selected model.");
        }
    }

    public String generate(String prompt, LocalAiConfig config) throws LocalAiException {
        return generate(prompt, config, 0.0f, "");
    }

    public String generate(String prompt, LocalAiConfig config, float presencePenalty, String grammar) throws LocalAiException {
        if (handle == 0L) {
            throw new LocalAiException("Model is not loaded.");
        }
        String architecture = getArchitecture(config.getModelPath());
        boolean isLiquid = architecture != null && architecture.toLowerCase(Locale.US).contains("liquid");
        boolean isQwen = architecture != null && architecture.toLowerCase(Locale.US).contains("qwen");

        int archTopK;
        float repeatPenalty;
        float temperature = config.getTemperature();
        float topP = config.getTopP();
        if (isLiquid) {
            // LFM2.5 recommends top_k=50 and repeat_penalty=1.1. For JSON output
            // (grammar active) clamp sampling so actions stay deterministic.
            archTopK = 50;
            repeatPenalty = 1.1f;
            if (grammar != null && !grammar.isEmpty()) {
                temperature = Math.min(temperature, 0.2f);
                topP = Math.min(topP, 0.95f);
            }
        } else if (isQwen) {
            // Qwen3.5 recommended settings (Unsloth): top_k=20.
            archTopK = 20;
            repeatPenalty = 1.0f;
        } else {
            archTopK = 40;
            repeatPenalty = 1.0f;
        }
        int topK = config.getTopK() > 0 ? config.getTopK() : archTopK;
        float presence = presencePenalty >= 0f ? presencePenalty : config.getPresencePenalty();

        String response = nativeGenerate(handle, prompt, config.getMaxTokens(),
                temperature, topP, presence, repeatPenalty, topK, grammar == null ? "" : grammar);
        return response == null ? "" : response.trim();
    }

    private static String getArchitecture(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            return "";
        }
        synchronized (ARCHITECTURE_CACHE) {
            String cached = ARCHITECTURE_CACHE.get(modelPath);
            if (cached != null) {
                return cached;
            }
            String architecture = "";
            try {
                architecture = LocalAiModelInfo.fromPath(modelPath).getArchitecture();
            } catch (LocalAiException ignored) {
                android.util.Log.d("SketchwarePro", "LocalAiBridge: LocalAiException ignored", ignored);
            }
            ARCHITECTURE_CACHE.put(modelPath, architecture);
            return architecture;
        }
    }

    public static void clearArchitectureCache() {
        synchronized (ARCHITECTURE_CACHE) {
            ARCHITECTURE_CACHE.clear();
        }
    }

    public void cancel() {
        nativeCancel(handle);
    }

    @Override
    public void close() {
        if (handle != 0L) {
            nativeRelease(handle);
            handle = 0L;
        }
    }

    private static native long nativeLoadModel(String modelPath, int contextSize, int threads);

    private static native String nativeGenerate(long handle, String prompt, int maxTokens, float temperature, float topP, float presencePenalty, float repeatPenalty, int topK, String grammar);

    private static native void nativeCancel(long handle);

    private static native void nativeRelease(long handle);
}
