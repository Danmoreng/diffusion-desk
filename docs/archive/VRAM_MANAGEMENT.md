# VRAM Management & Resource Arbitration

## Goal
To maintain low latency for both LLM and Stable Diffusion models while preventing Out-of-Memory (OOM) errors, especially during the VRAM-intensive VAE decoding phase. We aim to avoid aggressive "brute-force" unloading in favor of a surgical, intelligent resource management approach.

## Current Issue: VAE Decode OOM
When generating large images (e.g., 1024x1024 or larger), the VAE decoding step requires a significant amount of VRAM for its compute buffer. This is in addition to the VRAM already occupied by the Diffusion model and Text Encoder weights.

### Observations
- For a **512x512** image, the VAE decode compute buffer requires approximately **1.6 GB** of VRAM.
- For larger images, this scales linearly with pixel count. Very large images can request over **11 GB** just for the decode pass.
- If the LLM is also loaded, the available space is often insufficient, leading to `cudaMalloc failed: out of memory`.
- `stable-diffusion.cpp` may fail silently, producing blank images when allocation fails.

## Architecture Strategy

### 1. Centralized Resource Manager (Orchestrator)
The Orchestrator acts as a **Resource Arbitrator** rather than just a proxy.
- **VRAM Registry**: Maintains a map of active processes and their estimated/reported VRAM footprints.
- **Arbitration Logic**: Before forwarding a "heavy" request (like image generation), checks projected VRAM usage.
- **Soft vs. Hard Unloading**:
    - *Soft*: Instructs a worker to purge non-essential buffers (e.g., KV cache, temporary compute buffers).
    - *Hard*: Requests a full model unload only if necessary.

### 2. Dynamic Component Offloading (SD Worker)
Instead of unloading the entire LLM to make room for VAE decoding, we perform surgical offloading within the SD worker.
- **VAE-on-CPU Strategy**:
    - Calculate required VAE memory based on `width * height`.
    - If `Available_VRAM < (Diffusion_Model + VAE_Buffer)`, automatically set `vae_on_cpu = true` for that generation.
    - This keeps main Diffusion weights in VRAM, allowing subsequent generations to start instantly while incurring only a 2-3 second penalty during the final decode step.
- **Memory Reporting**: Workers report current allocated VRAM to the Orchestrator via `/internal/health`.

### 3. Latency-Optimized State Transitions
- **LRU Policy**: Least Recently Used policy for models. If the LLM is idle for 10 minutes, it is the first candidate for unloading.
- **Predictive Pre-warming**: Keep both models loaded if VRAM permits.
- **Parallel Loading**: Optimize worker initialization to keep weights in system RAM or use `mmap` for faster reloading.

## Implementation Status

### Implemented
- **Automatic LLM Offloading**: The Orchestrator automatically sends an unload request to the LLM worker before starting a "heavy" Stable Diffusion task. This frees ~4GB of VRAM.

### Planned / In Progress
1.  **Intelligent VAE Device Selection**:
    - Estimate VRAM for VAE decoding.
    - Check available VRAM before generation.
    - Auto-configure `vae_on_cpu: true` if the safety threshold is exceeded.
2.  **Enhance Health Checks**: Update `/internal/health` to return detailed memory stats.
3.  **Dynamic Context Re-initialization**: Allow workers to re-initialize with conservative settings (CPU offload) upon failure and retry.
4.  **UI Feedback**: Notify the user when models are offloaded or VAE is moved to CPU.