# Progress Report: Parallel Execution Refactor

**Date:** December 22, 2025  
**Goal:** Enable simultaneous Stable Diffusion image generation and LLM text generation on a single GPU.

---

## 1. Executive Summary
We have successfully transitioned MystiCanvas from a **monolithic process** with serialized GPU access (via mutex) to a **distributed multi-process architecture**. This resolves the fundamental conflict where the `ggml-cuda` backend's global state prevented concurrent execution within a single process.

## 2. Key Achievements

### 2.1 Multi-Process Worker Model
- **Isolation:** Created three distinct operating modes for the `mysti_server` executable:
    - **Orchestrator:** The public-facing entry point and process manager.
    - **SD-Worker:** Dedicated process for `stable-diffusion.cpp` workloads.
    - **LLM-Worker:** Dedicated process for `llama.cpp` workloads.
- **VRAM Co-existence:** Both models can now remain resident in GPU memory simultaneously, allowing for instant switching or parallel execution without reloading weights.

### 2.2 Orchestrator & Process Lifecycle
- **Unified Binary:** Implementation allows a single binary to launch itself in different modes using the `--mode` CLI flag.
- **ProcessManager (Windows):** 
    - Implemented silent spawning (no extra console windows).
    - **Log Consolidation:** Child processes inherit parent handles, merging all SD, LLM, and Orchestrator logs into one readable stream.
    - **Automatic Cleanup:** Orchestrator terminates all worker processes automatically upon exit (Ctrl+C).

### 2.3 Transparent Reverse Proxy
- **Centralized API:** Orchestrator routes `/v1/images/*` to the SD worker and `/v1/chat/*` to the LLM worker.
- **Unified Frontend:** The WebUI continues to talk to a single port (default `1234`), unaware of the multi-process backend.
- **Aggregation:** Implementation of a global `/health` endpoint that monitors the status of all sub-processes.

### 2.4 Build & Stability Improvements
- **Environment Compatibility:** Fixed standard header missing errors (`string`, `io.h`, `filesystem`) by ensuring the correct Visual Studio environment is imported.
- **Linker Resolution:** Fixed duplicate symbol conflicts between `stb_image` components across different modules.
- **Fixed Networking:** Resolved a microsecond-vs-second timeout bug in the proxy layer that caused premature generation failures.

## 3. Current System State
| Feature | Status | Note |
| :--- | :--- | :--- |
| **Image Generation** | ✅ Working | Fully isolated in SD-Worker. |
| **Parallel UI** | ✅ Working | Browser remains responsive during generation. |
| **Process Management** | ✅ Working | Clean spawn/kill/logging logic. |
| **LLM Chat** | ✅ Working | Implemented "Optimistic Streaming Proxy" for real-time tokens. |
| **VRAM Management** | ⚠️ Partial | Requires tuning when using large SDXL models (>10GB). |

## 4. Pending Tasks
1. **Dynamic VRAM Scaling:** Implement logic to automatically reduce LLM GPU layers if the SD model requires more headroom.
2. **Internal Auth:** Add token-based authentication between the Orchestrator and Workers to prevent unauthorized local access to worker ports.

## 5. Technical Implementation Details (Update Dec 22)
### Streaming Proxy Bridge
- **Challenge:** `httplib`'s `Client::Post` does not support `ResponseHandler` to intercept headers before the body stream. This broke the original plan for a fully transparent streaming proxy.
- **Solution:** Implemented an **"Optimistic Streaming Proxy"**:
    - For `POST` requests (like Chat Completions), the Orchestrator immediately responds with `200 OK` and a heuristic Content-Type (e.g., `text/event-stream`).
    - A background thread pipes the data from the worker to the client response queue in real-time.
    - `GET` requests (like Progress) use full header forwarding.
- **Result:** LLM tokens now stream instantly to the frontend, even if the model is still loading or calculating, preventing browser timeouts.

### Console Management
- **Unified Logging:** The Orchestrator now uses `STARTF_USESTDHANDLES` when spawning workers. This forces all child processes (SD and LLM workers) to write to the **same** console window as the parent.
- **Benefit:** No more popup windows. All logs are centralized in the main terminal for easier debugging.

---
**Status:** Architecture Validated. Image generation parallelization verified. Streaming Chat enabled.
