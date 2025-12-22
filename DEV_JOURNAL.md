# Developer Journal: MystiCanvas Migration

## 1. Project Goal
Transform `MystiCanvas` into a standalone, unified AI server that provides:
1.  **Image Generation:** Powered by `stable-diffusion.cpp`.
2.  **Text Generation:** Powered by `llama.cpp` (for prompt enhancement/creative assistance).
3.  **User Interface:** A Vue.js WebUI (migrated from the original SD example).

## 2. Actions Taken

### Phase 1: Preparation & Structuring
*   **Repo Cleanup:** Cleared the old Vue skeleton from `MystiCanvas`.
*   **Directory Layout:** Established a standard C++ project structure:
    *   `libs/`: Contains external dependencies (`stable-diffusion.cpp`, `llama.cpp`) as git submodules.
    *   `src/`: Contains our custom C++ backend code.
    *   `webui/`: Contains the Frontend code (Vue.js/Vite).
    *   `scripts/`: PowerShell scripts for building and maintenance.

### Phase 2: Code Migration
*   **Frontend:** Successfully moved the functional WebUI from `stable-diffusion.cpp/examples/server/webui` to `webui/`.
*   **Backend Logic:**
    *   Ported the core HTTP server logic from `stable-diffusion.cpp/examples/server/main.cpp` to `src/main.cpp`.
    *   Organized helper headers into `src/sd/` (Stable Diffusion specific), `src/server/` (HTTP/JSON), and `src/utils/` (General).

### Phase 3: Build System (CMake)
*   Created a root `CMakeLists.txt` to orchestrate the build.
*   Configured the project to link statically against `stable-diffusion` and `llama`.

## 3. Current Challenge: The "GGML Hell" (Resolved)
*   **The Issue:** Dependency conflict between `llama.cpp` and `stable-diffusion.cpp`.
*   **The Solution:** Forced `stable-diffusion.cpp` to use the top-level `ggml` from `llama.cpp` via `-DSD_BUILD_EXTERNAL_GGML=ON`.

## 4. Modernization Attempt: C++20 (Reverted)
*   **Action:** Attempted to upgrade the project to C++20 for more modern features.
*   **Failure:** `llama.cpp` (specifically `llama-chat.cpp`) failed to compile under C++20 on MSVC because C++20 treats `u8` string literals as `char8_t`, which broke their existing string streaming logic.
*   **Resolution:** Reverted the standard to **C++17** to ensure maximum compatibility with the original libraries without requiring invasive patches.

## 5. Build Optimization
*   **Ninja Generator:** Switched to the Ninja generator for significantly faster build times.
*   **Parallelism:** Enabled parallel compilation utilizing all 16 cores.
*   **Logging:** Updated the build script to generate separate logs for each component (`build_llama.log`, `build_sd.log`, etc.) to make debugging easier.

## 6. Status as of Dec 21, 2025
*   **Structure:** Ready.
*   **Code:** Ported from the fork.
*   **Build:** Currently running the verification build with C++17.

## 7. To-Do for Next Session
1.  **Verify Build Success:** Check if `mysti_server.exe` was created in `build/bin/`.
2.  **Run Server:** Execute the server and verify the WebUI is loading.
3.  **LLM Integration:** Begin adding LLM-specific endpoints to `src/main.cpp` using the `llama.h` API now that linking is solved.
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
| **Image Generation** | âœ… Working | Fully isolated in SD-Worker. |
| **Parallel UI** | âœ… Working | Browser remains responsive during generation. |
| **Process Management** | âœ… Working | Clean spawn/kill/logging logic. |
| **LLM Chat** | âœ… Working | Implemented "Optimistic Streaming Proxy" for real-time tokens. |
| **VRAM Management** | âš ï¸ Partial | Requires tuning when using large SDXL models (>10GB). |

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
