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
