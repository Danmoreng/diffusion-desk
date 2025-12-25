Please help me improve the plan for VRAM management of my application that runs a stable-diffusion.cpp and a llama.cpp server at the same time with both servers loading multiple models into VRAM. The idea is, that I want to have dynamic VRAM management so image generation never crashes.

Current plan:
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
1.  **Intelligent VAE Device Selection**:
    - Estimate VRAM for VAE decoding.
    - Check available VRAM before generation.
    - Auto-configure `vae_on_cpu: true` if the safety threshold is exceeded.
2.  **Enhance Health Checks**: Update `/internal/health` to return detailed memory stats.
3.  **Dynamic Context Re-initialization**: Allow workers to re-initialize with conservative settings (CPU offload) upon failure and retry.
4.  **UI Feedback**: Notify the user when models are offloaded or VAE is moved to CPU.


What I am interested in:
* the stable diffusion models consist of 3 different things: text encoder, stable-diffusion model and vae. Could it be possible to unload the text-encoder model when we have already done the stage and need more VRAM for the vae decoding? Can we save intermediate results?
* The llm model is needed for prompt enhancements and image tagging, it should be the first thing that is unloaded when image generation needs more VRAM, but ideally automatically re-loaded into memory, when it is needed again and there is space available. 
* If there is not enough space due to image size in VRAM, we want to do CPU offloading of one or more steps fully automatic.


This is a sophisticated resource management challenge. Your current plan is solid, but it treats the models as "monolithic blocks" (SD vs. LLM). To achieve true "surgical" management, you need to treat the Stable Diffusion process as a **pipeline of distinct stages**, each with different memory requirements.

Here are the specific improvements and answers to your questions, followed by a revised architecture plan.

### Direct Answers to Your Interests

**1. Can we unload the Text Encoder after its stage?**
**Yes, and you should.**
The Stable Diffusion inference pipeline is strictly sequential:

1. **Text Encoder:** Tokenizes prompt  Creates Embeddings. (Step complete).
2. **UNet (Diffusion):** Uses Embeddings to denoise Latents. (Repeated for  steps).
3. **VAE:** Decodes Latents  Pixel Image.

Once step 1 is done, the Text Encoder weights are **dead weight** in VRAM.

* **Strategy:** Implement a "Relay Race" memory model. The Text Encoder passes the baton (embeddings) to the UNet and immediately unloads (or moves to system RAM).
* **Implementation:** In `stable-diffusion.cpp` (C++ API), you can manage the lifetime of the `model` components if you access the underlying graph, or simply perform the conditioning pass, save the `conditioning_tensor` to CPU RAM, and destroy the encoder context before the diffusion loop begins.

**2. LLM Management: Priority Unload & Auto-Reload**
You are correct that the LLM is the "first to die." However, "unloading" is slow because reloading from disk is slow.

* **The Fix: `mmap` (Memory Mapped Files).**
Ensure your `llama.cpp` instance loads the model with `mmap`. When VRAM is needed for SD, you don't necessarily need to "close" the LLM application. You can rely on the OS (or CUDA's Unified Memory if configured) to page the idle LLM data out to system RAM.
* **Active Management:** If you need explicit control, the Orchestrator should send a `Pause/Hibernate` signal to the LLM worker. The worker releases its **KV Cache** (context memory) and **Compute Buffers**, keeping only the model weights in `mmap` (virtual memory). This allows near-instant recovery compared to a full reload.

**3. Automatic CPU Offloading for specific steps**
This is the fail-safe.

* **VAE Tiling (Crucial):** Before you resort to VAE-on-CPU (which is slow), you should implement **Tiled VAE Decoding**.
* Instead of decoding the whole 1024x1024 latent at once, the image is split into small overlapping tiles (e.g., 512x512), decoded sequentially, and stitched together.
* **Result:** Peak VRAM usage becomes *constant* regardless of image resolution. You might not need to offload to CPU at all if you tile aggressive enough.



---

### Revised Plan: The "Pipeline & Pulse" Architecture

This revised plan moves from "Model Arbitration" to "Step Arbitration."

#### 1. The "Relay Race" SD Worker

