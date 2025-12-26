# MystiCanvas: Comprehensive Architecture & Database Implementation Plan

**Date:** 2025-12-26
**Version:** 1.0
**Target Audience:** External Consultants, AI Architects, Senior Developers

---

## 1. Project Overview

**MystiCanvas** is a high-performance, local, and private AI creative studio for Windows. It integrates **Stable Diffusion** (image generation) and **Large Language Models** (text/reasoning) into a unified, privacy-first workflow.

**Key Philosophy:**
- **Local First:** No data leaves the user's machine.
- **Resource Management:** Dedicated handling of VRAM to allow concurrent or sequential usage of large models without crashing.
- **Process Isolation:** Stability through multi-process architecture.

---

## 2. System Architecture

The application uses a **Multi-Process Architecture** to ensure stability and resource isolation.

### 2.1 Process Topology

```mermaid
graph TD
    User[User Browser] <-->|HTTP/WebSocket (Port 1234)| Orch[Orchestrator Process]
    
    subgraph "Local Machine (Hidden from User)"
        Orch -->|Spawns & Monitors| SD[SD Worker Process]
        Orch -->|Spawns & Monitors| LLM[LLM Worker Process]
        
        Orch <-->|Internal HTTP (Port 1235)| SD
        Orch <-->|Internal HTTP (Port 1236)| LLM
        
        SD -->|GPU/VRAM| GPU[NVIDIA GPU]
        LLM -->|GPU/VRAM| GPU
    end
```

### 2.2 Component Roles

1.  **Orchestrator (`mysti_server`)**
    *   **Role:** The "Brain" and Gateway.
    *   **Responsibilities:**
        *   Serves the WebUI (Vue.js SPA).
        *   Manages the lifecycle of worker processes (spawning, health checks, restarting).
        *   Proxies public API requests (`/v1/*`) to the appropriate worker.
        *   Hosts the WebSocket Hub for real-time metrics and progress streaming.
        *   Enforces internal security via a transient API token.
    *   **Stack:** C++ (httplib, ixwebsocket).

2.  **SD Worker (`mysti_sd_worker`)**
    *   **Role:** Image Generation Engine.
    *   **Responsibilities:**
        *   Loads Stable Diffusion models (GGUF, Safetensors).
        *   Executes `txt2img`, `img2img`, `upscale`.
        *   Manages VRAM for visual tasks.
    *   **Stack:** C++, `stable-diffusion.cpp`.

3.  **LLM Worker (`mysti_llm_worker`)**
    *   **Role:** Text Intelligence Engine.
    *   **Responsibilities:**
        *   Loads LLMs (GGUF).
        *   Provides Chat Completion APIs (OpenAI-compatible).
        *   Performs prompt enhancement and auto-tagging.
    *   **Stack:** C++, `llama.cpp`.

### 2.3 Data Flow (Current)

*   **Image Generation:**
    1.  Frontend sends POST `/v1/images/generations` to Orchestrator.
    2.  Orchestrator proxies to SD Worker.
    3.  SD Worker generates image, saves `.png` to `outputs/`, and `.json` metadata sidecar.
    4.  SD Worker returns base64 preview to Frontend.
*   **Prompt Enhancement:**
    1.  Frontend sends POST `/v1/chat/completions` to Orchestrator.
    2.  Orchestrator proxies to LLM Worker.
    3.  LLM Worker returns enhanced text.

---

## 3. Database Implementation Plan (The "Theory Crafting" Target)

**Current Status:** File-system based. History is built by scanning the `outputs/` directory. This is slow and limited.
**Goal:** Implement **SQLite** to enable rich metadata, tagging, relationships, and rapid search.

### 3.1 Technology Stack
*   **Engine:** SQLite 3 (Embedded).
*   **Host:** Orchestrator (Centralized access).
*   **ORM/Wrapper:** SQLiteCpp or raw C++ SQLite interface.

### 3.2 Proposed Schema

We need to validate and refine this schema for maximum flexibility.

#### Table: `generations`
*Master record for every image.*
```sql
CREATE TABLE generations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT UNIQUE NOT NULL,       -- Public ID
    file_path TEXT NOT NULL,         -- Relative to outputs/
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    prompt TEXT,
    negative_prompt TEXT,
    seed INTEGER,
    width INTEGER,
    height INTEGER,
    steps INTEGER,
    cfg_scale REAL,
    model_hash TEXT,                 -- Identification for the model used
    is_favorite BOOLEAN DEFAULT 0,
    parent_uuid TEXT,                -- For img2img/upscaling lineage
    FOREIGN KEY(parent_uuid) REFERENCES generations(uuid)
);
```

#### Table: `tags`
*Categorization system.*
```sql
CREATE TABLE tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    category TEXT DEFAULT 'general'  -- e.g., 'style', 'subject', 'artist'
);
```

#### Table: `image_tags`
*Many-to-Many relationship.*
```sql
CREATE TABLE image_tags (
    generation_id INTEGER,
    tag_id INTEGER,
    source TEXT DEFAULT 'user',      -- 'user' or 'llm_auto'
    confidence REAL DEFAULT 1.0,     -- For AI-generated tags
    PRIMARY KEY(generation_id, tag_id),
    FOREIGN KEY(generation_id) REFERENCES generations(id),
    FOREIGN KEY(tag_id) REFERENCES tags(id)
);
```

#### Table: `prompt_templates`
*Library of reusable styles.*
```sql
CREATE TABLE prompt_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    content TEXT NOT NULL,
    description TEXT
);
```

### 3.3 Proposed Workflows

1.  **Auto-Archiving:**
    *   On startup, Orchestrator scans `outputs/` for files missing in the DB and imports them (reading sidecar JSONs).
2.  **LLM Auto-Tagging (The "Killer Feature"):**
    *   **Trigger:** After a successful generation.
    *   **Process:** Orchestrator sends the *prompt* (and potentially the image via vision model in future) to the LLM.
    *   **Prompt:** "Analyze this generation prompt and extract 5-10 keywords for categorization."
    *   **Result:** Tags are inserted into `image_tags` with `source='llm_auto'`.
3.  **Search:**
    *   Frontend provides a "Gallery" view.
    *   Users can filter by: Model, Date, Tags (User vs AI), and Prompt keywords.

---

## 4. Topics for Consultation

We are seeking expert advice on the following implementation details:

1.  **Vector Search Feasibility:**
    *   Is it feasible to store embedding vectors for prompts in SQLite (using an extension like `sqlite-vss`) locally?
    *   This would allow semantic search (e.g., searching "sad robot" finds images prompted with "melancholic android").
2.  **Concurrency:**
    *   Since the Orchestrator is multi-threaded (http server), how should we handle SQLite connections? (WAL mode, mutex strategies).
3.  **Tag Taxonomy:**
    *   How should we structure the system prompt for the LLM to ensure consistent tagging? (e.g., avoiding synonyms like "cat" vs "feline").
4.  **Performance:**
    *   Handling 10,000+ images in a scrolling grid. Pagination strategies vs. virtual scrolling.

---

## 5. Current File Structure Reference

```text
C:\StableDiffusion\MystiCanvas\
├── src\
│   ├── orchestrator\   (DB logic should live here)
│   ├── workers\        (SD/LLM logic)
│   └── sd\             (Shared SD logic)
├── webui\
│   └── src\
│       ├── stores\     (State management)
│       └── views\      (Gallery UI needs creation)
└── outputs\            (Existing image storage)
```
