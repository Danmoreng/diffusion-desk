# DiffusionDesk Development Plan

**Last Updated:** June 27, 2026
**Status:** Canonical roadmap for active work

This document is the single source of truth for roadmap status. The remaining
files in `docs/` are supporting references for legacy WebUI context, LLM worker
behavior, runtime memory notes, and repeatable benchmarks.

## Current Product Shape

DiffusionDesk is now primarily a Kotlin Compose Desktop application. The legacy
Vue/Vite `webui/` remains buildable and audit-clean, but new product UI should
go into `composeApp/`.

The current Compose app includes:

- Generate workspace with local SD worker control, structured Ideogram
  composition editing, image preview, compact progress, cancellation, and
  Endless Generation.
- Analyze workspace for turning uploaded or gallery images into editable
  Ideogram 4 compositions, then applying them back to Generate in Merge or
  Replace mode.
- Gallery workspace with search/filtering, tag/rating workflows, parameter
  reuse, upscale handoff, and Analyze handoff.
- Upscale workspace for ESRGAN-based image upscaling.
- Presets workspace for image and LLM presets.
- System workspace for image and LLM worker diagnostics, explicit unload, and
  stop controls.
- Assistant panel with controlled tool calls, including prompt edits,
  generation-setting edits, latest-image inspection, and Ideogram composition
  mutations.

Native inference remains isolated in subprocesses:

- `diffusion_desk_sd_worker`
- one or more `diffusion_desk_llm_worker` instances managed by Compose

## Completed Foundations

### Platform, Workers, and Observability

- [x] Local worker subprocess architecture for SD and LLM inference.
- [x] Worker health, shutdown, unload, and diagnostics endpoints used by
  Compose.
- [x] Compose-side worker lifecycle supervision.
- [x] Explicit user controls for starting/stopping the image worker and LLM
  workers.
- [x] Internal token handling for worker requests.
- [x] Structured user-visible notifications for common worker actions.

### Compose Desktop Migration

- [x] Compose desktop module and root Gradle tasks.
- [x] Desktop app shell, navigation, settings persistence, and setup wizard.
- [x] Backend worker bootstrap from Compose.
- [x] Core generation workflow.
- [x] Inpainting-style composition canvas primitives and bbox editing for
  structured Ideogram prompts.
- [x] Upscale workflow.
- [x] Gallery workflow.
- [x] Preset/library management.
- [x] Assistant panel and local LLM integration.
- [x] Windows and Linux packaging scripts for portable app images.

Legacy-only note: the old Vue Dynamic Exploration grid has not been ported to
Compose and is not part of the current product roadmap. Endless Generation
exists in Compose as a separate workflow.

### Database and Gallery

- [x] SQLite-backed gallery database.
- [x] Schema migration support with `PRAGMA user_version`.
- [x] Gallery search, filtering, tags, ratings, and reusable generation
  parameters.
- [x] Pending LLM tag state and gallery tagging service.
- [x] Metadata parsing for generated image reuse.

### Presets and LLM Roles

- [x] JSON-backed image presets.
- [x] JSON-backed LLM presets.
- [x] Role bindings for tagging, prompt enhancement, and assistant workflows.
- [x] Multimodal `mmproj` support for vision-capable LLM presets.
- [x] Advanced llama.cpp arguments with parser validation and reserved
  app-managed argument rejection.
- [x] Multiple distinct LLM workers can be loaded concurrently through
  `LlmWorkerPool`.
- [x] Matching roles can reuse a compatible worker.
- [x] CPU/GPU placement is represented at preset level.

### Ideogram 4 Composition Workflow

- [x] Structured Ideogram document model and canonical JSON mutation path.
- [x] Right-side composition/image canvas with overlay behavior.
- [x] Manual composition editing for fields, elements, palettes, and bboxes.
- [x] Composition undo/redo history for manual and LLM-driven mutations.
- [x] Field-, style-, composition-, element-, and palette-level LLM actions.
- [x] Staged composition generation with accepted intermediate drafts.
- [x] Analyze workspace for image-to-composition capture.
- [x] Gallery-to-Analyze handoff.
- [x] Analyze-to-Generate Merge and Replace application.
- [x] Assistant composition tools filtered by active prompt mode and context.
- [x] Assistant cannot start image generation through a tool.

### Reliability

- [x] Blank output and generation failure are surfaced as hard errors.
- [x] Conservative retry paths exist for selected failure/OOM scenarios.
- [x] SD and LLM idle unload behavior exists.
- [x] Generation jobs support cancellation through Compose and the SD worker.
- [x] User can explicitly unload image and LLM models.

### Packaging

