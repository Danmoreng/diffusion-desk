# VRAM Management in MystiCanvas

## Current Issue: VAE Decode OOM
When generating large images (e.g., 1024x1024 or larger), the VAE decoding step requires a significant amount of VRAM for its compute buffer. This is in addition to the VRAM already occupied by the Diffusion model and Text Encoder weights.

### Observations
- For a **512x512** image, the VAE decode compute buffer requires approximately **1.6 GB** of VRAM.
- For larger images, this requirement scales with the number of pixels. A very large image was observed to request over **11 GB** for the VAE decode pass.
- If the LLM is also loaded in VRAM, the available space for this temporary buffer is further reduced, leading to `cudaMalloc failed: out of memory`.
- When `stable-diffusion.cpp` fails to allocate the VAE compute buffer, it currently proceeds without error but produces a blank (gray/black) image.

## Planned Improvements

### 1. Automatic LLM Offloading (Implemented)
The Orchestrator now automatically sends an unload request to the LLM worker before starting a "heavy" Stable Diffusion task (generations, edits, upscales). This ensures that the ~4GB of VRAM used by the LLM is available for the SD worker.

### 2. Intelligent VAE Device Selection (Proposed)
Since VAE decoding is a one-time pass at the end of generation, the performance penalty for running it on CPU is relatively small (a few seconds) compared to the risk of an OOM failure.

We aim to implement a logic that:
- Estimates the VRAM required for VAE decoding based on the requested image dimensions.
- Checks available VRAM before starting generation.
- Automatically configures the SD context to use `vae_on_cpu: true` if the estimated requirement exceeds a safety threshold of available VRAM.

### 3. Dynamic Context Re-initialization
If a generation fails due to VRAM issues, the worker should be able to re-initialize with more conservative settings (e.g., offloading more components to CPU) and retry, or at least report the specific cause to the user.
