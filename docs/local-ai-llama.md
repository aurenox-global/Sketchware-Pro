# Local AI with llama.cpp

Sketchware Pro now has a local AI layer for offline code assistance. The Java side and JNI bridge are integrated in the app, while GGUF model files stay external and are imported from the device.

## Main Sketchware Pro app

- Native library name: `libsketchware_llama.so`
- Java bridge: `pro.sketchware.ai.LocalAiBridge`
- Model manager: main drawer > Local AI Manager
- AI inside the project editor: `Design > AI` tab (agent that modifies the open project)
- Imported models directory: `/sdcard/.sketchware/ai/models`

Build the native engine with:

```sh
scripts/build_local_ai_engine.command
```

By default it builds `arm64-v8a`, which is the right ABI for most modern phones. To build more ABIs:

```sh
ABIS="arm64-v8a armeabi-v7a x86_64 x86" scripts/build_local_ai_engine.command
```

The script downloads `ggml-org/llama.cpp`, compiles `libsketchware_llama.so`, and places one native library per ABI under:

```text
app/src/main/jniLibs/arm64-v8a/libsketchware_llama.so
app/src/main/jniLibs/armeabi-v7a/libsketchware_llama.so
app/src/main/jniLibs/x86/libsketchware_llama.so
app/src/main/jniLibs/x86_64/libsketchware_llama.so
```

The native JNI bridge in `app/src/main/cpp/sketchware_llama_jni.cpp` exposes:

```text
nativeLoadModel(String modelPath, int contextSize, int threads) -> long
nativeGenerate(long handle, String prompt, int maxTokens, float temperature, float topP, float presencePenalty, float repeatPenalty, int topK, String grammar) -> String
nativeCancel(long handle) -> void
nativeRelease(long handle) -> void
```

The sampler is architecture-aware: Qwen3.5 uses `top_k=20`, `presence_penalty=1.5` in the JSON agent path (Unsloth non-thinking recommendation) and deterministic sampling for instruction following. `GGML_LLAMAFILE` (CPU-accelerated matmul) is enabled for `arm64-v8a`.

Until the `.so` is present and the app is rebuilt, Local AI Manager reports the missing engine and editor actions fail gracefully instead of crashing.

## AI agent inside projects (Design > AI tab)

The project editor's AI tab is an agent that can **modify the open project**: create views, events and code, and report changes back in the chat. It replies with a strict JSON object (`{"reply": "...", "actions": [...]}`) constrained by a llama.cpp JSON grammar for local models, which makes generation fast and deterministic:

- `add_view`: create a view (linear, button, text, input, card, image, scroll, ...) on a screen.
- `add_event`: attach an `onClick` event to an existing view.
- `inject_code`: inject Java directly into `initializeLogic` or a view's `onClick`.

The prompt includes the project context (screens, events, variables, file list) and a worked JSON example ("create a button with a toast onClick") so small local models can follow the format. The agent uses a single system prompt with a step-by-step method (decide → create views first → attach events → verify ids/code) and the executor is lenient (mis-typed view actions are recovered) and runs views before events. The local model stays loaded in RAM after the first request; subsequent requests skip the model load entirely.

## Model workflow

1. Open `Local AI Manager` from the main drawer.
2. Tap `Import GGUF` and select a model from any location exposed by the phone's system file picker, including Downloads, SD card, cloud/file-manager providers, or recent files. The import progress bar shows copy percentage while the file is moved into `/sdcard/.sketchware/ai/models`.
3. The `Model Catalog` tab offers one-tap downloads. The default catalog is tuned for **Qwen3.5-0.8B** (Unsloth, 256K context, reasoning off by default):
   - `Qwen3.5-0.8B-Q8_0.gguf` (812 MB) — recommended for maximum instruction fidelity.
   - `Qwen3.5-0.8B-Q4_K_M.gguf` and `UD-Q4_K_XL` as lighter alternatives.
4. The first AI request loads the model into RAM and keeps it there; subsequent requests reuse it. Tap `Use` to reload after changing context/threads/model settings.

## Generated projects and local libraries

Generated APKs and AABs include native libraries from both the project native library folder and enabled local libraries. For a reusable llama.cpp local library, place ABI folders under the local library `jni` directory, for example:

```text
/sdcard/.sketchware/libs/local_libs/llama-cpp/jni/arm64-v8a/libllama.so
/sdcard/.sketchware/libs/local_libs/llama-cpp/jni/arm64-v8a/libggml.so
```

When a generated project contains native libraries with names containing `llama`, `ggml`, or `local_ai`, its generated manifest gets `android:largeHeap="true"` automatically.

## Model guidance

Use quantized `.gguf` models sized for the target device. Q4 models are usually the best starting point on phones. Local AI Manager validates the GGUF header and shows an approximate RAM estimate before loading.
