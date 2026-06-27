# Compose LLM Worker Support Spec

> **Status as of June 27, 2026:** Most of this spec is implemented in the
> Compose app. `LlmWorkerPool`, role bindings, CPU/GPU placement, advanced
> argument parsing, worker diagnostics, explicit unload/stop controls, and
> gallery tagging support exist. Keep this file as design rationale and
> validation context; track active work in `docs/DEVELOPMENT_PLAN.md`.

## Purpose

Re-introduce llama.cpp support in the current Kotlin Compose desktop app without
reviving the legacy C++ orchestrator as the primary desktop control plane.

The Compose app should own user-facing orchestration: settings, presets, role
selection, database updates, gallery integration, worker lifecycle, tagging
queues, and explicit user control over model placement. Native inference should
remain isolated in subprocesses.

## Goals

- Add LLM support to the Compose app using native `diffusion_desk_llm_worker`
  subprocesses.
- Allow multiple LLM models to be loaded at the same time.
- Let users assign different LLM presets to different roles, especially:
  - Image tagging
  - Prompt enhancement
  - Chat or assistant workflows
- Keep normal LLM preset settings simple.
- Give advanced users a raw llama.cpp command-line argument escape hatch.
- Allow both LLM and image models to be explicitly unloaded.
- Replace useful legacy orchestrator responsibilities inside Compose where
  needed, without adding a third app backend process.

## Non-Goals

- Do not restore `diffusion_desk_server` as the normal Compose app backend.
- Do not embed llama.cpp or stable-diffusion.cpp directly inside the JVM process.
- Do not re-create complex automatic VRAM arbitration.
- Do not use llama.cpp router mode as the primary multi-model implementation.
- Do not modify vendored llama.cpp code unless there is a narrowly justified
  compatibility need.

## Architecture

The Compose app becomes the desktop orchestration layer:

```text
Compose desktop app
  - UI
  - settings
  - presets
  - gallery database
  - tagging queue/service
  - worker process supervision
  - role-to-preset routing

Native subprocesses
  - diffusion_desk_sd_worker
  - diffusion_desk_llm_worker for tagging preset
  - diffusion_desk_llm_worker for assistant/prompt preset
  - additional diffusion_desk_llm_worker instances as needed
```

The workers remain separate processes for crash isolation, clean memory
lifetime, and native runtime isolation. If an LLM model crashes, it should not
take down the Compose app or another loaded LLM model.

## Why Not llama.cpp Router Mode

The vendored llama.cpp server includes router mode, but it is not a single
process that simply hosts multiple models internally. The router starts a
router process, then spawns child server instances for loaded models and proxies
requests to them.

That is functionally close to spawning multiple workers, but with an additional
hidden routing layer and less direct control from the Compose app. llama.cpp
also labels router mode experimental. For this app, explicit Compose-managed
workers are easier to inspect, debug, configure, and expose to users.

Decision: use a Compose-managed multi-worker LLM pool.

## Worker Model

### Image Worker

The SD worker remains a single app-managed native subprocess:

- Executable: `diffusion_desk_sd_worker`
- Base URL: current image worker URL, normally `http://127.0.0.1:{listenPort}`
- Health: `GET /internal/health`
- Shutdown: `POST /internal/shutdown`
- Load image preset: `POST /v1/models/load`
- Unload image model: `POST /v1/models/unload`
- Offload image model: `POST /v1/models/offload`

The current Compose image workflow should not silently move to legacy
orchestrator port offsets.

### LLM Workers

Each concurrently loaded LLM preset is backed by one `diffusion_desk_llm_worker`
process.

Examples:

```text
tagging role
  preset: Small Tagger CPU
  worker: http://127.0.0.1:1241

assistant role
  preset: Larger Assistant GPU
  worker: http://127.0.0.1:1242

prompt enhancement role
  preset: same as assistant
  worker: reuse assistant worker
```

If multiple roles use the same active preset and compatible runtime arguments,
they should reuse the same worker. If the role assignments point to different
presets, the app can keep both loaded simultaneously.

LLM worker endpoints:

