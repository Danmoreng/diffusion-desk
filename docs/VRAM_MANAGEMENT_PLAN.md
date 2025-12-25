# Plan: Efficient VRAM Management & Low-Latency Arbitration

## Goal
To maintain low latency for both LLM and Stable Diffusion models while preventing Out-of-Memory (OOM) errors, especially during the VRAM-intensive VAE decoding phase. We want to avoid aggressive "brute-force" unloading and instead move toward a more surgical resource management approach.

## 1. Centralized Resource Manager (Orchestrator)
The Orchestrator will evolve from a simple proxy to a **Resource Arbitrator**.

*   **VRAM Registry**: Maintain a map of active processes and their estimated/reported VRAM footprints.
*   **Arbitration Logic**: Before forwarding a "heavy" request (like image generation), the Orchestrator checks the projected VRAM usage.
*   **Soft vs. Hard Unloading**: 
    *   *Soft*: Instruct a worker to purge non-essential buffers (e.g., KV cache, temporary compute buffers).
    *   *Hard*: Request a full model unload only if the projected request cannot fit otherwise.

## 2. Dynamic Component Offloading (SD Worker)
Instead of unloading the entire LLM to make room for VAE decoding, we will implement surgical offloading within the SD worker itself.

*   **VAE-on-CPU Strategy**: 
    *   Calculate required VAE memory based on `width * height`.
    *   If `Available_VRAM < (Diffusion_Model + VAE_Buffer)`, automatically set `vae_on_cpu = true` for that specific generation.
    *   This keeps the main Diffusion weights in VRAM, allowing subsequent generations to start instantly while incurring only a 2-3 second penalty during the final decode step.
*   **Memory Reporting**: Workers should report their current allocated VRAM to the Orchestrator via the `/internal/health` endpoint after every load/task.

## 3. Latency-Optimized State Transitions
*   **LRU Policy**: Implement a Least Recently Used policy for models. If a user hasn't chatted with the LLM for 10 minutes, it becomes the first candidate for unloading if SD needs space.
*   **Predictive Pre-warming**: If VRAM is available, keep both models loaded.
*   **Parallel Loading**: Improve the worker initialization to allow the weights to remain in system RAM (if possible) or use `mmap` efficiently to speed up re-loading if a model was recently swapped out.

## 4. Implementation Steps
1.  **Enhance Health Checks**: Update `/internal/health` in both workers to return detailed memory stats (current vs. peak).
2.  **VAE Memory Profiling**: Add a utility function to estimate VRAM requirements for various resolutions.
3.  **Orchestrator Guardrails**: Implement the "Pre-flight check" in the Orchestrator that queries workers before committing to a heavy task.
4.  **UI Feedback**: Report to the WebUI when a model is being offloaded or if VAE was moved to CPU, so the user understands the slight latency change.
