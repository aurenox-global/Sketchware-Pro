#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

LLAMA_CPP_URL="${LLAMA_CPP_URL:-https://github.com/ggml-org/llama.cpp.git}"
# Pinned to a llama.cpp revision that includes libmtmd (multimodal/vision support).
# The JNI bridge (app/src/main/cpp) targets this revision. Override with:
# LLAMA_CPP_REF=<ref> scripts/build_local_ai_engine.command
LLAMA_CPP_REF="${LLAMA_CPP_REF:-b21e4de74567f5eef213765c9476a843c2e43f0d}"
ABIS="${ABIS:-arm64-v8a}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-26}"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" || ! -d "$ANDROID_SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must point to your Android SDK." >&2
  exit 1
fi

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$ANDROID_NDK_HOME" ]]; then
  latest_ndk=""
  for candidate in "$ANDROID_SDK_ROOT"/ndk/*; do
    if [[ -d "$candidate" ]]; then
      latest_ndk="$candidate"
    fi
  done
  ANDROID_NDK_HOME="$latest_ndk"
fi

if [[ -z "$ANDROID_NDK_HOME" || ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
  echo "Android NDK not found. Install it with sdkmanager 'ndk;27.2.12479018' or set ANDROID_NDK_HOME." >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "git is required." >&2
  exit 1
fi

if ! command -v cmake >/dev/null 2>&1; then
  echo "cmake is required." >&2
  exit 1
fi

LLAMA_DIR="$REPO_ROOT/build/local-ai/llama.cpp"
if [[ ! -d "$LLAMA_DIR/.git" ]]; then
  mkdir -p "$(dirname "$LLAMA_DIR")"
  git clone --depth 1 "$LLAMA_CPP_URL" "$LLAMA_DIR"
fi

cd "$LLAMA_DIR"
git fetch --depth 1 origin "$LLAMA_CPP_REF"
git checkout --detach FETCH_HEAD

generator_args=()
if command -v ninja >/dev/null 2>&1; then
  generator_args=(-G Ninja)
fi

strip_tool=""
for candidate in "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin/llvm-strip; do
  if [[ -x "$candidate" ]]; then
    strip_tool="$candidate"
    break
  fi
done

for abi in $ABIS; do
  build_dir="$REPO_ROOT/build/local-ai/android-$abi"
  output_dir="$REPO_ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$output_dir"

  cmake -S "$REPO_ROOT/app/src/main/cpp" \
    -B "$build_dir" \
    "${generator_args[@]}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
    -DLLAMA_CPP_DIR="$LLAMA_DIR"

  cmake --build "$build_dir" --config Release --target sketchware_llama
  cp "$build_dir/libsketchware_llama.so" "$output_dir/libsketchware_llama.so"
  if [[ -n "$strip_tool" ]]; then
    "$strip_tool" --strip-unneeded "$output_dir/libsketchware_llama.so" || true
  fi
  echo "Installed $output_dir/libsketchware_llama.so"
done

echo "Local AI native engine build complete. Rebuild Sketchware Pro to package the new .so files."