Instead of checking memory once at the start, the SD Worker evaluates memory **between stages**.

* **Stage A: Conditioning (Text Encoder)**
* **Check:** Is Text Encoder loaded? If not, load it (or run it on CPU—Text Encoders are often small enough to run fast on CPU).
* **Action:** Generate Embeddings.
* **Optimization:** If VRAM is tight, **offload Text Encoder immediately** after this step. Keep Embeddings in System RAM.


* **Stage B: Diffusion (UNet)**
* **Check:** Load UNet into VRAM.
* **Action:** Run the sampling loop.
* **Action:** Result is a small Latent Tensor (e.g., 128x128 pixels for a 1024 image).
* **Optimization:** If the next step (VAE) is predicted to OOM, **unload UNet** now.


* **Stage C: Decoding (VAE) - The Danger Zone**
* **Check:** Calculate `Required_VRAM`.
* **Decision Tree:**
1. **Standard:** If space exists, decode normally.
2. **Tiled (Preferred):** If `Required_VRAM > Available`, enable **Tiled Decoding**. This slices the VRAM spike by 4x or 8x with minimal speed penalty.
3. **CPU Offload (Fallback):** If Tiled VRAM > Available, move VAE to CPU.





#### 2. The "Elastic" LLM Worker

The LLM acts as a compressible gas that fills available space.

* **State 1: Hot (High Priority)**
* Full model in VRAM + KV Cache in VRAM.
* Used during conversation/prompting.


* **State 2: Warm (Standby)**
* Model weights in VRAM, but **KV Cache is offloaded/destroyed**.
* Triggered when SD starts the Diffusion Loop (which takes time).


* **State 3: Cold (Hibernation)**
* Model weights accessed via `mmap` (OS handles paging).
* Triggered when SD enters the VAE Decode phase (maximum pressure).



#### 3. Orchestrator: The "Traffic Light"

The Orchestrator now manages a "VRAM Budget" rather than just model states.

* **New API Endpoint:** `/internal/negotiate_vram`
* **Logic:**
1. SD Worker requests: "I need 4GB for VAE Decode in 2 seconds."
2. Orchestrator calculates: "LLM is using 8GB. Free VRAM is 2GB."
3. Orchestrator commands LLM: "Release buffers / Defrag."
4. LLM confirms: "Freed 6GB."
5. Orchestrator grants SD: "Proceed."



### Updated Implementation Roadmap

#### Phase 1: Optimization (Immediate)

1. **Enable VAE Tiling:** Search your `stable-diffusion.cpp` implementation for `vae_tiling` or `tile_size`. This single change will likely fix 90% of your large-image crashes without needing complex unloading logic.
2. **Quantize Everything:** Ensure you are using **GGUF (Q4_K_M)** for LLM and **Quantized (q5_0 or q8_0)** models for Stable Diffusion. This lowers the baseline "floor" of VRAM usage.

#### Phase 2: The Logic (Code Changes)

1. **Modify SD Worker:**
* Inject a check *after* the sampling loop but *before* `vae_decode`.
* Formula: `Estimated_VAE_Peak = (Image_Width * Image_Height * Channels * Precision_Bytes * Overhead_Factor)`.
* If `Current_Free_VRAM < Estimated_VAE_Peak`, force `vae_on_cpu = true` or `enable_tiling = true`.


2. **Modify LLM Worker:**
* Implement a `suspend` method that keeps the model loaded but frees the context window (KV Cache), which can grow very large.



#### Phase 3: Advanced Pipeline (The "Intermediate" Save)

* **Concept:** Since `stable-diffusion.cpp` returns a `latent` before decoding, you can safeguard against crashes.
* **Flow:**
1. Generate Latents.
2. **Save Latents to Disk/RAM** immediately.
3. Attempt Decode.
4. **Catch OOM:** If Decode crashes the worker, the Orchestrator sees the failure.
5. **Recovery:** Orchestrator restarts SD Worker in "Minimal Mode" (only VAE loaded, CPU mode), feeds it the *saved latents*, and finishes the image. This ensures the user never sees a "failed" image, just a slightly longer generation time.
