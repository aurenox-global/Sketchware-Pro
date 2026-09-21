package com.besome.sketch.design;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import pro.sketchware.R;
import pro.sketchware.activities.ai.LocalAiManagerActivity;
import pro.sketchware.ai.CloudAiService;
import pro.sketchware.ai.LocalAiConfig;
import pro.sketchware.ai.LocalAiPromptFactory;
import pro.sketchware.ai.LocalAiService;
import pro.sketchware.ai.ThinkModeParser;
import pro.sketchware.utility.SketchwareUtil;

public class AiLocalEditorFragment extends Fragment {
    private static final String PREF_CHAT_HISTORY = "ai_local_editor_chat_history";

    private final ArrayList<ChatMessage> chatMessages = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private RecyclerView chatMessagesRecycler;
    private TextInputEditText chatPromptInput;
    private TextView chatHeaderText;
    private MaterialButton chatSendButton;
    private com.google.android.material.textfield.TextInputLayout chatInputLayout;
    private MaterialButton chatNewChatButton;
    private MaterialButton agentBadge;

    private ChatMessageAdapter chatMessageAdapter;
    private SharedPreferences chatPrefs;

    private boolean running;
    private boolean pendingFinishAfterStreaming;

    private Runnable streamRunnable;
    private int streamingMessageIndex = -1;
    private int streamingCursor = 0;
    private String streamingText = "";

    private Markwon markwon;
    private MaterialButton chatReasoningButton;
    private boolean reasoningEnabled = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fr_ai_local_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        chatPrefs = requireContext().getSharedPreferences(PREF_CHAT_HISTORY, android.content.Context.MODE_PRIVATE);

        chatHeaderText = view.findViewById(R.id.chat_header_text);
        chatMessagesRecycler = view.findViewById(R.id.chat_messages_recycler);
        chatPromptInput = view.findViewById(R.id.chat_prompt_input);
        chatSendButton = view.findViewById(R.id.chat_send_button);
        chatNewChatButton = view.findViewById(R.id.chat_new_chat_button);
        chatInputLayout = view.findViewById(R.id.chat_input_layout);
        agentBadge = view.findViewById(R.id.agent_badge_button);
        chatReasoningButton = view.findViewById(R.id.chat_reasoning_button);

        markwon = Markwon.create(requireContext());
        loadChatSettings();

        chatMessageAdapter = new ChatMessageAdapter(chatMessages);
        chatMessagesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatMessagesRecycler.setAdapter(chatMessageAdapter);

