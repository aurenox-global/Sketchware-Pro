#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define SKETCHWARE_LLAMA_LOG_TAG "SketchwareLocalAI"

namespace {

struct LlamaHandle {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::atomic_bool cancel_requested{false};
    std::mutex generation_mutex;
};

class JniString {
public:
    JniString(JNIEnv * env, jstring value) : env_(env), value_(value) {
        if (value_ != nullptr) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~JniString() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char * c_str() const {
        return chars_ == nullptr ? "" : chars_;
    }

private:
    JNIEnv * env_;
    jstring value_;
    const char * chars_ = nullptr;
};

std::once_flag backend_init_flag;

std::atomic_bool g_load_cancel_requested{false};

void local_ai_log_callback(enum ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, SKETCHWARE_LLAMA_LOG_TAG, text);
    }
}

void ensure_backend_ready() {
    std::call_once(backend_init_flag, [] {
        llama_log_set(local_ai_log_callback, nullptr);
        ggml_backend_load_all();
        llama_backend_init();
    });
}

bool abort_generation(void * user_data) {
    auto * handle = static_cast<LlamaHandle *>(user_data);
    return handle != nullptr && handle->cancel_requested.load();
}

void throw_local_ai_exception(JNIEnv * env, const std::string & message) {
    jclass exception_class = env->FindClass("pro/sketchware/ai/LocalAiException");
    if (exception_class == nullptr) {
        env->ExceptionClear();
        exception_class = env->FindClass("java/lang/RuntimeException");
    }
    env->ThrowNew(exception_class, message.c_str());
}

std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int piece_length = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (piece_length < 0) {
        buffer.resize(static_cast<size_t>(-piece_length));
        piece_length = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (piece_length <= 0) {
        return "";
    }
    return std::string(buffer.data(), static_cast<size_t>(piece_length));
}

std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & prompt) {
    int32_t token_count = -llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (token_count <= 0) {
        return {};
    }

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    int32_t actual_count = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), token_count, true, true);
    if (actual_count < 0) {
        return {};
    }
    tokens.resize(static_cast<size_t>(actual_count));
    return tokens;
}

// Detects when a top-level JSON object has closed (the grammar only allows
// valid JSON, so brace balance outside strings is enough). Used to stop
// generation as soon as the JSON is complete: if we kept sampling, the model
// could emit an EOG token that llama.cpp's grammar sampler would reject with
// ggml_abort() (SIGABRT, killing the app) instead of stopping cleanly.
bool json_object_complete(const std::string & text) {
    int depth = 0;
    bool in_string = false;
    bool escaped = false;
    for (char c : text) {
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                in_string = false;
            }
            continue;
        }
        if (c == '"') {
            in_string = true;
        } else if (c == '{') {
            depth++;
        } else if (c == '}') {
            depth--;
            if (depth == 0) {
                return true;
            }
        }
    }
    return false;
}

llama_sampler * create_sampler(float temperature, float top_p, const llama_vocab * vocab, const char * grammar, float presence_penalty, float repeat_penalty, int32_t top_k) {
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(std::max(1, top_k)));
    if (presence_penalty > 0.0f || repeat_penalty > 0.0f && repeat_penalty != 1.0f) {
        // Qwen3.5 thinking mode recommends presence_penalty = 1.5 to cut repetition;
        // LFM2.5 recommends repeat_penalty = 1.1.
        llama_sampler * penalties = llama_sampler_init_penalties(
                llama_vocab_n_tokens(vocab), 64,
                repeat_penalty > 0.0f ? repeat_penalty : 1.0f, 0.0f,
                presence_penalty > 0.0f ? presence_penalty : 0.0f);
        if (penalties != nullptr) {
            llama_sampler_chain_add(sampler, penalties);
        }
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(top_p, 0.01f, 1.0f), 1));
    if (grammar != nullptr && grammar[0] != '\0') {
        llama_sampler * grammar_sampler = llama_sampler_init_grammar(vocab, grammar, "root");
        if (grammar_sampler != nullptr) {
            llama_sampler_chain_add(sampler, grammar_sampler);
        }
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }
    return sampler;
}

