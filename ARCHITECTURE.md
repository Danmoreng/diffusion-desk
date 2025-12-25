# MystiCanvas Architecture

MystiCanvas is a local, privacy-focused AI sandbox that integrates Large Language Models (LLM) and Stable Diffusion (SD) image generation. It uses a **multi-process architecture** to manage resource-intensive AI tasks efficiently, ensuring system stability and UI responsiveness.

## High-Level Overview

The system consists of a central **Orchestrator** process that serves a web-based user interface and manages specialized **Worker** processes for AI inference. This design chooses to resolve CUDA context conflicts and allow parallel execution of text and image generation.

```mermaid
graph TD
    User[User (Browser)] <-->|HTTP/WebSocket| Orch[Orchestrator]
    
    subgraph "Backend Processes"
        Orch -->|Spawns/Manages| SDWorker[SD Worker]
        Orch -->|Spawns/Manages| LLMWorker[LLM Worker]
        
        Orch <-->|Auth HTTP Proxy| SDWorker
        Orch <-->|Auth HTTP Proxy| LLMWorker
    end
    
    subgraph "Libraries"
        SDWorker -->|Uses| LibSD[stable-diffusion.cpp]
        LLMWorker -->|Uses| LibLlama[llama.cpp]
        Orch -->|Uses| LibIXWS[ixwebsocket]
    end
```

## Architectural Decisions

### Multi-Process vs. Single-Process
Initially, the project attempted to run both engines in a single process. This was abandoned due to CUDA context conflicts and responsiveness issues. Isolating image generation in a separate worker allows the Orchestrator to remain responsive and broadcast metrics.

### The Orchestrator (`src/orchestrator/`)
The Orchestrator is the main entry point (`mysti_server`).
- **Unified Namespacing:** 
  - `/app/` -> Static Vue.js frontend assets.
  - `/v1/` -> Proxied API routes.
  - `/` -> Automatically redirects to `/app/`.
- **WebSocket Hub:** Serves a WebSocket server (port `listen_port + 3`) using `IXWebSocket`. It broadcasts:
  - **System Metrics:** Real-time VRAM usage (Total, Free, Worker-specific).
  - **Generation Progress:** Proxied from the SD Worker's SSE stream via a line-buffered internal parser.
- **Internal Security Layer:** 
  - Generates a **transient API token** on startup.
  - Workers listen only on `127.0.0.1`.
  - All internal communication requires the `X-Internal-Token` header.
- **Process Manager:** Spawns and monitors worker health, automatically restarting crashed processes and restoring model state.

### Worker Processes (`src/workers/`)
Workers are specialized subprocesses that handle high-latency inference.
- **Isolaton:** Workers are protected from external access and only accept connections from the Orchestrator.
- **SD Worker:** Wraps `stable-diffusion.cpp`, handles model loading and generation.
- **LLM Worker:** Wraps `llama.cpp`, provides chat completion endpoints.

### Frontend WebUI (`webui/`)
Built with Vue 3 and Pinia.
- **WebSocket Integration:** Connects to the Orchestrator hub for real-time updates, replacing all legacy polling and EventSource logic.
- **Dynamic Routing:** Supports SPA history mode with a server-side fallback to `index.html`.

## Configuration System
Centralized `config.json` allows overriding default ports, paths, and performance settings. Supports Windows environment variables (e.g., `%APPDATA%`) for cross-system portability.

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