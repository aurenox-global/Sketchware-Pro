cmake_minimum_required(VERSION 3.22.1)

project(sketchware_local_ai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED LLAMA_CPP_DIR OR NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Set LLAMA_CPP_DIR to a llama.cpp checkout")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# llamafile kernels: CPU-accelerated matmul (large speedup on phones).
# Only for arm64-v8a: the fp16 intrinsics used by sgemm are AArch64-only and
# do not compile for 32-bit ARM or x86 with this NDK.
if(ANDROID_ABI MATCHES "arm64-v8a")
    set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
else()
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${LLAMA_CPP_DIR}" llama.cpp EXCLUDE_FROM_ALL)

add_library(sketchware_llama SHARED sketchware_llama_jni.cpp)

target_include_directories(sketchware_llama PRIVATE
        "${LLAMA_CPP_DIR}/include"
        "${LLAMA_CPP_DIR}/ggml/include")

target_link_libraries(sketchware_llama PRIVATE llama android log)