void free_handle(LlamaHandle * handle) {
    if (handle == nullptr) {
        return;
    }
    if (handle->context != nullptr) {
        llama_free(handle->context);
        handle->context = nullptr;
    }
    if (handle->model != nullptr) {
        llama_model_free(handle->model);
        handle->model = nullptr;
    }
    delete handle;
}

} // namespace

static std::string generate_locked(JNIEnv * env,
                                   LlamaHandle * handle,
                                   jstring prompt_text,
                                   jint max_tokens,
                                   jfloat temperature,
                                   jfloat top_p,
                                   jfloat presence_penalty,
                                   jfloat repeat_penalty,
                                   jint top_k,
                                   const std::string & grammar);

extern "C" JNIEXPORT jlong JNICALL
Java_pro_sketchware_ai_LocalAiBridge_nativeLoadModel(JNIEnv * env, jclass, jstring model_path, jint context_size, jint threads) {
    ensure_backend_ready();
    g_load_cancel_requested.store(false);

    JniString path(env, model_path);
    if (std::string(path.c_str()).empty()) {
        throw_local_ai_exception(env, "Model path is empty.");
        return 0L;
    }
    if (g_load_cancel_requested.load()) {
        throw_local_ai_exception(env, "Local AI request cancelled.");
        return 0L;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        throw_local_ai_exception(env, "llama.cpp could not load the selected GGUF model.");
        return 0L;
    }
    if (g_load_cancel_requested.load()) {
        llama_model_free(model);
        throw_local_ai_exception(env, "Local AI request cancelled.");
        return 0L;
    }

    auto * handle = new LlamaHandle();
    handle->model = model;

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::max(128, static_cast<int>(context_size)));
    context_params.n_batch = std::min(context_params.n_ctx, static_cast<uint32_t>(2048));
    context_params.n_ubatch = std::min(context_params.n_ctx, static_cast<uint32_t>(512));
    context_params.n_threads = std::max(1, static_cast<int>(threads));
    context_params.n_threads_batch = std::max(1, static_cast<int>(threads));
    context_params.abort_callback = abort_generation;
    context_params.abort_callback_data = handle;

    handle->context = llama_init_from_model(model, context_params);
    if (handle->context == nullptr) {
        free_handle(handle);
        throw_local_ai_exception(env, "llama.cpp could not create a context for this model.");
        return 0L;
    }
    if (g_load_cancel_requested.load()) {
        free_handle(handle);
        throw_local_ai_exception(env, "Local AI request cancelled.");
        return 0L;
    }

    // Warmup: run a single token to prime CPU caches and memory
    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> warmup_tokens = tokenize_prompt(vocab, ".");
    if (!warmup_tokens.empty()) {
        llama_batch warmup_batch = llama_batch_get_one(warmup_tokens.data(), static_cast<int32_t>(warmup_tokens.size()));
        llama_decode(handle->context, warmup_batch);
    }
    llama_memory_clear(llama_get_memory(handle->context), true);

    if (g_load_cancel_requested.load()) {
        free_handle(handle);
        throw_local_ai_exception(env, "Local AI request cancelled.");
        return 0L;
    }

    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_pro_sketchware_ai_LocalAiBridge_nativeGenerate(JNIEnv * env, jclass, jlong native_handle, jstring prompt_text, jint max_tokens, jfloat temperature, jfloat top_p, jfloat presence_penalty, jfloat repeat_penalty, jint top_k, jstring grammar_text) {
    auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
    if (handle == nullptr || handle->model == nullptr || handle->context == nullptr) {
        throw_local_ai_exception(env, "Local AI model is not loaded.");
        return env->NewStringUTF("");
    }
    if (g_load_cancel_requested.load()) {
        throw_local_ai_exception(env, "Local AI request cancelled.");
        return env->NewStringUTF("");
    }

    JniString grammar_chars(env, grammar_text);
    std::string grammar(grammar_chars.c_str());

    try {
        std::string result = generate_locked(env, handle, prompt_text, max_tokens, temperature, top_p, presence_penalty, repeat_penalty, top_k, grammar);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & e) {
        __android_log_print(ANDROID_LOG_ERROR, SKETCHWARE_LLAMA_LOG_TAG,
                "Local AI generation failed: %s", e.what());
        throw_local_ai_exception(env, e.what());
        return env->NewStringUTF("");
    }
}

