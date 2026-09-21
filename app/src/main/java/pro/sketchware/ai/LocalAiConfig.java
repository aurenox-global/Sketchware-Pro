package pro.sketchware.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class LocalAiConfig {
    public static final String PREFS_NAME = "local_ai";
    public static final String NATIVE_LIBRARY_NAME = "sketchware_llama";
    public static final int DEFAULT_CONTEXT_SIZE = 8192;
    public static final int DEFAULT_MAX_TOKENS = 512;
    // Qwen3.5 non-thinking (instruct) recommended settings (Unsloth).
    public static final float DEFAULT_TEMPERATURE = 0.5f;
    public static final float DEFAULT_TOP_P = 0.85f;
    public static final int DEFAULT_TOP_K = 20;
    public static final float DEFAULT_PRESENCE_PENALTY = 1.5f;
    public static final int MAX_CONTEXT_SIZE = 262144;
    public static final int MAX_MAX_TOKENS = 262144;
    public static final int MAX_THREADS = 16;
    public static final float MAX_TEMPERATURE = 2.0f;

    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_CHATGPT = "chatgpt";
    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_KIMI = "kimi";
    public static final String PROVIDER_GLM = "glm";
    public static final String PROVIDER_GITHUB = "github";
    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_MISTRAL = "mistral";
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_CUSTOM = "custom";

    private static final String KEY_MODEL_PATH = "model_path";
    private static final String KEY_CONTEXT_SIZE = "context_size";
    private static final String KEY_THREADS = "threads";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_TOP_P = "top_p";
    private static final String KEY_TOP_K = "top_k";
    private static final String KEY_PRESENCE_PENALTY = "presence_penalty";
        private static final String KEY_PROVIDER_ID = "provider_id";
        private static final String KEY_CLOUD_ENDPOINT = "cloud_endpoint";
        private static final String KEY_CLOUD_MODEL = "cloud_model";
        private static final String KEY_CLOUD_API_KEY = "cloud_api_key";
    private static final String KEY_THINK_MODE = "think_mode";
    private static final String KEY_CHAT_REASONING = "chat_reasoning_enabled";

        private static final String[] CLOUD_PROVIDER_IDS = new String[]{
            PROVIDER_DEEPSEEK,
            PROVIDER_CHATGPT,
            PROVIDER_ANTHROPIC,
            PROVIDER_KIMI,
            PROVIDER_GLM,
            PROVIDER_GITHUB,
            PROVIDER_GROQ,
            PROVIDER_OPENROUTER,
            PROVIDER_MISTRAL,
            PROVIDER_GEMINI,
            PROVIDER_CUSTOM
        };

    private String modelPath;
    private int contextSize;
    private int threads;
    private int maxTokens;
    private float temperature;
    private float topP;
    private int topK;
    private float presencePenalty;
    private String providerId;
    private String cloudEndpoint;
    private String cloudModel;
    private String cloudApiKey;
    private boolean thinkMode;

    public static LocalAiConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        LocalAiConfig config = new LocalAiConfig();
        config.modelPath = prefs.getString(KEY_MODEL_PATH, "");
        config.contextSize = prefs.getInt(KEY_CONTEXT_SIZE, DEFAULT_CONTEXT_SIZE);
        config.threads = prefs.getInt(KEY_THREADS, getDefaultThreads());
        config.maxTokens = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS);
        config.temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE);
        config.topP = prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P);
        config.topK = prefs.getInt(KEY_TOP_K, DEFAULT_TOP_K);
        config.presencePenalty = prefs.getFloat(KEY_PRESENCE_PENALTY, DEFAULT_PRESENCE_PENALTY);
        config.providerId = sanitizeProviderId(prefs.getString(KEY_PROVIDER_ID, PROVIDER_LOCAL));
        config.cloudEndpoint = trimOrEmpty(prefs.getString(KEY_CLOUD_ENDPOINT, getDefaultEndpointForProvider(PROVIDER_DEEPSEEK)));
        config.cloudModel = trimOrEmpty(prefs.getString(KEY_CLOUD_MODEL, getDefaultModelForProvider(PROVIDER_DEEPSEEK)));
        config.thinkMode = prefs.getBoolean(KEY_THINK_MODE, true);
        String legacyCloudApiKey = trimOrEmpty(prefs.getString(KEY_CLOUD_API_KEY, ""));
        config.cloudApiKey = AiSecretStore.readCloudApiKey(context.getApplicationContext());
        if (!legacyCloudApiKey.isEmpty()) {
            prefs.edit().remove(KEY_CLOUD_API_KEY).apply();
        }
        return config;
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_PATH, modelPath == null ? "" : modelPath)
            .putInt(KEY_CONTEXT_SIZE, clampInt(contextSize, 128, MAX_CONTEXT_SIZE))
            .putInt(KEY_THREADS, clampInt(threads, 1, MAX_THREADS))
            .putInt(KEY_MAX_TOKENS, clampInt(maxTokens, 1, MAX_MAX_TOKENS))
            .putFloat(KEY_TEMPERATURE, clampFloat(temperature, 0f, MAX_TEMPERATURE))
            .putFloat(KEY_TOP_P, clampFloat(topP, 0.01f, 1f))
            .putInt(KEY_TOP_K, clampInt(topK, 1, 1000))
            .putFloat(KEY_PRESENCE_PENALTY, clampFloat(presencePenalty, 0f, 2f))
            .putString(KEY_PROVIDER_ID, sanitizeProviderId(providerId))
            .putString(KEY_CLOUD_ENDPOINT, trimOrEmpty(cloudEndpoint))
            .putString(KEY_CLOUD_MODEL, trimOrEmpty(cloudModel))
            .putBoolean(KEY_THINK_MODE, thinkMode)
            .remove(KEY_CLOUD_API_KEY)
            .apply();
        AiSecretStore.writeCloudApiKey(context.getApplicationContext(), trimOrEmpty(cloudApiKey));
    }

    public static String[] getCloudProviderIds() {
        return CLOUD_PROVIDER_IDS.clone();
    }

    public static String sanitizeProviderId(String providerId) {
        String normalized = trimOrEmpty(providerId).toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return PROVIDER_LOCAL;
        }
        if (PROVIDER_LOCAL.equals(normalized)
                || PROVIDER_DEEPSEEK.equals(normalized)
                || PROVIDER_CHATGPT.equals(normalized)
                || PROVIDER_ANTHROPIC.equals(normalized)
                || PROVIDER_KIMI.equals(normalized)
                || PROVIDER_GLM.equals(normalized)
                || PROVIDER_GITHUB.equals(normalized)
                || PROVIDER_GROQ.equals(normalized)
                || PROVIDER_OPENROUTER.equals(normalized)
                || PROVIDER_MISTRAL.equals(normalized)
                || PROVIDER_GEMINI.equals(normalized)
                || PROVIDER_CUSTOM.equals(normalized)) {
            return normalized;
        }
        return PROVIDER_LOCAL;
    }

    public static String getProviderDisplayName(String providerId) {
        String normalized = sanitizeProviderId(providerId);
        switch (normalized) {
            case PROVIDER_DEEPSEEK:
                return "DeepSeek";
            case PROVIDER_CHATGPT:
                return "ChatGPT (OpenAI)";
            case PROVIDER_ANTHROPIC:
                return "Anthropic";
            case PROVIDER_KIMI:
                return "Kimi (Moonshot)";
            case PROVIDER_GLM:
                return "GLM";
            case PROVIDER_GITHUB:
                return "GitHub Models";
            case PROVIDER_GROQ:
                return "Groq";
            case PROVIDER_OPENROUTER:
                return "OpenRouter";
            case PROVIDER_MISTRAL:
                return "Mistral";
            case PROVIDER_GEMINI:
                return "Gemini (Google)";
            case PROVIDER_CUSTOM:
                return "OpenAI-compatible";
            default:
                return "Local model (GGUF)";
        }
    }

    public static String getDefaultEndpointForProvider(String providerId) {
        String normalized = sanitizeProviderId(providerId);
        switch (normalized) {
            case PROVIDER_DEEPSEEK:
                return "https://api.deepseek.com/chat/completions";
            case PROVIDER_CHATGPT:
                return "https://api.openai.com/v1/chat/completions";
            case PROVIDER_ANTHROPIC:
                return "https://api.anthropic.com/v1/messages";
            case PROVIDER_KIMI:
                return "https://api.moonshot.cn/v1/chat/completions";
            case PROVIDER_GLM:
                return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case PROVIDER_GITHUB:
                return "https://models.inference.ai.azure.com/chat/completions";
            case PROVIDER_GROQ:
                return "https://api.groq.com/openai/v1/chat/completions";
            case PROVIDER_OPENROUTER:
                return "https://openrouter.ai/api/v1/chat/completions";
            case PROVIDER_MISTRAL:
                return "https://api.mistral.ai/v1/chat/completions";
            case PROVIDER_GEMINI:
                return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            default:
                return "";
        }
    }

    public static String getDefaultModelForProvider(String providerId) {
        String normalized = sanitizeProviderId(providerId);
        switch (normalized) {
            case PROVIDER_DEEPSEEK:
                return "deepseek-chat";
            case PROVIDER_CHATGPT:
                return "gpt-4o-mini";
            case PROVIDER_ANTHROPIC:
                return "claude-3-5-sonnet-latest";
            case PROVIDER_KIMI:
                return "moonshot-v1-8k";
            case PROVIDER_GLM:
                return "glm-4-flash";
            case PROVIDER_GITHUB:
                return "gpt-4.1-mini";
            case PROVIDER_GROQ:
                return "llama-3.3-70b-versatile";
            case PROVIDER_OPENROUTER:
                return "deepseek/deepseek-chat";
            case PROVIDER_MISTRAL:
                return "mistral-small-latest";
            case PROVIDER_GEMINI:
                return "gemini-2.0-flash";
            default:
                return "";
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean isCustomProvider(String providerId) {
        return PROVIDER_CUSTOM.equals(sanitizeProviderId(providerId));
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static int getDefaultThreads() {
        return Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors() - 1));
    }

    public static File getModelsDirectory() {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/ai/models");
    }

    public static File copyModelToModelsDirectory(File source) throws IOException {
        return copyModelToModelsDirectory(source, null);
    }

    public static File copyModelToModelsDirectory(File source, CopyProgressCallback callback) throws IOException {
        File modelsDirectory = getModelsDirectory();
        if (!modelsDirectory.exists() && !modelsDirectory.mkdirs()) {
            throw new IOException("Couldn't create " + modelsDirectory.getAbsolutePath());
        }

        File target = new File(modelsDirectory, source.getName());
        if (!source.getAbsolutePath().equals(target.getAbsolutePath())) {
            File temporaryTarget = new File(modelsDirectory, source.getName() + ".part");
            long totalBytes = Math.max(1L, source.length());
            long copiedBytes = 0L;
            try (FileInputStream inputStream = new FileInputStream(source);
                 FileOutputStream outputStream = new FileOutputStream(temporaryTarget, false)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, read);
                    copiedBytes += read;
                    if (callback != null) {
                        callback.onProgress(copiedBytes, totalBytes);
                    }
                }
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("Couldn't replace existing model " + target.getAbsolutePath());
            }
            if (!temporaryTarget.renameTo(target)) {
                throw new IOException("Couldn't move imported model to " + target.getAbsolutePath());
            }
        } else if (callback != null) {
            callback.onProgress(source.length(), Math.max(1L, source.length()));
        }
        return target;
    }

    public static File copyModelToModelsDirectory(Context context, Uri sourceUri, CopyProgressCallback callback) throws IOException {
        File modelsDirectory = getModelsDirectory();
        if (!modelsDirectory.exists() && !modelsDirectory.mkdirs()) {
            throw new IOException("Couldn't create " + modelsDirectory.getAbsolutePath());
        }

        String displayName = sanitizeModelFileName(getUriDisplayName(context, sourceUri));
        if (displayName.isEmpty()) {
            displayName = "model_" + System.currentTimeMillis() + ".gguf";
        } else if (!displayName.toLowerCase(Locale.US).endsWith(".gguf")) {
            displayName = displayName + ".gguf";
        }

        File target = new File(modelsDirectory, displayName);
        File temporaryTarget = new File(modelsDirectory, displayName + ".part");
        long totalBytes = getUriSize(context, sourceUri);
        long copiedBytes = 0L;

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(temporaryTarget, false)) {
            if (inputStream == null) {
                throw new IOException("Couldn't open selected model file.");
            }
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
                copiedBytes += read;
                if (callback != null) {
                    callback.onProgress(copiedBytes, totalBytes);
                }
            }
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("Couldn't replace existing model " + target.getAbsolutePath());
        }
        if (!temporaryTarget.renameTo(target)) {
            throw new IOException("Couldn't move imported model to " + target.getAbsolutePath());
        }
        return target;
    }

    private static String getUriDisplayName(Context context, Uri sourceUri) {
        try (Cursor cursor = context.getContentResolver().query(sourceUri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    String displayName = cursor.getString(columnIndex);
                    return displayName == null ? "" : displayName;
                }
            }
        } catch (RuntimeException ignored) {
            android.util.Log.d("SketchwarePro", "LocalAiConfig: RuntimeException ignored", ignored);
        }

        String path = sourceUri.getLastPathSegment();
        if (path == null) {
            return "";
        }
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static long getUriSize(Context context, Uri sourceUri) {
        try (Cursor cursor = context.getContentResolver().query(sourceUri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (columnIndex >= 0) {
                    return cursor.getLong(columnIndex);
                }
            }
        } catch (RuntimeException ignored) {
            android.util.Log.d("SketchwarePro", "LocalAiConfig: RuntimeException ignored", ignored);
        }
        return -1L;
    }

    private static String sanitizeModelFileName(String displayName) {
        if (displayName == null) {
            return "";
        }
        return displayName.replaceAll("[^A-Za-z0-9._ -]+", "_").trim();
    }

    public interface CopyProgressCallback {
        void onProgress(long copiedBytes, long totalBytes);
    }

    public boolean hasModel() {
        return modelPath != null && !modelPath.trim().isEmpty() && new File(modelPath).isFile();
    }

    public String getModelPath() {
        return modelPath == null ? "" : modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath == null ? "" : modelPath;
    }

    public String getModelName() {
        return hasModel() ? new File(modelPath).getName() : "No model selected";
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(int contextSize) {
        this.contextSize = contextSize;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getTopP() {
        return topP;
    }

    public void setTopP(float topP) {
        this.topP = topP;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public float getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(float presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public String getProviderId() {
        return sanitizeProviderId(providerId);
    }

    public void setProviderId(String providerId) {
        this.providerId = sanitizeProviderId(providerId);
    }

    public boolean isCloudProvider() {
        return !PROVIDER_LOCAL.equals(getProviderId());
    }

    public String getCloudEndpoint() {
        return trimOrEmpty(cloudEndpoint);
    }

    public void setCloudEndpoint(String cloudEndpoint) {
        this.cloudEndpoint = trimOrEmpty(cloudEndpoint);
    }

    public String getCloudModel() {
        return trimOrEmpty(cloudModel);
    }

    public void setCloudModel(String cloudModel) {
        this.cloudModel = trimOrEmpty(cloudModel);
    }

    public String getCloudApiKey() {
        return trimOrEmpty(cloudApiKey);
    }

    public void setCloudApiKey(String cloudApiKey) {
        this.cloudApiKey = trimOrEmpty(cloudApiKey);
    }

    public String resolveCloudEndpoint() {
        String configured = getCloudEndpoint();
        return configured.isEmpty() ? getDefaultEndpointForProvider(getProviderId()) : configured;
    }

    public String resolveCloudModel() {
        String configured = getCloudModel();
        return configured.isEmpty() ? getDefaultModelForProvider(getProviderId()) : configured;
    }

    public String getMaskedCloudApiKey() {
        String key = getCloudApiKey();
        if (key.length() < 8) {
            return key.isEmpty() ? "Not set" : "******";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }

    public boolean isThinkMode() {
        return thinkMode;
    }

    public void setThinkMode(boolean thinkMode) {
        this.thinkMode = thinkMode;
    }

    public static boolean loadChatReasoning(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHAT_REASONING, true);
    }

    public static void saveChatReasoning(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CHAT_REASONING, enabled)
                .apply();
    }
}