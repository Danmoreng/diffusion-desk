# MystiCanvas Development Plan (Consolidated)

**Last Updated:** December 30, 2025
**Status:** Active Development

This document outlines the roadmap for MystiCanvas, merging original milestones with architectural review recommendations and the "Model Presets + Creative Assistant" vision.

---

## 0. Core Principles

1.  **Stability through Isolation:** Orchestrator + specialized workers remains the default architecture.
2.  **Predictability before Intelligence:** Solidify the substrate (DB, VRAM prediction) before adding "smart" agentic features.
3.  **HTTP for IPC:** Keep HTTP for internal communication for debuggability; optimize only if proven bottleneck.
4.  **Database as Memory:** All state (presets, styles, search, jobs) must be durable.

---

## Release A — Platform Contracts & Observability (Foundation)

**Goal:** Make every core subsystem *inspectable*, *restartable*, and *consistent*.

*   [x] **A1. Standardize Contracts:** `internal/health` endpoints, consistent error shapes, internal token auth. (Completed in Refactor)
*   [ ] **A2. Worker Lifecycle Resilience:**
    *   Orchestrator monitors worker health.
    *   Automatic restart on crash.
    *   Broadcast "system alert" to UI via WebSockets.
*   [ ] **A3. Structured Logs & Correlation:**
    *   Add request IDs to logs.
    *   Structured JSON logging for easy parsing.

---

## Release B — VRAM Management v2 (Predictive + Surgical)

**Goal:** Prevent OOM *without* brute-force unloading; explain behavior to the user.

*   [ ] **B1. Health-Driven VRAM Registry:**
    *   Workers report detailed memory usage.
    *   Orchestrator tracks model footprints.
*   [ ] **B2. Predictive Arbitration (Orchestrator):**
    *   Compute expected VRAM (Weights + Compute + Safety).
    *   Logic: Proceed vs. Soft Unload (KV) vs. Hard Unload.
*   [ ] **B3. Surgical Worker-Level Mitigations:**
    *   Dynamic VAE tiling based on resolution.
    *   Auto VAE-on-CPU fallback if VRAM is tight.
    *   OOM retry path (re-init with conservative settings).
*   [ ] **B4. UI Feedback:**
    *   Indicate "Projected vs Actual" VRAM.
    *   Notifications for "VAE moved to CPU" or "LLM Unloaded".

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

---

## Release D — Model Presets System (The "Palette")

**Goal:** Formalize model stacks to enable reliable VRAM prediction and user convenience.

*   [ ] **D1. Preset Schema:**
    *   `image_presets` table: `unet`, `vae`, `clip`, `vram_weights_mb`, `default_params`.
    *   `llm_presets` table: `model`, `mmproj`, `n_ctx`, `capabilities`.
*   [ ] **D2. Preset Manager UI:**
    *   Interface to assemble/edit presets.
    *   Auto-calculate VRAM estimates from file sizes.
*   [ ] **D3. Runtime Integration:**
    *   Orchestrator loads by Preset ID.
    *   VRAM Arbiter uses preset metadata for predictions.

---

## Release E — Creative Assistant v1 (Tool Use)

**Goal:** An integrated Chat Agent that can control the application.

*   [ ] **E1. Assistant UI:** Persistent sidebar chat drawer.
*   [ ] **E2. Tool Definition:**
    *   `get_styles()`, `apply_style()`, `enhance_prompt()`, `search_history()`.
*   [ ] **E3. Safety Rails:**
    *   Orchestrator executes tools (not the LLM worker directly).
    *   Permission checks and loop prevention.
*   [ ] **E4. Style Library:**
    *   `styles` table (Completed).
    *   UI to save/apply styles.

---

## Release F — Vision & Auto-Tagging v2

**Goal:** Image-grounded intelligence.

*   [ ] **F1. Vision Presets:** Support `mmproj` in LLM presets.
*   [ ] **F2. Image Handoff:** Mechanism to pass image paths to LLM worker.
*   [ ] **F3. Vision Tagging Job:** Background job to tag images based on visual content.
*   [ ] **F4. Feedback Loop:** "Analyze last image and suggest improvements."

---

## Release G — Distribution & Packaging

**Goal:** Installer-grade polish.

*   [ ] **G1. Build Automation:** One-command release build.
*   [ ] **G2. Installer:** Inno Setup / NSIS.
*   [ ] **G3. IPC Hardening:** Optional Named Pipes support (if needed).

---

## Priority Order (Next Steps)

1.  **Release C (DB Hardening):** Unlocks safe schema evolution for Presets/Jobs.
2.  **Release B (VRAM v2):** Fixes reliability/OOM issues.
3.  **Release D (Presets):** Improves UX and enables accurate VRAM prediction.