package pro.sketchware.ai;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LocalAiPromptFormatter {

    private static final ConcurrentMap<String, String> ARCHITECTURE_CACHE = new ConcurrentHashMap<>();

    private LocalAiPromptFormatter() {
    }

    public static String format(String prompt, LocalAiConfig config, boolean thinking) {
        String modelPath = config.getModelPath();
        if (modelPath == null || modelPath.isEmpty()) {
            return prompt;
        }
        String architecture = getArchitecture(modelPath);
        return formatForArchitecture(prompt, architecture, thinking);
    }

    public static String format(String prompt, String architecture, boolean thinking) {
        return formatForArchitecture(prompt, architecture, thinking);
    }

    private static String formatForArchitecture(String prompt, String architecture, boolean thinking) {
        String arch = architecture == null ? "" : architecture.toLowerCase(Locale.US);
        if (arch.contains("liquid")) {
            // LFM2.5 (Liquid AI): ChatML-like template whose assistant turn always
            // opens with "<think>" (reasoning model). The BOS token is added
            // automatically by the tokenizer. In JSON/action mode we start a plain
            // assistant turn so the grammar forces the JSON directly.
            String assistantPrefix = thinking
                    ? "<|im_start|>assistant\n<think>"
                    : "<|im_start|>assistant\n";
            return "<|im_start|>user\n" + prompt + "<|im_end|>\n" + assistantPrefix;
        }
        if (arch.contains("qwen") || arch.contains("moonshot")) {
            // Qwen3.5/Ornith official template:
            //   enable_thinking=true  -> assistant turn opens with "<think>\n"
            //   enable_thinking=false -> assistant turn opens with an empty "<think>\n\n</think>"
            String assistantPrefix = thinking
                    ? "<|im_start|>assistant\n<think>\n"
                    : "<|im_start|>assistant\n<think>\n\n</think>\n\n";
            return "<|im_start|>user\n" + prompt + "<|im_end|>\n" + assistantPrefix;
        }
        if (arch.contains("gemma4")) {
            // Gemma-4: thinking is enabled by placing the <|think|> token at the top of the
            // first system turn. Without it the model answers directly.
            String systemTurn = thinking
                    ? "<|turn>system\n<|think|>\n<turn|>\n"
                    : "";
            return systemTurn + "<|turn>user\n" + prompt + "<turn|>\n<|turn>model\n";
        }
        if (arch.contains("gemma")) {
            return "<start_of_turn>user\n" + prompt + "<end_of_turn>\n<start_of_turn>model\n";
        }
        if (arch.contains("llama") || arch.contains("mistral")) {
            return "[INST] " + prompt + " [/INST]";
        }
        return prompt;
    }

    private static String getArchitecture(String modelPath) {
        String cached = ARCHITECTURE_CACHE.get(modelPath);
        if (cached != null) {
            return cached;
        }
        String architecture = "";
        try {
            architecture = LocalAiModelInfo.fromPath(modelPath).getArchitecture();
        } catch (LocalAiException ignored) {
            android.util.Log.d("SketchwarePro", "LocalAiPromptFormatter: LocalAiException ignored", ignored);
        }
        ARCHITECTURE_CACHE.put(modelPath, architecture);
        return architecture;
    }

    public static void clearCache() {
        ARCHITECTURE_CACHE.clear();
    }
}
