# Testing Guide: Multi-Executable Architecture & Stability

This document outlines the changes made during the Dec 24, 2025 refactor and provides a checklist for verifying that the system remains functional and stable.

## 1. Architectural Changes Overview

We transitioned from a monolithic process to a **Micro-service Architecture**:
- **`mysti_server.exe` (Orchestrator)**: The main entry point. Serves the Vue.js WebUI and proxies API requests.
- **`mysti_sd_worker.exe` (SD Worker)**: Dedicated process for image generation.
- **`mysti_llm_worker.exe` (LLM Worker)**: Dedicated process for text generation and prompt enhancement.

### New Stability Features
- **Process Watchdog**: The Orchestrator monitors worker health via PID status and HTTP `/internal/health` pings.
- **Auto-Restart**: If a worker binary crashes (e.g., CUDA OOM), the Orchestrator respawns it within 2 seconds.
- **State Recovery**: The Orchestrator remembers the last loaded SD and LLM models. It automatically sends a reload command to a fresh worker process after a restart.

---

## 2. Build Verification

Run the build script:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

**Success Criteria:**
- [ ] `build/bin/` contains `mysti_server.exe`, `mysti_sd_worker.exe`, and `mysti_llm_worker.exe`.
- [ ] No linker errors related to `log_printf`, `sd_version`, or `str_to_prediction`.
- [ ] The `public/` directory is copied into `build/bin/`.

---

## 3. Runtime Testing Checklist

### 3.1 Startup & Connectivity
Launch the server using `scripts/run.ps1`.
- [ ] Verify Orchestrator starts on port `1234`.
- [ ] Verify logs show "Spawning SD Worker... on port 1235".
- [ ] Verify logs show "Spawning LLM Worker... on port 1236".
- [ ] Verify WebUI loads at `http://localhost:1234`.

### 3.2 Image Generation (Happy Path)
- [ ] Load a model via the WebUI (Verify Orchestrator logs "Saved SD model state").
- [ ] Generate an image (Verify progress bar works).
- [ ] View generated image in gallery.

### 3.3 Crash Recovery (The Stress Test)
1. Start a long generation or simply **kill the `mysti_sd_worker.exe` process** via Task Manager.
2. **Observe Orchestrator Logs**: Should show `Detected SD Worker failure. Restarting...`.
3. **Verify State Recovery**: After restart, the logs should show `Restoring SD model...` followed by a successful reload.
4. **Verify Frontend**: The WebUI should show an error if a generation was active, but should allow a new generation immediately after the worker is back "online" (approx 5-10s).

### 3.4 LLM & Streaming
- [ ] Use the "Magic Wand" (Enhance Prompt) button.
- [ ] Verify LLM tokens stream into the text area.
- [ ] Kill `mysti_llm_worker.exe` and verify it also auto-restarts and restores the LLM model.

### 3.5 Parallelism
- [ ] Start an image generation (Txt2Img).
- [ ] While it is generating, click "Enhance Prompt".
- [ ] **Expectation**: LLM should respond immediately without waiting for the image to finish.

### 3.6 Model Loading Verification
- [ ] **CLI Startup**: Ensure `scripts/run.ps1` uses `--diffusion-model` for GGUF-based SD models (like Flux, SD3).
- [ ] **Smart Fallback**: If using `--model` with a `.gguf` file, verify logs show "Smart fallback: Moving GGUF...".
- [ ] **API Check**:
  ```powershell
  curl http://localhost:1234/v1/models | Select-String '"active":true'
  ```
  Should return at least one match.

---

## 4. Troubleshooting
- **Port Conflicts**: Ensure `1234`, `1235`, and `1236` are free.
- **Logs**:
    - `build/build_server.log`: Orchestrator issues.
    - `build/build_sd_worker.log`: SD compilation issues.
    - `build/build_llm_worker.log`: LLM compilation issues.
- **VRAM**: If both workers fail to start, check if enough VRAM is available to load both models simultaneously.
