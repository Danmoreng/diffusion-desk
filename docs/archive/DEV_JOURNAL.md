# Developer Journal: MystiCanvas Migration

## 1. Project Goal
Transform `MystiCanvas` into a standalone, unified AI server that provides:
1.  **Image Generation:** Powered by `stable-diffusion.cpp`.
2.  **Text Generation:** Powered by `llama.cpp` (for prompt enhancement/creative assistance).
3.  **User Interface:** A Vue.js WebUI.

## 2. Actions Taken

### Phase 1: Preparation & Structuring
*   **Repo Cleanup:** Established a standard C++ project structure.
*   **Directory Layout:** `libs/` (submodules), `src/` (custom code), `webui/` (frontend), `scripts/` (build/run).

### Phase 2: Build System (CMake)
*   **Dependency Resolution:** Forced `stable-diffusion.cpp` to use `llama.cpp`'s `ggml` via `-DSD_BUILD_EXTERNAL_GGML=ON` to solve target collisions.
*   **C++ Standard:** Set to **C++17** for maximum compatibility with ML libraries.

### Phase 3: Parallel Execution Refactor (Dec 22)
*   Transitioned from monolithic to multi-process architecture to allow simultaneous SD and LLM execution on one GPU.
*   Implemented **Optimistic Streaming Proxy** for real-time LLM token delivery.

### Phase 4: Multi-Executable & Stability Refactor (Dec 24)
*   **Architecture Evolution:** Split the single binary into three specialized executables:
    - `mysti_server.exe`: Orchestrator & Proxy. Minimal, stable, serves WebUI.
    - `mysti_sd_worker.exe`: SD generation workload.
    - `mysti_llm_worker.exe`: LLM generation workload.
*   **Watchdog Implementation:** Orchestrator now monitors worker processes and automatically restarts them if they crash (e.g., due to OOM).
*   **State Recovery:** Orchestrator intercepts model load calls and automatically restores the last active model upon worker restart.
*   **Dependency Isolation:** Each worker links only against its specific ML libraries, simplifying the build and increasing robustness.

## 3. Current System State
| Feature | Status | Note |
| :--- | :--- | :--- |
| **Image Generation** | ✅ Working | Fully isolated in SD-Worker. |
| **LLM Chat** | ✅ Working | Streaming tokens via Proxy. |
| **Auto-Recovery** | ✅ Working | Workers restart automatically on crash. |
| **State Persistence**| ✅ Working | Last model reloaded after restart. |
| **Frontend Error UI**| ✅ Working | Displays worker status correctly. |

## 4. Pending Tasks
1. **Dynamic VRAM Scaling:** Automatically adjust LLM layers based on SD VRAM demand.
2. **Internal Auth:** Secure worker communication ports.

---
**Status:** Architecture Refined. System is resilient to crashes and scalable.