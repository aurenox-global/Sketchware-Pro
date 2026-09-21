package pro.sketchware.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudAiService {
    private static final CloudAiService INSTANCE = new CloudAiService();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .build();
    private final Object activeCallLock = new Object();
    private Call activeCall;

    public static CloudAiService getInstance() {
        return INSTANCE;
    }

    public void generate(Context context, String prompt, LocalAiService.Callback callback) {
        generateImpl(context, prompt, false, callback);
    }

    /**
     * Requests strict JSON output via response_format when the provider supports
     * it (OpenAI-compatible). Anthropic falls back to a plain prompt request.
     */
    public void generateJson(Context context, String prompt, LocalAiService.Callback callback) {
        generateImpl(context, prompt, true, callback);
    }

    private void generateImpl(Context context,
                              String prompt,
                              boolean jsonMode,
                              LocalAiService.Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            Call call = null;
            try {
                LocalAiConfig config = LocalAiConfig.load(appContext);
                if (!config.isCloudProvider()) {
                    throw new LocalAiException("Switch source to a cloud provider first.");
                }

                String providerId = config.getProviderId();
                String providerName = LocalAiConfig.getProviderDisplayName(providerId);
                String endpoint = config.resolveCloudEndpoint();
                String model = config.resolveCloudModel();
                String apiKey = config.getCloudApiKey();

                if (endpoint.isEmpty()) {
                    throw new LocalAiException("Set a cloud endpoint for " + providerName + ".");
                }
                if (model.isEmpty()) {
                    throw new LocalAiException("Set a cloud model for " + providerName + ".");
                }
                if (apiKey.isEmpty() && !LocalAiConfig.isCustomProvider(providerId)) {
                    throw new LocalAiException("Add the API key in Local AI Manager before using " + providerName + ".");
                }

                post(() -> callback.onStatus("Connecting to " + providerName + "..."));

                boolean forceJson = jsonMode && !LocalAiConfig.PROVIDER_ANTHROPIC.equals(providerId);
                JSONObject payload = buildPayload(providerId, prompt, model, config, forceJson);
                Request request = buildRequest(providerId, endpoint, apiKey, payload.toString());
                call = httpClient.newCall(request);
                synchronized (activeCallLock) {
                    activeCall = call;
                }

                try (Response response = call.execute()) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        throw new LocalAiException(formatHttpError(response.code(), responseBody));
                    }
                    String answer = parseAssistantResponse(providerId, responseBody).trim();
                    if (answer.isEmpty()) {
                        throw new LocalAiException("The provider returned an empty response.");
                    }
                    post(() -> callback.onStatus("Response received from " + providerName + "."));
                    post(() -> callback.onSuccess(answer));
                }
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                synchronized (activeCallLock) {
                    if (activeCall == call) {
                        activeCall = null;
                    }
                }
                post(callback::onFinished);
            }
        });
    }

    public void cancel() {
        synchronized (activeCallLock) {
            if (activeCall != null) {
                activeCall.cancel();
            }
        }
    }

    private JSONObject buildPayload(String providerId,
                                    String prompt,
                                    String model,
                                    LocalAiConfig config,
                                    boolean forceJson) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", config.getTemperature());
        payload.put("top_p", config.getTopP());

        if (LocalAiConfig.PROVIDER_ANTHROPIC.equals(providerId)) {
            payload.put("max_tokens", config.getMaxTokens());
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", prompt));
            payload.put("messages", messages);
            return payload;
        }

        payload.put("max_tokens", config.getMaxTokens());
        if (forceJson) {
            payload.put("response_format", new JSONObject().put("type", "json_object"));
        }
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt));
        payload.put("messages", messages);
        return payload;
    }

    private Request buildRequest(String providerId,
                                 String endpoint,
                                 String apiKey,
                                 String body) {
        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(body, JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json");

        if (LocalAiConfig.PROVIDER_ANTHROPIC.equals(providerId)) {
            builder.addHeader("x-api-key", apiKey);
            builder.addHeader("anthropic-version", "2023-06-01");
        } else if (!apiKey.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }

        if (LocalAiConfig.PROVIDER_GITHUB.equals(providerId)) {
            builder.addHeader("X-GitHub-Api-Version", "2022-11-28");
        }

        return builder.build();
    }

    @NonNull
    private String parseAssistantResponse(String providerId, String responseBody) throws LocalAiException {
        try {
            JSONObject root = new JSONObject(responseBody);
            if (LocalAiConfig.PROVIDER_ANTHROPIC.equals(providerId)) {
                return parseAnthropicResponse(root);
            }
            return parseOpenAiCompatibleResponse(root);
        } catch (JSONException e) {
            throw new LocalAiException("Unable to parse provider response.", e);
        }
    }

    private String parseAnthropicResponse(JSONObject root) {
        JSONArray content = root.optJSONArray("content");
        if (content != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String text = item.optString("text", "");
                if (!text.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            if (builder.length() > 0) {
                return builder.toString();
            }
        }
        return root.optString("completion", "");
    }

    private String parseOpenAiCompatibleResponse(JSONObject root) {
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return "";
        }

        JSONObject firstChoice = choices.optJSONObject(0);
        if (firstChoice == null) {
            return "";
        }

        JSONObject message = firstChoice.optJSONObject("message");
        if (message != null) {
            Object content = message.opt("content");
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof JSONArray) {
                StringBuilder builder = new StringBuilder();
                JSONArray array = (JSONArray) content;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String text = item.optString("text", "");
                    if (!text.isEmpty()) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(text);
                    }
                }
                return builder.toString();
            }
        }

        return firstChoice.optString("text", "");
    }

    private String formatHttpError(int statusCode, String body) {
        String message = extractErrorMessage(body);
        if (message.isEmpty()) {
            return "Cloud AI request failed with status " + statusCode + ".";
        }
        return "Cloud AI request failed with status " + statusCode + ": " + message;
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONObject errorObject = root.optJSONObject("error");
            if (errorObject != null) {
                String message = errorObject.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
                return errorObject.toString();
            }
            String message = root.optString("message", "");
            if (!message.isEmpty()) {
                return message;
            }
            return body.length() > 300 ? body.substring(0, 300) + "..." : body;
        } catch (JSONException ignored) {
            return body.length() > 300 ? body.substring(0, 300) + "..." : body;
        }
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }
}