- `GET /internal/health`
- `POST /internal/shutdown`
- `POST /v1/llm/load`
- `POST /v1/llm/unload`
- `POST /v1/llm/offload`
- `POST /v1/chat/completions`
- `POST /v1/completions`
- `POST /v1/embeddings`
- `POST /v1/tokenize`
- `POST /v1/detokenize`
- `GET /v1/llm/models`

## LLM Presets

LLM presets should remain intentionally simple for normal users.

Recommended fields:

```kotlin
data class LlmPreset(
    val id: String,
    val name: String,
    val modelPath: String,
    val placement: LlmPlacement,
    val advancedArgs: String,
)

enum class LlmPlacement {
    Cpu,
    Gpu,
}
```

Optional future fields may include:

- `mmprojPath`, for multimodal models that need an external projector.
- `notes`, for user-visible preset notes.
- `capabilities`, if the app needs to distinguish chat, tagging, vision,
  embeddings, or tool-capable models.

Avoid turning presets into a full llama.cpp settings editor. The normal UI
should expose only CPU/GPU placement and a single advanced argument field.

### Placement Behavior

Placement is a high-level user choice:

- `CPU`: force CPU inference.
- `GPU`: allow GPU inference using the app's default GPU behavior, unless
  advanced arguments refine it.

The current LLM worker defaults to GPU when `n_gpu_layers` is omitted, because
`-1` becomes a high GPU layer count internally. For CPU presets, the app or
worker must explicitly force zero GPU layers.

Expected CPU load payload:

```json
{
  "model_id": "llm/small-tagger.gguf",
  "n_gpu_layers": 0
}
```

The exact mapping can be refined during implementation, but CPU placement must
be predictable and must not silently use VRAM.

## Advanced llama.cpp Arguments

Each LLM preset has an optional advanced text field where users can enter raw
llama.cpp-compatible arguments.

Examples:

```text
--threads 8 --ctx-size 4096 --batch-size 256
```

Rules:

- Do not pass this field through a shell.
- Parse it into argv tokens and pass it directly to `ProcessBuilder`.
- Preserve quoted values correctly.
- Validate or reject malformed quoting.
- Show the parsed arguments in diagnostics.
- Store the raw text exactly as the user entered it.

### Reserved Arguments

The app must own process/network identity and lifecycle arguments. Advanced
arguments must not override these:

- `--listen-ip`
- `--listen-port`
- `--host`
- `--port`
- `--model-dir`
- `--internal-token`
- `--mode`
- Any worker identity or app-managed log routing argument

The app should either reject presets containing reserved arguments or ignore the
reserved tokens with a clear validation message. Rejection is preferable because
it avoids surprising behavior.

### Placement vs Advanced Arguments

The UI-level CPU/GPU placement should remain understandable. Two reasonable
policies are possible:

1. Placement wins:
   - CPU always forces `n_gpu_layers = 0`.
   - GPU uses app defaults unless advanced args include non-conflicting tuning.

2. Advanced wins for GPU only:
   - CPU still forces `n_gpu_layers = 0`.
   - GPU allows advanced `--gpu-layers` or equivalent args.

Recommended initial policy: CPU always wins; GPU may be refined by advanced
args.

## Role Assignments

Do not bake role ownership into the preset itself. Store role bindings
separately so a preset can be reused by multiple roles.

Recommended settings:

```kotlin
data class LlmRoleSettings(
    val taggingPresetId: String?,
    val assistantPresetId: String?,
    val promptEnhancerPresetId: String?,
)
```

Roles:

- `Tagging`: background or manual image tagging.
- `Assistant`: chat-style interactive helper.
- `PromptEnhancer`: prompt rewriting, expansion, style extraction, or similar.

If `assistantPresetId` and `promptEnhancerPresetId` point to the same preset,
they should normally share a worker.

## LLM Worker Pool

Implement a Compose-side worker pool responsible for starting, stopping,
reusing, and monitoring LLM workers.

Conceptual API:

```kotlin
class LlmWorkerPool {
    suspend fun ensureWorkerForPreset(preset: LlmPreset): LlmWorkerHandle
    suspend fun unloadPreset(presetId: String)
    suspend fun stopWorker(workerId: String)
    suspend fun stopAll()
    fun state: StateFlow<List<LlmWorkerState>>
}
```

