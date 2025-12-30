# MystiCanvas Development Plan (Consolidated)

**Last Updated:** December 30, 2025
**Status:** Active Development

This document outlines the roadmap for MystiCanvas, merging original milestones with architectural review recommendations and the "Model Presets + Creative Assistant" vision.

---

## 0. Core Principles

1.  **Stability through Isolation:** Orchestrator + specialized workers remains the default architecture.
2.  **Predictability before Intelligence:** Solidify the substrate (DB, VRAM prediction) before adding "smart" agentic features.
3.  **HTTP for IPC:** Keep HTTP for internal communication for debuggability; optimize only if proven bottleneck.
4.  **Database as Memory:** All state (presets, prompt libraries, search, jobs) must be durable.

---

## Release A — Platform Contracts & Observability (Foundation)

**Goal:** Make every core subsystem *inspectable*, *restartable*, and *consistent*.

*   [ ] **A1. Standardize Contracts (Verify):**
    *   [ ] Ensure all workers return standard health object: `{ ok, service, version, model_loaded, vram_allocated_mb, vram_free_mb }`.
    *   [ ] Ensure all endpoints use standard error envelope.
    *   [ ] Verify internal token auth on *all* routes (including proxy).
*   [ ] **A2. Worker Lifecycle Resilience:**
    *   Orchestrator monitors worker health.
    *   Automatic restart on crash.
    *   Broadcast "system alert" to UI via WebSockets.
*   [ ] **A3. Structured Logs & Correlation:**
    *   Add request IDs to logs.
    *   Structured JSON logging for easy parsing.
*   [ ] **A4. UI Modularity & Stores:**
    *   Extract `Sidebar.vue` sub-components (e.g., `VramIndicator.vue`, `ModelSelector.vue`).
    *   Move business rules (validation, generation logic) from components into Pinia store actions.
*   [ ] **A5. Config Hygiene:**
    *   Move hardcoded system prompts (Assistant, Tagger) to `config.json`.
    *   Externalize tool schemas/descriptions.

**Definition of Done:**
*   `curl /internal/health` works uniformly across all services.
*   UI components do not contain generation logic; they only invoke Store actions.
*   No prompt text exists in `.cpp` or `.ts` files; all are loaded from config.

---

## Release B0 — "Stop the Bleeding" (Immediate Reliability)

**Goal:** Prevent silent failures and improve error visibility before full VRAM system is ready.

*   [ ] **B0.1. Fail Loudly:** Detect "blank output" or generation failure and return hard error to UI.
*   [ ] **B0.2. Conservative Retry:** Implement single retry with conservative settings on OOM/failure.

---

## Release B — VRAM Management v2 (Predictive + Surgical)

**Goal:** Prevent OOM *without* brute-force unloading; explain behavior to the user.

*   [ ] **B1. Health-Driven VRAM Registry:**
    *   Workers report detailed memory usage.
    *   Orchestrator tracks model footprints.
*   [ ] **B2. Predictive Arbitration (Orchestrator):**
    *   Compute expected VRAM (Weights + Compute + Safety).
    *   Logic: Proceed vs. Soft Unload (KV) vs. Hard Unload.
*   [ ] **B2.5. Latency-Optimized State Transitions:**
    *   LRU / Idle unload policy (e.g., "unload after 10m idle").
    *   Pre-warm models when resources allow.
*   [ ] **B3. Surgical Worker-Level Mitigations:**
    *   Dynamic VAE tiling based on resolution.
    *   Auto VAE-on-CPU fallback if VRAM is tight.
    *   **B3.4 Text Encoder Offload:** Enable `clip_on_cpu` for massive encoders (e.g., T5XXL) per preset.
*   [ ] **B4. UI Feedback:**
    *   Indicate "Projected vs Actual" VRAM.
    *   Notifications for "VAE moved to CPU" or "LLM Unloaded".

**Definition of Done:**
*   Zero "silent crashes" or blank images due to VRAM.
*   System creates specific log entry when falling back to CPU or unloading.
*   Idle models unload automatically after configured timeout.

---

## Release C — Database Hardening v2 (The "Memory" Layer)

**Goal:** A robust persistence layer supporting advanced features (Search, Jobs, Presets).

*   [ ] **C1. Schema Versioning & Migrations:**
    *   Use `PRAGMA user_version`.
    *   Implement idempotent migration system on startup.
*   [ ] **C2. Performance Optimization:**
    *   Keyset pagination for Gallery (cursor-based).
    *   Verify/Add indexes.
*   [ ] **C3. Asset Management:**
    *   `generation_files` table (thumbnails, previews, masks).
    *   Job for background thumbnail creation.
*   [ ] **C4. FTS5 Search:**
    *   Virtual table for prompt search.
    *   Endpoints for full-text search query.
