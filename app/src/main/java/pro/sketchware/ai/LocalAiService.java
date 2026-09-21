package pro.sketchware.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalAiService {
    private static final LocalAiService INSTANCE = new LocalAiService();

    /**
     * GBNF grammar (official llama.cpp json.gbnf) that constrains local generation
     * to a valid JSON object: {"reply": "...", "actions": [{...}, ...]}
     */
    public static final String AGENT_JSON_GRAMMAR =
            "root   ::= object\n"
                    + "value  ::= object | array | string | number | (\"true\" | \"false\" | \"null\") ws\n"
                    + "object ::= \"{\" ws (string \":\" ws value (\",\" ws string \":\" ws value)*)? \"}\" ws\n"
                    + "array  ::= \"[\" ws (value (\",\" ws value)*)? \"]\" ws\n"
                    + "string ::= \"\\\"\" ([^\"\\\\\\x7F\\x00-\\x1F] | \"\\\\\" ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4}))* \"\\\"\" ws\n"
                    + "number ::= (\"-\"? ([0-9] | [1-9] [0-9]{0,15})) (\".\" [0-9]+)? ([eE] [-+]? [0-9] [1-9]{0,15})? ws\n"
                    + "ws ::= | \" \" | \"\\n\" [ \\t]{0,20}\n";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object activeBridgeLock = new Object();
    private LocalAiBridge activeBridge;
    private LocalAiBridge loadedBridge;
    private String loadedSignature = "";

    public static LocalAiService getInstance() {
        return INSTANCE;
    }

    public void generate(Context context, String prompt, Callback callback) {
        generate(context, prompt, true, callback);
    }

    public void generate(Context context, String prompt, boolean reasoningEnabled, Callback callback) {
        generateWithConfig(context, prompt, reasoningEnabled, null, callback);
    }

    public void generateWithConfig(Context context, String prompt, boolean reasoningEnabled, LocalAiConfig config, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = null;
            String signature = null;
            boolean success = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig cfg = config == null ? LocalAiConfig.load(appContext) : config;
                if (cfg.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select a local .gguf model in Local AI Manager first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(cfg.getModelPath());
                signature = createLoadSignature(cfg);
                bridge = acquireBridge(cfg, signature, callback);

                post(() -> callback.onStatus("Model loaded. Context: " + cfg.getContextSize()
                        + " | Threads: " + cfg.getThreads()
                        + " | Max tokens: " + cfg.getMaxTokens()
                        + "\nEvaluating prompt..."));
                String formattedPrompt = LocalAiPromptFormatter.format(prompt, cfg, reasoningEnabled);
                String result = bridge.generate(formattedPrompt, cfg, cfg.getPresencePenalty(), "");
                success = true;
                post(() -> callback.onStatus("Generation complete."));
                post(() -> callback.onSuccess(result));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                finishBridge(bridge, signature, success);
                post(callback::onFinished);
            }
        });
    }

    /**
     * Generates with JSON output constrained by a grammar (local models).
     * Reasoning is forced off so the chat template does not open a think block
     * that would violate the JSON grammar.
     */
    public void generateJson(Context context, String prompt, Callback callback) {
        generateJsonWithConfig(context, prompt, null, callback);
    }

    public void generateJsonWithConfig(Context context, String prompt, LocalAiConfig config, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = null;
            String signature = null;
            boolean success = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig cfg = config == null ? LocalAiConfig.load(appContext) : config;
                if (cfg.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select a local .gguf model in Local AI Manager first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(cfg.getModelPath());
                signature = createLoadSignature(cfg);
                bridge = acquireBridge(cfg, signature, callback);

                post(() -> callback.onStatus("Model loaded. Context: " + cfg.getContextSize()
                        + " | Threads: " + cfg.getThreads()
                        + " | Max tokens: " + cfg.getMaxTokens()
                        + "\nGenerating agent actions (JSON)..."));
                String formattedPrompt = LocalAiPromptFormatter.format(prompt, cfg, false);
                // Qwen3.5 non-thinking instruct recommends presence_penalty = 1.5.
                String result = bridge.generate(formattedPrompt, cfg, cfg.getPresencePenalty(), AGENT_JSON_GRAMMAR);
                if (result == null || result.trim().isEmpty()) {
                    // The grammar may have forced an early stop; retry without it.
                    post(() -> callback.onStatus("Retrying without JSON grammar..."));
                    result = bridge.generate(formattedPrompt, cfg);
                }
                success = true;
                final String finalResult = result;
                post(() -> callback.onStatus("Generation complete."));
                post(() -> callback.onSuccess(finalResult));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                finishBridge(bridge, signature, success);
                post(callback::onFinished);
            }
        });
    }

    /**
     * Reuses the model kept in RAM when the requested model/config match the
     * loaded signature; otherwise loads a new bridge (which stays cached on
     * success so the next request skips the slow model load entirely).
     */
    private LocalAiBridge acquireBridge(LocalAiConfig config, String signature, Callback callback) throws LocalAiException {
        synchronized (activeBridgeLock) {
            if (loadedBridge != null && signature.equals(loadedSignature)) {
                LocalAiBridge loaded = loadedBridge;
                activeBridge = loaded;
                post(() -> callback.onStatus("Using model loaded in RAM: " + config.getModelName()));
                return loaded;
            }
        }
        LocalAiBridge bridge = new LocalAiBridge();
        LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(config.getModelPath());
        post(() -> callback.onStatus("Loading model: " + config.getModelName() + "\n" + modelInfo.getDisplaySummary()));
        synchronized (activeBridgeLock) {
            activeBridge = bridge;
        }
        bridge.load(config);
        return bridge;
    }

    private void finishBridge(LocalAiBridge bridge, String signature, boolean success) {
        synchronized (activeBridgeLock) {
            if (activeBridge == bridge) {
                activeBridge = null;
            }
            if (success) {
                // Keep the model in RAM: next request reuses it (huge latency win).
                if (loadedBridge != null && loadedBridge != bridge) {
                    loadedBridge.close();
                }
                loadedBridge = bridge;
                loadedSignature = signature;
            } else if (bridge != null && loadedBridge != bridge) {
                bridge.close();
            }
        }
    }

    public void loadModel(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = new LocalAiBridge();
            boolean keepBridgeLoaded = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig config = LocalAiConfig.load(appContext);
                if (config.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select or import a local .gguf model first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(config.getModelPath());
                String signature = createLoadSignature(config);
                synchronized (activeBridgeLock) {
                    if (loadedBridge != null && signature.equals(loadedSignature)) {
                        post(() -> callback.onStatus("Model already loaded in RAM: " + config.getModelName()));
                        post(() -> callback.onSuccess("Model already loaded in RAM.\n" + modelInfo.getDisplaySummary()));
                        return;
                    }
                    activeBridge = bridge;
                }

                post(() -> callback.onStatus("Loading model into RAM: " + config.getModelName() + "\n" + modelInfo.getDisplaySummary()));
                bridge.load(config);
                synchronized (activeBridgeLock) {
                    if (loadedBridge != null) {
                        loadedBridge.close();
                    }
                    loadedBridge = bridge;
                    loadedSignature = signature;
                    keepBridgeLoaded = true;
                    activeBridge = null;
                }
                post(() -> callback.onStatus("Model loaded in RAM."));
                post(() -> callback.onSuccess("Model loaded in RAM.\n" + modelInfo.getDisplaySummary()));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                synchronized (activeBridgeLock) {
                    if (activeBridge == bridge) {
                        activeBridge = null;
                    }
                }
                if (!keepBridgeLoaded) {
                    bridge.close();
                }
                post(callback::onFinished);
            }
        });
    }

    public String getLoadedModelStatus(Context context) {
        LocalAiConfig config = LocalAiConfig.load(context.getApplicationContext());
        String signature = createLoadSignature(config);
        synchronized (activeBridgeLock) {
            if (loadedBridge != null && signature.equals(loadedSignature)) {
                return "Loaded in RAM: " + config.getModelName();
            }
            if (loadedBridge != null) {
                return "A different model/config is loaded in RAM. Tap Use to reload this one.";
            }
        }
        return "Not loaded in RAM. The first request will load it and keep it in RAM afterwards.";
    }

    public void cancel() {
        synchronized (activeBridgeLock) {
            if (activeBridge != null) {
                activeBridge.cancel();
            }
        }
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private String createLoadSignature(LocalAiConfig config) {
        return config.getModelPath() + "|" + config.getContextSize() + "|" + config.getThreads();
    }

    public interface Callback {
        void onStarted();

        default void onStatus(String status) {
        }

        void onSuccess(String response);

        void onError(Throwable throwable);

        void onFinished();
    }
}