- [x] Native worker build script.
- [x] Compose distributable build tasks.
- [x] Windows portable packaging script.
- [x] Optional Windows MSI packaging through `jpackage`/WiX.
- [x] Linux portable packaging script.
- [x] Optional Linux DEB packaging through `jpackage`.

## Active Roadmap

### P1 - First-Run Onboarding

Goal: make the first installation successful without requiring users to already
understand local model layout, Hugging Face downloads, or preset setup.

- [ ] Improve the first-run setup flow with clearer folder, worker, and runtime
  checks.
- [ ] Offer one or two recommended starter model examples from Hugging Face.
- [ ] Document where to download those models, which files are required, and
  where users should place them locally.
- [ ] Provide preset templates or guided preset creation for the recommended
  starter models.
- [ ] Add validation that explains missing model files, unsupported file
  combinations, and common CUDA/runtime mismatches in user-facing language.

### P2 - Roadmap and Test Hygiene

Goal: keep the now-large app maintainable and measurable.

- [ ] Keep this roadmap in sync when feature plans are completed.
- [ ] Add or refresh focused tests for worker pooling, advanced-args parsing,
  role routing, generation cancellation, Analyze capture, and composition
  mutations.
- [ ] Update user-facing docs whenever setup, packaging, or preset formats
  change.

### P3 - UI Cleanup

Goal: reduce unnecessary complexity in the Compose UI and make common workflows
clearer.

- [ ] Audit Generate, Analyze, Gallery, Presets, and System for controls or
  states that are redundant, unclear, or too deeply nested.
- [ ] Simplify first-use paths without removing advanced controls.
- [ ] Improve empty states, error states, and recovery actions.
- [ ] Review assistant and composition-tool affordances for discoverability.
- [ ] Keep cleanup targeted; avoid broad visual redesigns without a concrete
  workflow problem.

### P4 - Performance

Goal: make everyday app use feel faster and reduce avoidable waiting.

- [ ] Profile Compose startup, first worker launch, gallery loading, and
  composition editor interactions.
- [ ] Tighten gallery/database queries and thumbnail loading where needed.
- [ ] Reduce avoidable LLM worker reloads through clearer reuse and idle
  behavior.
- [ ] Review image generation progress polling and UI recomposition hotspots.
- [ ] Use `docs/RUNTIME_MEMORY_NOTES.md` when changing unload/offload behavior
  or low-VRAM preset guidance.
- [ ] Keep the existing Ideogram backend performance benchmark current when
  runtime behavior changes.

## Backlog

### Feedback Loop

Goal: close the loop from generated result back into improved prompts.

- [ ] Add a first-class "Analyze last image and suggest improvements" workflow.
- [ ] Let the user apply suggestions as targeted normal-prompt edits, structured
  composition mutations, or Analyze-to-Generate Merge changes.
- [ ] Preserve all applied changes in the same undo/redo history used by
  composition editing.

This is a useful future workflow, but it is not an active priority.

### Packaging and Installer Polish

Goal: make the app installer-grade for non-developer users.

- [x] Portable Windows package.
- [x] Optional Windows MSI package.
- [x] Portable Linux package.
- [x] Optional Linux DEB package.
- [x] First-run setup wizard for folders and initial presets.
- [ ] Validate packaged app startup on clean Windows and Linux machines.
- [ ] Fail packaging if required worker binaries, runtime DLLs, or default
  assets are missing.
- [ ] Decide whether named pipes or another IPC hardening layer is needed.

### Runtime Polish

Goal: make long sessions less surprising.

- [ ] Optional model pre-warm when resources allow and the user enables it.
- [ ] Better worker log surfaces in the System workspace.
- [ ] Clearer recovery guidance after worker crashes or failed model loads.
- [ ] More explicit memory status for loaded image and LLM models.
- [ ] Revisit dynamic parameter updates during generation if upstream support
  makes it useful.

## Supporting Docs

- `compose-llm-worker-support-spec.md`: LLM worker pool design and remaining
  validation context.
- `RUNTIME_MEMORY_NOTES.md`: Compose-first notes for unload/offload behavior,
  diagnostics, and low-VRAM preset guidance.
- `ideogram4-performance-benchmark.md`: repeatable Ideogram backend benchmark
  instructions.
- `LEGACY_WEBUI_README.md`: reference for the deprecated Vue WebUI while
  `webui/` remains in the repository.

## Suggested Next Work

1. Improve first-run onboarding with starter Hugging Face model examples and
   guided preset creation.
2. Add focused tests around the Compose LLM worker pool and composition
   mutations.
3. Audit the Compose UI for unnecessary complexity and simplify the highest
   friction workflows.
4. Profile startup, gallery, worker reuse, and composition editing performance.
5. Validate portable/MSI packages on clean machines and tighten packaging
   failure checks.