*   [ ] **C5. Tag Normalization:**
    *   `normalized_name` column.
    *   `tag_aliases` table.
*   [ ] **C6. Job Queue:**
    *   `jobs` table for background tasks (Auto-tagging, Thumbnails).
    *   Job runner service in Orchestrator.
*   [ ] **C7. Prompt Library (Generalized Styles):**
    *   `prompt_library` table: `id`, `label`, `content`, `category`, `created_at`.
    *   Categories: "Style", "Character", "Lighting", "Negative", etc.

**Definition of Done:**
*   Database upgrades automatically without data loss.
*   Gallery pagination remains instant (>50fps equivalent) with 50k items.
*   Jobs persist across application restarts.

---

## Release D — Model Presets System (The "Palette")

**Goal:** Formalize model stacks to enable reliable VRAM prediction and user convenience.

*   [ ] **D1. Preset Schema:**
    *   `image_presets` table: `unet`, `vae`, `clip`, `vram_weights_mb_estimate`, `vram_weights_mb_measured`, `default_params`, `preferred_params`.
    *   `llm_presets` table: `model`, `mmproj`, `n_ctx`, `capabilities`, `role` (e.g., "Vision", "Assistant").
*   [ ] **D2. Preset Manager UI:**
    *   Interface to assemble/edit presets.
    *   Auto-calculate VRAM estimates from file sizes (heuristic).
    *   Update `vram_weights_mb_measured` from actual usage reports.
*   [ ] **D3. Runtime Integration:**
    *   Orchestrator loads by Preset ID.
    *   VRAM Arbiter uses preset metadata for predictions.

**Definition of Done:**
*   User can switch between "SDXL High Quality" and "Flux Fast" with one click.
*   VRAM prediction accuracy improves over time (using measured vs. estimated).

---

## Release E — Creative Assistant v1 (Tool Use)

**Goal:** An integrated Chat Agent that can control the application.

*   [ ] **E1. Assistant UI:** Persistent sidebar chat drawer.
*   [ ] **E2. Tool Definition:**
    *   `get_library_items(category)`, `apply_style()`, `enhance_prompt()`, `search_history()` (Depends on C4).
*   [ ] **E3. Safety Rails:**
    *   Orchestrator executes tools (not the LLM worker directly).
    *   Permission checks and loop prevention.
*   [ ] **E4. Integration:**
    *   Connect `prompt_library` (C7) to Assistant context.

**Definition of Done:**
*   Assistant can "Find that blue robot image I made yesterday" (FTS + History).
*   Assistant can "Apply the 'Cinematic' style" (Library Tool).

---

## Release F — Vision & Auto-Tagging v2

**Goal:** Image-grounded intelligence.

*   [ ] **F1. Vision Presets:** Support `mmproj` in LLM presets (Depends on D1).
*   [ ] **F2. Image Handoff:** Mechanism to pass image paths to LLM worker.
*   [ ] **F3. Vision Tagging Job:** Background job to tag images based on visual content (Depends on C6 + D1).
*   [ ] **F4. Feedback Loop:** "Analyze last image and suggest improvements."

---

## Release H — Real-time Control

**Goal:** Responsive user experience and dynamic interaction.

*   [ ] **H1. Request Cancellation:**
    *   Support canceling pending/active generation requests.
    *   (Blocked by upstream `stable-diffusion.cpp` support, implement signaling first).
*   [ ] **H2. Dynamic Updates:**
    *   Allow parameter updates (e.g., guidance scale) during generation if supported.

---

## Release G — Distribution & Packaging

**Goal:** Installer-grade polish.

*   [ ] **G1. Build Automation:** One-command release build.
*   [ ] **G2. Installer:** Inno Setup / NSIS.
*   [ ] **G2.1. First-Run Wizard:**
    *   Set model paths.
    *   Download curated baseline models.
    *   Validate GPU capability + VRAM.
    *   Write initial `config.json`.
*   [ ] **G3. IPC Hardening:** Optional Named Pipes support (if needed).

---

## Cross-Cutting & Maintainability

*   **Safety:** Ensure stable-diffusion "blank output" is treated as an error (Release B0).
*   **Refactoring:** Continue RAII adoption (SD context, Upscaler context).
*   **Dependencies:**
    *   E2 (`search_history`) depends on C4 (FTS5).
    *   F3 (Vision Tagging) depends on C6 (Jobs) + D1 (Vision Presets).

## Priority Order (Next Steps)

1.  **Release B0 (Stop the Bleeding):** Immediate reliability fixes.
2.  **Release C (DB Hardening):** Unlocks safe schema evolution for Presets/Jobs.
3.  **Release B (VRAM v2):** Fixes reliability/OOM issues comprehensively.
4.  **Release D (Presets):** Improves UX and enables accurate VRAM prediction.
