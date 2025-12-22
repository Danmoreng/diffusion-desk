# MystiCanvas Architecture

MystiCanvas is a local, privacy-focused AI sandbox that integrates Large Language Models (LLM) and Stable Diffusion (SD) image generation. It uses a **multi-process architecture** to manage resource-intensive AI tasks efficiently, ensuring system stability and UI responsiveness.

## High-Level Overview

The system consists of a central **Orchestrator** process that serves a web-based user interface and manages specialized **Worker** processes for AI inference. This design was chosen to resolve CUDA context conflicts and allow parallel execution of text and image generation.

```mermaid
graph TD
    User[User (Browser)] <-->|HTTP/WebSocket| Orch[Orchestrator (mysti_server --mode orchestrator)]
    
    subgraph "Backend Processes"
        Orch -->|Spawns/Manages| SDWorker[SD Worker Process (--mode sd-worker)]
        Orch -->|Spawns/Manages| LLMWorker[LLM Worker Process (--mode llm-worker)]
        
        Orch <-->|HTTP Proxy| SDWorker
        Orch <-->|HTTP Proxy| LLMWorker
    end
    
    subgraph "Libraries"
        SDWorker -->|Uses| LibSD[stable-diffusion.cpp]
        LLMWorker -->|Uses| LibLlama[llama.cpp]
    end
```

## Architectural Decisions

### Multi-Process vs. Single-Process
Initially, the project attempted to run both engines in a single process. This was abandoned due to:
1.  **CUDA Context Conflicts:** Both `llama.cpp` and `stable-diffusion.cpp` use `ggml-cuda`. Managing VRAM pools and global backend state in a single process led to OOM crashes and resource contention.
2.  **Responsiveness:** Image generation (a heavy, blocking GPU operation) freezes the main thread. Isolating it in a separate worker allows the Orchestrator to keep serving the UI and streaming LLM tokens smoothly.

### The Orchestrator (`src/orchestrator/`)
The Orchestrator is the main entry point (`mysti_server`) when no mode is specified.
- **Process Manager:** Spawns worker processes using `STARTF_USESTDHANDLES` on Windows to consolidate logs into a single console window.
- **Reverse Proxy:** Routes requests to the appropriate worker:
  - `/v1/images/*`, `/v1/models/*` -> **SD Worker**
  - `/v1/chat/*`, `/v1/llm/*` -> **LLM Worker**
- **Optimistic Streaming Proxy:** 
  - For LLM chat completion (`POST`), the standard HTTP client blocks until the full response is ready. 
  - To support streaming tokens, the Proxy immediately returns `200 OK` with `text/event-stream` and pipes data from the worker to the client in a background thread as it arrives.

### Worker Processes (`src/workers/`)
The `mysti_server` binary re-launches itself in "worker mode" to host specific capabilities.

- **Stable Diffusion Worker (`sd-worker`):**
  - Wraps `stable-diffusion.cpp`.
  - Handles Model loading (checkpoint, LoRA, VAE).
  - Exposes endpoints for text-to-image and image-to-image generation.
  - Independent VRAM management.
  
- **LLM Worker (`llm-worker`):**
  - Wraps `llama.cpp`.
  - Manages the context and KV cache for the language model.
  - Exposes OpenAI-compatible endpoints for chat completions.

### Frontend WebUI (`webui/`)
A modern web interface built with:
- **Vue.js 3** (Composition API)
- **Vite** (Build tool)
- **Bootstrap 5** (Styling)
- **Pinia** (State Management)

The WebUI communicates exclusively with the Orchestrator's API (default port 1234), unaware of the underlying worker processes.

## Directory Structure

| Directory | Description |
|-----------|-------------|
| `src/main.cpp` | Main entry point. Dispatches execution to Orchestrator or Workers based on `--mode` CLI arg. |
| `src/orchestrator/` | Orchestrator logic: `process_manager` (spawning), `proxy` (routing). |
| `src/workers/` | Worker implementations: `sd_worker.cpp`, `llm_worker.cpp`. |
| `src/server/` | `llama_server` implementation (wrapping llama.cpp's server logic). |
| `src/sd/` | Shared Stable Diffusion logic (API endpoints, model loading). |
| `webui/` | Frontend source code. |
| `libs/` | Git submodules for `llama.cpp` and `stable-diffusion.cpp`. |

## Build System
- **CMake:** Used for the C++ backend. It compiles `mysti_server` and links against `llama` and `stable-diffusion` static libraries.
- **NPM/Vite:** Used for the Frontend. Assets are built and copied to the `public/` directory, which the C++ server serves.