        setupInsets(view);
        setupUiActions(view);
        loadChatHistory();
        updateChatHeader(LocalAiConfig.load(requireContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            );
        }
        updateChatHeader(LocalAiConfig.load(requireContext()));
    }

    @Override
    public void onDestroyView() {
        LocalAiService.getInstance().cancel();
        CloudAiService.getInstance().cancel();
        cancelStreamingAnimation();
        persistChatHistory();
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        super.onDestroyView();
    }

    private void setupInsets(@NonNull View rootView) {
        int initialLeft = rootView.getPaddingLeft();
        int initialTop = rootView.getPaddingTop();
        int initialRight = rootView.getPaddingRight();
        int initialBottom = rootView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            v.setPadding(
                    initialLeft + bars.left,
                    initialTop,
                    initialRight + bars.right,
                    initialBottom + bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    private void setupUiActions(@NonNull View view) {
        view.findViewById(R.id.chat_open_manager_button).setOnClickListener(v -> {
            if (getContext() != null) {
                startActivity(new Intent(getContext(), LocalAiManagerActivity.class));
            }
        });

        chatSendButton.setOnClickListener(v -> {
            if (running) {
                cancelCurrentRequest();
            } else {
                sendChatPrompt();
            }
        });
        chatNewChatButton.setOnClickListener(v -> startNewChat());

        chatReasoningButton.setOnClickListener(v -> {
            reasoningEnabled = !reasoningEnabled;
            updateReasoningButtonState();
            LocalAiConfig config = LocalAiConfig.load(requireContext());
            if (reasoningEnabled) {
                config.setTemperature(1.0f);
                config.setTopP(0.95f);
            } else {
                config.setTemperature(0.7f);
                config.setTopP(0.8f);
            }
            config.save(requireContext());
            LocalAiConfig.saveChatReasoning(requireContext(), reasoningEnabled);
        });

        view.findViewById(R.id.chat_export_button).setOnClickListener(v -> exportChatHistory());

        chatPromptInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendChatPrompt();
                return true;
            }
            return false;
        });

        chatPromptInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                chatMessagesRecycler.postDelayed(() -> {
                    if (!chatMessages.isEmpty()) {
                        chatMessagesRecycler.scrollToPosition(chatMessages.size() - 1);
                    }
                }, 120L);
            }
        });

        updatePromptHint();
    }

    private void updatePromptHint() {
        chatInputLayout.setHint("Describe qué quieres: crear pantalla, botón, lógica…");
    }

    private void sendChatPrompt() {
        if (running) {
            return;
        }

        String prompt = chatPromptInput.getText() == null ? "" : chatPromptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            SketchwareUtil.toastError("Escribe un mensaje primero.");
            return;
        }

        LocalAiConfig config = LocalAiConfig.load(requireContext());
        if (!validateAiSourceReady(config)) {
            return;
        }

        final boolean cloudSelected = config.isCloudProvider();

        addChatMessage(true, prompt, true);
        chatPromptInput.setText("");
        int assistantMessageIndex = addChatMessage(false, "Generando acciones…", false);
        String effectivePrompt = buildPromptForSelectedRole(prompt);

        Runnable runGeneration = () -> {
            LocalAiService.Callback callback = new LocalAiService.Callback() {
                @Override
                public void onStarted() {
                    running = true;
                    pendingFinishAfterStreaming = false;
                    setBusy(true);
                }

                @Override
                public void onStatus(String status) {
                }

                @Override
                public void onSuccess(String response) {
                    String safeResponse = response == null ? "" : response.trim();
                    if (safeResponse.isEmpty()) {
                        updateChatMessage(assistantMessageIndex, "El agente no devolvió respuesta.", true);
                        finishAgentRequest();
                        return;
                    }
                    processAgentResponse(assistantMessageIndex, safeResponse);
                }

                @Override
                public void onError(Throwable throwable) {
                    cancelStreamingAnimation();
                    String message = throwable == null ? "Unknown error" : throwable.getMessage();
                    updateChatMessage(assistantMessageIndex, "Error: " + (message == null ? "Unknown error" : message), true);
                    finishAgentRequest();
                }

                @Override
                public void onFinished() {
                    if (isStreamingActive()) {
                        pendingFinishAfterStreaming = true;
                    } else {
                        running = false;
                        setBusy(false);
                    }
                }
            };

            if (cloudSelected) {
                if (reasoningEnabled) {
                    CloudAiService.getInstance().generate(requireContext(), effectivePrompt, callback);
                } else {
                    CloudAiService.getInstance().generateJson(requireContext(), effectivePrompt, callback);
                }
            } else {
                // Local agent: always grammar-constrained JSON (no thinking), with
                // deterministic sampling so small models follow instructions reliably.
                LocalAiConfig agentConfig = LocalAiConfig.load(requireContext());
                agentConfig.setTemperature(0.5f);
                agentConfig.setTopP(0.85f);
                LocalAiService.getInstance().generateJsonWithConfig(requireContext(), effectivePrompt, agentConfig, callback);
            }
        };

        runGeneration.run();
    }

    private void loadChatSettings() {
        reasoningEnabled = LocalAiConfig.loadChatReasoning(requireContext());
        updateReasoningButtonState();
    }

    private void updateReasoningButtonState() {
        chatReasoningButton.setChecked(reasoningEnabled);
        chatReasoningButton.setText(reasoningEnabled ? "Razonar: ON" : "Razonar: OFF");
    }

    private String buildPromptForSelectedRole(String userPrompt) {
        String projectId = DesignActivity.sc_id == null || DesignActivity.sc_id.trim().isEmpty()
                ? "unknown"
                : DesignActivity.sc_id.trim();

        String projectContext = pro.sketchware.ai.AgentProjectContext.build(projectId);
        String filesContext = pro.sketchware.ai.ProjectCodeInjector.buildProjectContext(projectId);
        if (!filesContext.isEmpty()) {
            projectContext = (projectContext == null ? "" : projectContext) + filesContext;
        }
        return LocalAiPromptFactory.buildAgentPrompt(
                projectId,
                userPrompt,
                projectContext);
    }

    private boolean validateAiSourceReady(@NonNull LocalAiConfig config) {
        if (config.isCloudProvider()) {
            if (config.getCloudApiKey().isEmpty() && !LocalAiConfig.isCustomProvider(config.getProviderId())) {
                String issue = "Configure the cloud API key in AI Manager first.";
                SketchwareUtil.toastError(issue);
                addChatMessage(false, issue, true);
                return false;
            }
            if (config.resolveCloudModel().isEmpty()) {
                String issue = "Configure the cloud model in AI Manager first.";
                SketchwareUtil.toastError(issue);
                addChatMessage(false, issue, true);
                return false;
            }
            if (config.resolveCloudEndpoint().isEmpty()) {
                String issue = "Configure the cloud endpoint in AI Manager first.";
                SketchwareUtil.toastError(issue);
                addChatMessage(false, issue, true);
                return false;
            }
            return true;
        }

        if (!config.hasModel()) {
            String issue = "No local model selected. Open AI Manager first.";
            SketchwareUtil.toastError(issue);
            addChatMessage(false, issue, true);
            return false;
        }
        return true;
    }

    private void updateChatHeader(@NonNull LocalAiConfig config) {
        chatHeaderText.setText(config.isCloudProvider()
                ? "Model: " + config.resolveCloudModel()
                : (config.hasModel() ? "Model: " + config.getModelName() : "Model: Not configured"));
    }

    private void startNewChat() {
        if (running) {
            return;
        }
        cancelStreamingAnimation();
        chatMessages.clear();
        chatMessageAdapter.notifyDataSetChanged();
        addChatMessage(false, "Nueva conversacion iniciada. Estoy listo para ayudarte.", true);
    }

    private void setBusy(boolean busy) {
        chatPromptInput.setEnabled(!busy);
        chatSendButton.setEnabled(true);
        chatNewChatButton.setEnabled(!busy);
        chatSendButton.setIconResource(busy ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_arrow_up);
        chatSendButton.setContentDescription(busy ? "Cancel" : "Send");
    }

    private void cancelCurrentRequest() {
        LocalAiService.getInstance().cancel();
        CloudAiService.getInstance().cancel();
        cancelStreamingAnimation();
        running = false;
        setBusy(false);
        addChatMessage(false, "Request cancelled.", true);
    }

    private int addChatMessage(boolean user, String text, boolean persist) {
        if (text == null || text.trim().isEmpty()) {
            return -1;
        }

        chatMessages.add(new ChatMessage(user, text.trim()));
        int index = chatMessages.size() - 1;
        chatMessageAdapter.notifyItemInserted(index);
        chatMessagesRecycler.scrollToPosition(index);
        if (persist) {
            persistChatHistory();
        }
        return index;
    }

    private void updateChatMessage(int index, String text, boolean persist) {
        if (index < 0 || index >= chatMessages.size()) {
            return;
        }
        chatMessages.get(index).text = text == null ? "" : text;
        chatMessageAdapter.notifyItemChanged(index);
        chatMessagesRecycler.scrollToPosition(index);
        if (persist) {
            persistChatHistory();
        }
    }

    private void processAgentResponse(int messageIndex, String response) {
        updateChatMessage(messageIndex, "Procesando acciones del agente…", false);
        Thread worker = new Thread(() -> {
            String raw = response == null ? "" : response.trim();

            String jsonText = raw;
            LocalAiConfig config = LocalAiConfig.load(requireContext());
            String archName = config.isCloudProvider() ? config.resolveCloudModel() : config.getModelName();
            ThinkModeParser.ThinkResult thinkResult = ThinkModeParser.extractThinking(raw, ThinkModeParser.detectArchitecture(archName));
            if (thinkResult.hasThinking) {
                jsonText = thinkResult.cleanResponse == null ? raw : thinkResult.cleanResponse.trim();
            }

            JSONObject agentJson = parseAgentJson(jsonText);
            if (agentJson == null) {
                final String fallbackText = jsonText;
                uiHandler.post(() -> {
                    if (thinkResult.hasThinking && messageIndex >= 0 && messageIndex < chatMessages.size()) {
                        chatMessages.get(messageIndex).thinkingText = thinkResult.thinkingText;
                    }
                    updateChatMessage(messageIndex, fallbackText.isEmpty() ? "El agente no devolvió respuesta." : fallbackText, true);
                    finishAgentRequest();
                });
                return;
            }

            String projectId = DesignActivity.sc_id == null ? "" : DesignActivity.sc_id.trim();
            if (projectId.isEmpty()) {
                uiHandler.post(() -> {
                    if (thinkResult.hasThinking && messageIndex >= 0 && messageIndex < chatMessages.size()) {
                        chatMessages.get(messageIndex).thinkingText = thinkResult.thinkingText;
                    }
                    updateChatMessage(messageIndex, "No hay proyecto abierto para aplicar acciones.", true);
                    finishAgentRequest();
                });
                return;
            }

            StringBuilder resultText = new StringBuilder();
            final boolean[] changesApplied = {false};
            try {
                pro.sketchware.ai.AgentActionExecutor executor = new pro.sketchware.ai.AgentActionExecutor(projectId);
                pro.sketchware.ai.AgentActionExecutor.Result result = executor.execute(agentJson);
                if (!result.getReply().trim().isEmpty()) {
                    resultText.append(result.getReply().trim()).append("\n\n");
                }
                java.util.List<String> summary = result.getSummaryLines();
                if (summary.isEmpty()) {
                    if (resultText.length() == 0) {
                        resultText.append("No se detectaron acciones para aplicar.");
                    }
                } else {
                    resultText.append("**Cambios aplicados:**\n");
                    for (String line : summary) {
                        resultText.append("- ").append(line).append('\n');
                    }
                    changesApplied[0] = true;
                    SketchwareUtil.toast("Agente: cambios aplicados al proyecto");
                }
            } catch (Throwable throwable) {
                resultText.setLength(0);
                resultText.append("Error al aplicar acciones: ").append(throwable.getMessage());
            }

            final String finalText = resultText.toString();
            uiHandler.post(() -> {
                if (thinkResult.hasThinking && messageIndex >= 0 && messageIndex < chatMessages.size()) {
                    chatMessages.get(messageIndex).thinkingText = thinkResult.thinkingText;
                }
                updateChatMessage(messageIndex, finalText, true);
                if (changesApplied[0] && getActivity() instanceof DesignActivity designActivity) {
                    // Reload the visual editor, events and components so the new
                    // views/code appear immediately in their tabs.
                    designActivity.refreshAfterAiActions();
                }
                finishAgentRequest();
            });
        }, "ai-agent-executor");
        worker.start();
    }

    private JSONObject parseAgentJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[\\w+\\-]*\\s*\\n?", "");
            int fence = json.lastIndexOf("```");
            if (fence >= 0) {
                json = json.substring(0, fence);
            }
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return new JSONObject(json.substring(start, end + 1));
        } catch (JSONException ignored) {
            return null;
        }
    }

    private void finishAgentRequest() {
        if (pendingFinishAfterStreaming) {
            pendingFinishAfterStreaming = false;
        }
        running = false;
        setBusy(false);
    }

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:[\\w+\\-]+)?\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);

    private void copyMessageToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("AI response", text == null ? "" : text));
        Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void applyMessageCode(String text) {
        final String code = extractCodeBlock(text);
        final String projectId = DesignActivity.sc_id == null ? "" : DesignActivity.sc_id.trim();
        boolean hadCodeBlock = text != null && CODE_BLOCK_PATTERN.matcher(text).find();

        if (hadCodeBlock && !projectId.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Apply code")
                    .setMessage("¿Aplicar el código al proyecto (initializeLogic de MainActivity) o solo copiarlo?")
                    .setPositiveButton("Aplicar al proyecto", (dialog, which) -> {
                        boolean applied = pro.sketchware.ai.ProjectCodeInjector.injectIntoInitializeLogic(
                                projectId, pro.sketchware.ai.ProjectCodeInjector.getMainJavaName(), code);
                        Toast.makeText(getContext(),
                                applied ? "Código aplicado a MainActivity (initializeLogic)." : "No se pudo aplicar el código.",
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Solo copiar", (dialog, which) -> {
                        copyMessageToClipboard(code);
                        Toast.makeText(getContext(), "Código copiado.", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return;
        }

        copyMessageToClipboard(code);
        Toast.makeText(getContext(),
                hadCodeBlock ? "Code block copied. Paste it in your editor." : "Copied to clipboard.",
                Toast.LENGTH_SHORT).show();
    }

    private String extractCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    private String formatMessageTime(long timestamp) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private void finishStreamingAnimation() {
        streamRunnable = null;
        streamingMessageIndex = -1;
        streamingCursor = 0;
        streamingText = "";
    }

    private void cancelStreamingAnimation() {
        if (streamRunnable != null) {
            uiHandler.removeCallbacks(streamRunnable);
        }
        finishStreamingAnimation();
    }

    private boolean isStreamingActive() {
        return streamRunnable != null;
    }

    private String resolveChatScopeKey() {
        String projectId = DesignActivity.sc_id == null ? "" : DesignActivity.sc_id.trim();
        return projectId.isEmpty() ? "project:unknown" : "project:" + projectId;
    }

    private void exportChatHistory() {
        if (chatMessages.size() <= 1) {
            SketchwareUtil.toastError("No messages to export.");
            return;
        }

        StringBuilder md = new StringBuilder();
        md.append("# AI Chat Export\n");
        md.append("**Date:** ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date())).append("\n");
        md.append("**Project:** ").append(resolveChatScopeKey()).append("\n\n");
        md.append("---\n\n");

        for (ChatMessage msg : chatMessages) {
            String role = msg.user ? "**You**" : "**AI**";
            md.append("### ").append(role).append("\n\n");
            md.append(msg.text).append("\n\n");
            if (msg.hasThinking()) {
                md.append("<details>\n<summary>Thinking</summary>\n\n").append(msg.thinkingText).append("\n\n</details>\n\n");
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Export conversation")
                .setItems(new CharSequence[]{"Copy to clipboard", "Save to file"}, (dialog, which) -> {
                    if (which == 0) {
                        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("AI Chat Export", md.toString()));
                        Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        try {
                            File dir = new File(Environment.getExternalStorageDirectory(), ".sketchware/ai/exports");
                            dir.mkdirs();
                            File file = new File(dir, "chat_" + System.currentTimeMillis() + ".md");
                            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                                writer.write(md.toString());
                            }
                            SketchwareUtil.toast("Saved: " + file.getName());
                        } catch (Exception e) {
                            SketchwareUtil.toastError("Failed to save: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadChatHistory() {
        cancelStreamingAnimation();
        chatMessages.clear();

        String payload = chatPrefs.getString(resolveChatScopeKey(), "");
        if (payload != null && !payload.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(payload);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    boolean user = object.optBoolean("user", false);
                    String text = object.optString("text", "").trim();
                    long timestamp = object.optLong("time", System.currentTimeMillis());
                    if (!text.isEmpty()) {
                        ChatMessage msg = new ChatMessage(user, text, timestamp);
                        msg.thinkingText = object.optString("thinking", null);
                        if ("".equals(msg.thinkingText)) msg.thinkingText = null;
                        chatMessages.add(msg);
                    }
                }
            } catch (JSONException ignored) {
                android.util.Log.d("SketchwarePro", "AiLocalEditorFragment: failed to decode chat history", ignored);
            }
        }

        if (chatMessages.isEmpty()) {
            chatMessages.add(new ChatMessage(false, "Hola. Esta es la vista AI Local dentro del proyecto. Escribe tu mensaje y te respondo con la fuente AI configurada."));
            persistChatHistory();
        }

        chatMessageAdapter.notifyDataSetChanged();
        chatMessagesRecycler.scrollToPosition(chatMessages.size() - 1);
    }

    private void persistChatHistory() {
        JSONArray array = new JSONArray();
        for (ChatMessage message : chatMessages) {
            JSONObject object = new JSONObject();
            try {
                object.put("user", message.user);
                object.put("text", message.text == null ? "" : message.text);
                object.put("time", message.timestamp);
                if (message.hasThinking()) {
                    object.put("thinking", message.thinkingText);
                }
                array.put(object);
            } catch (JSONException ignored) {
                android.util.Log.d("SketchwarePro", "AiLocalEditorFragment: failed to encode chat message", ignored);
            }
        }
        chatPrefs.edit().putString(resolveChatScopeKey(), array.toString()).apply();
    }

    private static final class ChatMessage {
        private final boolean user;
        private String text;
        private String thinkingText;
        private final long timestamp;

        private ChatMessage(boolean user, String text) {
            this.user = user;
            this.text = text;
            this.thinkingText = null;
            this.timestamp = System.currentTimeMillis();
        }

        private ChatMessage(boolean user, String text, String thinkingText) {
            this.user = user;
            this.text = text;
            this.thinkingText = thinkingText;
            this.timestamp = System.currentTimeMillis();
        }

        private ChatMessage(boolean user, String text, long timestamp) {
            this.user = user;
            this.text = text;
            this.thinkingText = null;
            this.timestamp = timestamp;
        }

        private boolean hasThinking() {
            return thinkingText != null && !thinkingText.trim().isEmpty();
        }
    }

    private final class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {
        private final ArrayList<ChatMessage> data;

        private ChatMessageAdapter(ArrayList<ChatMessage> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage message = data.get(position);
            boolean isUser = message.user;

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.bubbleContainer.getLayoutParams();
            params.gravity = isUser ? Gravity.END : Gravity.START;
            holder.bubbleContainer.setLayoutParams(params);

            holder.messageRoleText.setText(isUser ? "Tú" : "IA");
            holder.messageTimeText.setText(formatMessageTime(message.timestamp));

            int backgroundColor;
            int textColor;
            if (isUser) {
                backgroundColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer);
                textColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);
            } else {
                backgroundColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceContainerHigh);
                textColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface);
            }

            holder.bubbleCard.setCardBackgroundColor(backgroundColor);
            holder.bubbleText.setTextColor(textColor);

            String text = message.text == null ? "" : message.text;
            if (isUser) {
                holder.bubbleText.setText(text);
            } else {
                markwon.setMarkdown(holder.bubbleText, text);
            }

            if (message.hasThinking()) {
                holder.thinkCard.setVisibility(View.VISIBLE);
                holder.thinkText.setText(message.thinkingText);
                holder.thinkText.setVisibility(View.VISIBLE);
                holder.thinkHeader.setOnClickListener(v -> {
                    if (holder.thinkText.getVisibility() == View.VISIBLE) {
                        holder.thinkText.setVisibility(View.GONE);
                        holder.thinkHeader.setText("Show reasoning");
                    } else {
                        holder.thinkText.setVisibility(View.VISIBLE);
                        holder.thinkHeader.setText("Thinking...");
                    }
                });
            } else {
                holder.thinkCard.setVisibility(View.GONE);
            }

            if (!isUser && !text.trim().isEmpty()) {
                holder.actionRow.setVisibility(View.VISIBLE);
                holder.actionCopyButton.setOnClickListener(v -> copyMessageToClipboard(text));
                holder.actionApplyButton.setOnClickListener(v -> applyMessageCode(text));
            } else {
                holder.actionRow.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {
            private final View bubbleContainer;
            private final MaterialCardView bubbleCard;
            private final TextView bubbleText;
            private final MaterialCardView thinkCard;
            private final TextView thinkHeader;
            private final TextView thinkText;
            private final TextView messageRoleText;
            private final TextView messageTimeText;
            private final View actionRow;
            private final MaterialButton actionCopyButton;
            private final MaterialButton actionApplyButton;

            private ViewHolder(@NonNull View itemView) {
                super(itemView);
                bubbleContainer = itemView.findViewById(R.id.bubble_container);
                bubbleCard = itemView.findViewById(R.id.bubble_card);
                bubbleText = itemView.findViewById(R.id.bubble_text);
                thinkCard = itemView.findViewById(R.id.think_card);
                thinkHeader = itemView.findViewById(R.id.think_header);
                thinkText = itemView.findViewById(R.id.think_text);
                messageRoleText = itemView.findViewById(R.id.message_role_text);
                messageTimeText = itemView.findViewById(R.id.message_time_text);
                actionRow = itemView.findViewById(R.id.action_row);
                actionCopyButton = itemView.findViewById(R.id.action_copy_button);
                actionApplyButton = itemView.findViewById(R.id.action_apply_button);
            }
        }
    }
}