static std::string generate_locked(JNIEnv * env,
                                   LlamaHandle * handle,
                                   jstring prompt_text,
                                   jint max_tokens,
                                   jfloat temperature,
                                   jfloat top_p,
                                   jfloat presence_penalty,
                                   jfloat repeat_penalty,
                                   jint top_k,
                                   const std::string & grammar) {

    std::lock_guard<std::mutex> lock(handle->generation_mutex);
    handle->cancel_requested.store(false);

    JniString prompt_chars(env, prompt_text);
    std::string prompt(prompt_chars.c_str());
    if (prompt.empty()) {
        throw_local_ai_exception(env, "Prompt is empty.");
        return "";
    }

    const llama_vocab * vocab = llama_model_get_vocab(handle->model);
    llama_memory_clear(llama_get_memory(handle->context), true);

    int32_t context_limit = static_cast<int32_t>(llama_n_ctx(handle->context));
    int32_t requested_tokens = std::max(1, static_cast<int>(max_tokens));

    std::vector<llama_token> prompt_tokens = tokenize_prompt(vocab, prompt);
    if (prompt_tokens.empty()) {
        throw_local_ai_exception(env, "llama.cpp could not tokenize the prompt.");
        return "";
    }
    if (static_cast<int32_t>(prompt_tokens.size()) + 1 >= context_limit) {
        throw_local_ai_exception(env, "Prompt is larger than the configured context window.");
        return "";
    }
    requested_tokens = std::min(requested_tokens, context_limit - static_cast<int32_t>(prompt_tokens.size()) - 1);

    // Decode the prompt in n_batch-sized chunks so prompts larger than the
    // batch size (e.g. agent prompts with project context) never abort.
    const int32_t n_batch = static_cast<int32_t>(llama_n_batch(handle->context));
    for (size_t offset = 0; offset < prompt_tokens.size(); offset += n_batch) {
        const int32_t n_tokens = static_cast<int32_t>(
                std::min<size_t>(n_batch, prompt_tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + offset, n_tokens);
        if (llama_decode(handle->context, batch) != 0) {
            throw_local_ai_exception(env, handle->cancel_requested.load() ? "Local AI request cancelled." : "llama.cpp failed while evaluating the prompt.");
            return "";
        }
    }

    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(create_sampler(temperature, top_p, vocab, grammar.empty() ? nullptr : grammar.c_str(), presence_penalty, repeat_penalty, top_k), llama_sampler_free);

    const bool constrain_json = !grammar.empty();
    std::string response;
    response.reserve(static_cast<size_t>(requested_tokens) * 4);
    for (int32_t generated = 0; generated < requested_tokens; generated++) {
        if (handle->cancel_requested.load()) {
            throw_local_ai_exception(env, "Local AI request cancelled.");
            return "";
        }
        if (constrain_json && json_object_complete(response)) {
            // JSON is already complete; stop before sampling a token that the
            // grammar might reject with a fatal abort.
            break;
        }

        llama_token next_token;
        try {
            next_token = llama_sampler_sample(sampler.get(), handle->context, -1);
        } catch (const std::exception & e) {
            // llama.cpp's grammar throws when the JSON grammar is fully matched
            // and the model emits one extra trailing token. The accumulated
            // response is already a complete JSON document, so stop here.
            if (std::string(e.what()).find("empty grammar stack") != std::string::npos) {
                break;
            }
            throw;
        }
        if (llama_vocab_is_eog(vocab, next_token)) {
            break;
        }

        response += token_to_piece(vocab, next_token);

        llama_batch batch = llama_batch_get_one(&next_token, 1);
        int decode_result = llama_decode(handle->context, batch);
        if (decode_result != 0) {
            throw_local_ai_exception(env, handle->cancel_requested.load() ? "Local AI request cancelled." : "llama.cpp failed while generating a response.");
            return "";
        }
    }

    return response;
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_LocalAiBridge_nativeCancel(JNIEnv *, jclass, jlong native_handle) {
    auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
    if (handle != nullptr) {
        handle->cancel_requested.store(true);
    } else {
        g_load_cancel_requested.store(true);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_pro_sketchware_ai_LocalAiBridge_nativeRelease(JNIEnv *, jclass, jlong native_handle) {
    auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
    if (handle == nullptr) {
        return;
    }

    std::lock_guard<std::mutex> lock(handle->generation_mutex);
    free_handle(handle);
}
