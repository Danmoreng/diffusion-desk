# 🗺️ MystiCanvas Development Roadmap

This document outlines the strategic milestones for evolving MystiCanvas from a source-built prototype into a professional, secure, and user-friendly Windows application.

---

## 🎯 Project Vision
To provide a high-performance, private, and local AI creative suite that seamlessly integrates Image Generation (Stable Diffusion) and Large Language Models (Llama), optimized for Windows systems with deep hardware integration.

---

## 🏗️ Milestone 1: Architectural Hardening & Security
*Focus: Move away from hardcoded configurations and secure the process boundaries.*

- [x] **1.1 Dynamic Configuration System**
    - Implement a central `config.json` loader.
    - Support for environment variables and `%APPDATA%` path resolution.
    - Automatic directory discovery for `/models`, `/checkpoints`, and `/outputs`.
- [x] **1.2 SPA Routing & Fallback**
    - Update the Orchestrator to serve `index.html` for unknown routes.
    - Fixes the "404 on refresh" bug in Vue Router.
- [x] **1.3 Internal Security Layer**
    - Restrict all worker subprocesses to `127.0.0.1`.
    - Implement **Header-based Auth**: Orchestrator generates a transient API key on startup and passes it to workers.
    - Workers reject any request lacking the valid internal token.

## ⚡ Milestone 2: Real-time Interactive Experience
*Focus: Transition from one-way status updates to a full-duplex interactive UI.*

- [x] **2.1 WebSocket Integration**
    - Replace SSE (`/stream/progress`) with a robust WebSocket implementation.
    - Unified stream for generation progress, VRAM metrics, and system alerts.
- [ ] **2.2 Bidirectional Control**
    - ~~Implement **Request Cancellation**: Stop a 100-step generation mid-way via WS signal.~~ (Blocked: `stable-diffusion.cpp` API does not support interruption via callback yet)
    - Dynamic Parameter Updates: Update CFG or Guidance scales while the LLM is "thinking."
- [x] **2.3 UI Cleanup**
    - Remove legacy "Manual Load" buttons.
    - Implement "Model Hot-Swap" indicator. (Completed: LLM and SD model selection integrated into Sidebar with VRAM status)

## 💾 Milestone 3: Persistence & Context Management
*Focus: Solving the "Undo" problem and managing user history.*

- [x] **3.1 SQLite Persistence Layer**
    - Schema for `Generations`, `Prompts`, and `ModelMetadata`.
    - Link every `.png` file to a database entry containing its full generation recipe (JSON).
- [x] **3.2 Infinite Prompt History**
    - Custom history stack in Pinia.
    - "Snapshot" prompts before LLM enhancement to allow easy reversion.
- [x] **3.3 Enhanced Gallery**
    - Searchable gallery by tag, model, or date.
    - "One-click" parameter injection from any historical image.

## 🎨 Milestone 4: Intelligence & Style Workflows
*Focus: Leveraging the LLM to assist the creative process.*

- [ ] **4.1 Style Extraction**
    - Automated pipeline: Image -> Prompt -> LLM -> "Art Style Keywords."
    - Ability to "Save Style" into a local library.
- [ ] **4.2 Smart Prompt Expansion**
    - Context-aware enhancement based on the selected model (e.g., specific tags for Flux vs. SDXL). (In-progress: Basic "🪄 Enhance" feature implemented)
- [ ] **4.3 Aspect Ratio Intelligence**
    - Auto-suggest optimal resolutions based on the loaded model's training bucket.

## 📦 Milestone 5: Distribution & IPC Optimization
*Focus: Performance tuning and the "One-Click" installation experience.*

- [ ] **5.1 IPC Performance (Named Pipes)**
    - Implement Windows Named Pipes for Orchestrator <-> Worker communication.
    - Reduces TCP overhead and increases security via OS-level permissions.
- [ ] **5.2 Release Build Automation**
    - Multi-stage build script for Release binaries (MSVC).
    - Resource embedding (icon, version info).
- [ ] **5.3 Windows Installer**
    - Create an Inno Setup or NSIS installer.
    - Bundle the WebUI `dist`, C++ executables, and a "First Run" wizard to download base models.

---

## 🛠️ Technical Specifications

| Component | Technology |
| :--- | :--- |
| **Orchestrator** | C++ (httplib, nlohmann/json, ixwebsocket) |
| **Workers** | C++ (llama.cpp, stable-diffusion.cpp) |
| **Frontend** | Vue 3 + Pinia + Vite |
| **IPC** | HTTP/REST (Current) -> Named Pipes (Target) |
| **Database** | SQLite 3 |
| **Auth** | Random API Token (Transient) |

---

## ✅ Current Status: Milestone 4 (Ready to Start)
*Last Updated: 2025-12-27*
- [x] Milestone 1: Architectural Hardening & Security completed.
- [x] Milestone 2: Real-time Interactive Experience completed.
- [x] Milestone 3: Persistence & Context Management completed.
- [ ] Next: Milestone 4 - Intelligence & Style Workflows.
