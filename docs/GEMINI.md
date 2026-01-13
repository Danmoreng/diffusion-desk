# DiffusionDesk Project Context

## Overview
DiffusionDesk is a high-performance, self-hosted Creative AI server that integrates image generation (Stable Diffusion) and text generation (LLM) into a unified local application.

## Architecture
The system uses a **multi-process architecture** to handle resource-intensive AI tasks:
- **Orchestrator (`mysti_server`):** The main process.
    - Serves the Vue.js WebUI (`/app/`).
    - Acts as a reverse proxy/API gateway (`/v1/`).
    - Manages a WebSocket hub for real-time status (VRAM usage, generation progress).
    - Spawns and monitors worker processes.
- **Workers:** Specialized subprocesses for inference.
    - **SD Worker:** Wraps `stable-diffusion.cpp` for image generation.
    - **LLM Worker:** Wraps `llama.cpp` for text generation.
    - Workers communicate with the Orchestrator via internal HTTP APIs protected by transient tokens.

## Tech Stack
- **Backend:** C++ (CMake)
    - **Libraries:** `stable-diffusion.cpp`, `llama.cpp`, `ixwebsocket`, `SQLiteCpp`.
- **Frontend:** Vue 3 + TypeScript
    - **Build Tool:** Vite
    - **State Management:** Pinia
    - **Styling:** Bootstrap 5
- **Scripting:** Platform-specific automation scripts (PowerShell/Bash)

## Directory Structure
- `src/`: C++ Backend source code.
    - `orchestrator/`: Main server logic, process management, proxying.
    - `workers/`: Worker implementation wrappers.
    - `sd/`, `server/`: Specific AI logic.
- `webui/`: Vue.js frontend source.
- `libs/`: Submodules (`llama.cpp`, `stable-diffusion.cpp`, `ixwebsocket`).
- `scripts/`: Build and run scripts for supported platforms.
- `models/`: Directory for AI models (not tracked in git).
- `config.json`: Runtime configuration.

## Key Workflows

### Building
**Always** use the provided platform-specific build scripts in the `scripts/` directory to ensure all components (frontend + backend) are built correctly.

### Running
Use the platform-specific run script in `scripts/`.
- Starts the Orchestrator, which then spawns workers.
- Default UI URL: `http://localhost:1234/app/`

### Development Guidelines

1.  **Conventions:** Follow existing C++ and TypeScript coding styles.

2.  **Safety:** Always verify compilation after changes using the build script.

3.  **Submodules:** Be aware of `libs/` submodules; ensure they are up to date if build errors related to them occur (`git submodule update --init --recursive`).

4.  **Libraries:** Code in the `libs/` directory consists of git submodules. You should **only read** this code to understand library functionality; **never modify it**.
