# LLM Server Integration Plan

## 1. Objective
Integrate the native `llama.cpp` server logic into the `mysti_server` backend to provide OpenAI-compatible endpoints (Chat, Completions, Embeddings) for prompt rewriting and exploration features.

## 2. Current Status
- **Wrapper Implemented**: `LlamaServer` class (`src/server/llama_server.cpp/hpp`) manages the `server_context` and `server_routes` from `llama.cpp`.
- **Request Bridging**: Implemented a `bridge_handler` to map `httplib` requests/responses to native `llama.cpp` server types, including chunked streaming support.
- **Unified API**: Endpoints registered:
    - `/v1/llm/load`: Loads a GGUF model into the LLM context.
    - `/v1/chat/completions`: Native OAI-compatible chat.
    - `/v1/completions`, `/v1/embeddings`, `/v1/tokenize`, `/v1/detokenize`.
- **UI Progress**: Added LLM selection, loading, and a test completion box in the **Settings** view.

## 3. Current Blockers & Solved Issues
- **Solved: Parameter Shifting**: Fixed a critical memory layout mismatch in `sd_ctx_params_t` which was causing black/noisy images.
- **Solved: Dependencies**: Disabled `LLAMA_CURL` and added `ws2_32` for Windows network compatibility.
- **In-Progress: JSON Symbol Conflict**: 
    - `llama.cpp` defines `json` globally as `nlohmann::ordered_json`.
    - `MystiCanvas` previously used a global `using json = nlohmann::json`.
    - **Latest Change**: Removed all global `using json` declarations. Scoped all local JSON usage to `mysti::json`.

## 4. Next Steps for New Session
1. **Verify Build**: Run `.\MystiCanvas\scripts\build.ps1` to confirm the namespacing resolved the `C2371` redefinition errors.
2. **Runtime Test**:
    - Load an LLM (e.g., `models/text-encoder/Qwen3...`).
    - Run a completion test from the Settings UI.
    - Simultaneously generate an image to ensure dual-backend CUDA stability.
3. **Feature Implementation**:
    - Develop the "Subtle Prompt Variation" logic for the Exploration UI.
    - Implement a structured prompt template for the LLM to ensure consistent formatting.

## 5. Maintenance Notes
- **Namespacing**: Never use `using namespace` or global `using json` in headers.
- **Alignment**: If submodules are updated, re-verify the `sd_ctx_params_t` struct layout in `stable-diffusion.h` against `common.hpp`.
