# DiffusionDesk VRAM Management & Arbitration (v2.0)

## Overview

DiffusionDesk implements a proactive, centralized VRAM management system designed to allow Stable Diffusion (SD) and Large Language Models (LLM) to coexist efficiently on consumer hardware. 

Version 2.0 introduces a **Smart Queue** system and **VRAM Commitment**, moving from a reactive "reject-on-tight-resources" approach to a "wait-and-escalate" model.

## Architecture

1.  **Orchestrator (`ResourceManager`)**: 
    *   Tracks real-time polled usage from workers.
    *   Tracks **Committed VRAM**: Memory promised to starting tasks that haven't been physically allocated yet.
    *   **Effective Free VRAM** = `Total VRAM - (Polled Usage + Committed Buffer)`.
2.  **Service Controller**:
    *   Implements the **Smart Queue**. 
    *   If a requested model is not loaded, the request blocks and triggers an automatic background load instead of failing.
    *   Injects mitigations and manages the commitment lifecycle.
3.  **Workers**: 
    *   Support **Swap-to-RAM/CPU** commands to release VRAM without fully purging the model from system memory.

## Core Mechanisms

### 1. Lazy Loading & Smart Queue
When a generation or completion request arrives for a model that isn't active:
*   The Orchestrator sends a `LoadCommand` to the worker.
*   The request thread enters a `WAIT` state (up to 60s).
*   Once the worker signals `LOADED`, the blocked request proceeds immediately.
*   Multiple simultaneous requests for the same model will queue up and wait for the single load operation.

### 2. VRAM Reservation (Commitment)
To prevent race conditions where two requests simultaneously see free memory:
1.  **Request Arrival:** `ResourceManager` calculates estimated cost.
2.  **Commitment:** `committed_vram_buffer += estimated_cost`.
3.  **Execution:** Worker begins allocation.
4.  **Completion:** Once the HTTP proxy call returns (or progress starts), `committed_vram_buffer -= estimated_cost`.

### 3. Arbitration Escalation (SD Generation)
Instead of immediately killing the LLM, the system follows these steps:
1.  **Check:** Does it fit in `Effective Free VRAM`? -> **Proceed.**
2.  **LLM Swap:** If tight, send `POST /v1/llm/offload`. LLM moves to system RAM. VRAM is freed, but the model stays "warm".
3.  **CLIP Offload:** Check User Preset. If `force_clip_cpu` is true, encoder stays on CPU.
4.  **VAE Tiling:** If still tight, force tiling to reduce peak consumption.
5.  **Hard Unload:** Only if all above fail, send `POST /v1/llm/unload`.

### 4. Arbitration Escalation (LLM Load)
1.  **Single LLM Policy:** Any other LLM is hard unloaded.
2.  **SD Check:** If SD is currently generating, the load waits in the queue.
3.  **SD Offload:** If SD is idle but VRAM is tight, send `POST /v1/models/offload`. SD weights move to RAM.

## User Configuration (Presets)

Users can define the memory strategy per model in the Preset settings:
*   **Force CLIP on CPU:** Reduces base VRAM cost by ~1.5GB (SDXL).
*   **Force VAE Tiling:** Ensures large generations never OOM during the decode phase.