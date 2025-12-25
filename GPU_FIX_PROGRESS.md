# Session Progress: Enabling GPU Support on Linux

## Status Summary
- **Goal:** Enable GPU (CUDA) acceleration for SD and LLM workers.
- **Completed:** Build system updated, CUDA support compiled, generation is significantly faster.
- **Current Issue:** Generated images are plain gray when using GPU.
- **Next Step:** Compare build configuration with a known working standalone benchmark in `/home/sebastian/Development/benchmark_sd_backends/`.

## Changes Made

### 1. Build Configuration (`CMakeLists.txt`)
Enabled CUDA support by default for both Llama.cpp and Stable Diffusion.cpp.
```cmake
set(GGML_CUDA ON CACHE BOOL "" FORCE)
set(SD_CUDA ON CACHE BOOL "" FORCE)
```

### 2. Linux Build Script (`scripts/build.sh`)
Created a comprehensive build script for Linux that handles:
- WebUI dependency installation and production build.
- CMake configuration with `Release` type and CUDA enabled.
- Parallel C++ compilation.

### 3. Verification
- Verified `nvcc` is available (`V13.1.80`).
- Build completed successfully.
- Confirmed existence of `libggml-cuda.so` in `build/bin/`.
- User confirmed generation is much faster, indicating GPU utilization.

## Observations on Gray Image Issue
- The user noted that a similar "gray image" issue occurred in a standalone benchmark and was fixed there.
- This often relates to specific compiler flags (like `-march=native`), precision settings (FP16 vs FP32), or specific CUDA/Flash Attention configurations that might differ between the integrated project and the standalone version.

## Files for Reference in New Session
- `CMakeLists.txt`: Current build config.
- `scripts/build.sh`: Current build process.
- `server_run.txt`: Log showing the initial CPU-only run.