Worker identity should be derived from the runtime configuration, not only the
preset id. A preset edit that changes model path, placement, or advanced args
should result in a different runtime signature.

Runtime signature inputs:

- Model path
- Optional mmproj path
- Placement
- Parsed advanced arguments
- Worker binary version if needed

## Port Allocation

The existing SD worker should keep using the current configured image port.

LLM workers should use explicit app-managed ports:

- Start with a configured LLM base port, e.g. `listenPort + 1`.
- Allocate sequentially for additional loaded LLM workers.
- Detect already-used ports and skip them.
- Persist only the base port, not every transient assigned worker port.

Alternative: ask the OS for free ports and store active assignments in memory.
This is simpler for conflict avoidance but less transparent in diagnostics.

Recommended initial approach: configured base port plus conflict probing.

## Lifecycle Controls

The UI should expose explicit commands:

- Start/load tagging LLM
- Start/load assistant LLM
- Start/load prompt enhancer LLM
- Unload a specific LLM model
- Stop a specific LLM worker
- Stop all LLM workers
- Unload image model
- Stop image worker

Distinguish these states:

- Worker process stopped
- Worker process starting
- Worker process ready but no model loaded
- Model loading
- Model loaded
- Busy
- Error

For LLMs, "unload model" and "stop worker" are different:

- Unload model: call `/v1/llm/unload`; process can remain alive.
- Stop worker: call `/internal/shutdown`, then terminate if needed.

For images:

- Unload model: call `/v1/models/unload`.
- Stop worker: existing image worker shutdown behavior.

## Image Tagging Service

The tagging service should live in Compose, not in the C++ orchestrator.

Responsibilities:

- Discover untagged or pending images from the Compose-managed gallery DB.
- Maintain a tagging queue and persisted status.
- Load or reuse the LLM preset assigned to the tagging role.
- Run tagging prompts against the selected worker.
- Store tags/captions/metadata back into the existing DB.
- Respect user scheduling preferences.

Recommended tagging modes:

- Manual only
- Auto-tag while app is idle
- Auto-tag after generation
- Pause tagging during image generation

Recommended queue item states:

- Pending
- Running
- Completed
- Failed
- Skipped

Tagging should not silently replace the model used by assistant or prompt
enhancement. If tagging and assistant use different presets, they should use
different workers when both are loaded.

## Prompt Enhancement and Chat

Prompt enhancement and chat should resolve their assigned role preset, then ask
the LLM worker pool for a compatible worker.

Request routing:

```text
Prompt enhancement request
  -> role: PromptEnhancer
  -> preset id from role settings
  -> worker pool returns matching worker
  -> POST /v1/chat/completions

Chat request
  -> role: Assistant
  -> preset id from role settings
  -> worker pool returns matching worker
  -> POST /v1/chat/completions
```

Prompt enhancement can share the assistant preset by default.

## Image Preset Unload Policy

Image presets should stay focused on model components and generation defaults.
Unload behavior should be a runtime preference, not part of the preset identity
unless a future use case clearly requires preset-specific policy.

Suggested runtime settings:

- Keep image model loaded after generation
- Unload image model after generation
- Unload image model after idle timeout

The UI should also provide a direct "Unload Image Model" action.

## Memory and Resource Policy

Do not re-create the old C++ VRAM arbitration logic. It was complex and not
reliable enough to justify carrying forward.

The app should favor explicit user control:

- LLM presets choose CPU or GPU.
- Small tagging LLMs can run on CPU/RAM.
- Larger assistant models can run on CPU or GPU by user choice.
- Image generation uses the image worker and image preset settings.
- The app may show memory/worker status, but should not silently unload or move
  models unless the user enabled a clear policy.

Helpful status display:

- SD worker loaded model
- SD worker VRAM allocated
- Each LLM worker loaded preset/model
- Each LLM worker placement
- Each LLM worker busy/idle state
- Last log line or error

## Required Backend Changes

The current `diffusion_desk_llm_worker` accepts only a limited set of llama.cpp
runtime settings through its load path. The advanced argument field requires a
clean pass-through design.

Implementation requirements:

- Add support for app-provided llama.cpp/common params beyond the current small
  set.
- Preserve app-owned network/process args.
- Ensure CPU placement can force zero GPU layers.
- Ensure the worker can be started without immediately loading a model.
- Ensure `/v1/llm/load` can load the requested model into the worker.
- Include enough health metadata for Compose diagnostics:
  - service type
  - loaded state
  - model path
  - placement or effective GPU layers when known
  - VRAM allocation/free values if available

Avoid changing vendored llama.cpp unless the wrapper cannot support needed
arguments any other way.

## Compose Implementation Areas

Likely new or changed modules:

- `NativeWorkerManager` or equivalent reusable process supervisor.
- `ImageWorkerManager`, possibly refactored from current `BackendManager`.
- `LlmWorkerPool`.
- `LlmWorkerClient`.
- `LlmPresetRepository`.
- `LlmRoleSettingsRepository`.
- `ImageTaggingService`.
- Settings and Library UI for LLM presets and role bindings.
- Diagnostics UI for active workers.

## Packaging Changes

The portable app must include:

- `diffusion_desk_sd_worker.exe`
- `diffusion_desk_llm_worker.exe`
- Required DLLs for both workers

Packaging validation should fail if either required worker executable is
missing.

The README/runtime layout should be updated once implementation is complete.

## Security and Robustness

- Bind workers to `127.0.0.1`.
- Continue using internal tokens for worker endpoints if supported.
- Never shell-execute user-provided advanced arguments.
- Avoid exposing worker ports beyond localhost.
- Ensure shutdown tries graceful API shutdown first, then process termination.
- Keep logs per worker instance or include worker identity in log diagnostics.

## Current Implementation Status

Implemented in the Compose app:

- Native SD and LLM worker subprocess supervision.
- Multi-worker LLM pool with runtime signatures and worker reuse.
- LLM preset placement, role binding, and advanced argument validation.
- Explicit model unload and worker stop controls for image and LLM workers.
- Worker diagnostics in the System area.
- Gallery metadata/tagging integration and role-routed LLM calls.
- Portable packaging inputs for native workers.

Remaining work belongs in roadmap polish rather than core architecture:

- Broaden automated tests for argument parsing, placement behavior, worker pool
  reuse, and role routing.
- Keep packaging validation strict for both Windows and Linux artifacts.
- Improve diagnostics copy and failure recovery around crashed or missing
  workers.

## Implementation Phases

These phases are retained as historical implementation notes. Use the status
section above and the canonical roadmap for current planning.

### Phase 1: Foundation

- Refactor existing image `BackendManager` into reusable worker process logic.
- Add `diffusion_desk_llm_worker` discovery and packaging checks.
- Add `LlmWorkerClient` with health, load, unload, shutdown, and chat calls.
- Add simple one-worker LLM start/load/unload from Compose diagnostics.

### Phase 2: Presets and Roles

- Add simple LLM preset storage.
- Add role binding settings for tagging, assistant, and prompt enhancement.
- Add CPU/GPU placement handling.
- Add advanced args parsing and reserved-argument validation.

### Phase 3: Multi-Worker Pool

- Add LLM worker pool with runtime signatures.
- Reuse workers for matching role/preset signatures.
- Allow multiple distinct LLM presets to remain loaded at once.
- Add UI for active LLM workers and explicit unload/stop commands.

### Phase 4: Tagging Service

- Add tagging queue/status persistence.
- Add manual tagging action.
- Add optional auto-tag policies.
- Store generated tags/captions in the existing gallery DB.

### Phase 5: Polish and Validation

- Add worker diagnostics and log surfaces.
- Add packaging validation.
- Add focused tests for argument parsing, reserved flag validation, role routing,
  and worker pool reuse.
- Update README and user-facing docs.

## Open Decisions

- Whether `mmprojPath` belongs in the normal preset form or an advanced section.
- Whether GPU placement should allow advanced `--gpu-layers` to override the
  app default.
- Whether LLM worker ports should be stable sequential ports or OS-assigned
  transient ports.
- Whether idle unload should be global, per role, or per preset.
- Whether prompt enhancement and chat should remain separate roles or share one
  assistant role by default.
