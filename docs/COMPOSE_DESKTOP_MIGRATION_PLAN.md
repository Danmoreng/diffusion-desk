# Compose Desktop Migration Plan

## Summary

This document describes a practical migration path from the current Vue/Vite web UI to a native desktop UI built with Compose Multiplatform Desktop.

Recommendation:

- Keep the existing C++ backend and process model unchanged.
- Build a new native desktop client against the current local HTTP and WebSocket API.
- Use Compose for all standard UI surfaces.
- Use custom Compose `Canvas` code only where the current UI is already custom, primarily the inpainting editor.
- Use Swing/AWT interop only for narrow desktop gaps if needed.

Do not start with a raw Skia UI from scratch. That would turn a UI migration into a UI toolkit project.

## Why This Path

The current application is already split cleanly:

- `diffusion_desk_server` acts as the orchestrator and local app server.
- The frontend talks to `/v1/*` routes plus a WebSocket stream for metrics and progress.
- The browser-specific code is concentrated in a handful of files rather than spread through the whole stack.

That means the desktop app can reuse:

- the current orchestrator,
- the worker lifecycle,
- the database,
- the API surface,
- the image/output conventions,
- the generation queue semantics.

It only needs to replace the frontend runtime and UI implementation.

## Current Web UI Feature Map

Primary migration hotspots in the current repo:

- `webui/src/stores/generation.ts`
  - Main app state
  - persistence
  - WebSocket progress/metrics
  - generation requests
  - model loading
  - presets/styles flows
- `webui/src/views/ManagerView.vue`
  - largest form-heavy management screen
  - presets, styles, tags, model metadata, LoRA previews
- `webui/src/components/ImageGallery.vue`
  - infinite scroll
  - modal viewer
  - multi-select delete
  - rating/tags
  - clipboard and drag/drop
- `webui/src/components/InpaintingCanvas.vue`
  - custom drawing and mask processing
- `webui/src/components/AssistantPanel.vue`
  - chat shell
  - image attachment
  - markdown rendering
  - drag/drop
- `webui/src/services/mutation-builder.ts`
  - exploration view logic

Feature groups to preserve:

- App shell: sidebar, navigation, floating action bar, settings, setup wizard
- Generation workflows: text-to-image, image-to-image, inpainting, upscale
- Progress and metrics: WebSocket-driven progress and VRAM display
- Asset workflows: gallery, ratings, tags, search/filter, reuse parameters
- Library manager: image presets, LLM presets, styles, model metadata, LoRA previews
- Assistant: multimodal chat, tool calls, image attach/drag/drop
- Exploration: mutation grid and anchor promotion flow

## Target Desktop Architecture

### Process Model

Keep the current backend as a separate local process.

Desktop app responsibilities:

- launch and monitor `diffusion_desk_server`,
- detect readiness,
- connect to the existing HTTP and WebSocket endpoints,
- present desktop UI,
- package the backend binaries with the desktop app.

Do not move inference into the UI process in phase 1.

### Suggested Module Layout

Suggested new structure, based on the `qwen-tts-studio` Compose blueprint:

- root `settings.gradle.kts`
  - include a single desktop app module such as `:composeApp`
- root `build.gradle.kts`
  - define wrapper tasks for native build and packaging scripts
- `composeApp/`
  - Compose Desktop module
- `composeApp/src/commonMain/`
  - theme
  - shared UI primitives
  - common models where useful
- `composeApp/src/desktopMain/`
  - desktop entry point
  - window lifecycle
  - app navigation
  - backend process bootstrap
- `composeApp/src/desktopMain/kotlin/.../core/model`
  - DTOs and domain models
- `composeApp/src/desktopMain/kotlin/.../core/api`
  - HTTP client
  - WebSocket client
  - route wrappers for `/v1/*`
- `composeApp/src/desktopMain/kotlin/.../core/process`
  - orchestrator process launch
  - readiness checks
  - shutdown and restart handling
- `composeApp/src/desktopMain/kotlin/.../core/state`
  - app stores or view-models
  - settings persistence
- `composeApp/src/desktopMain/kotlin/.../screens`
  - generation
  - gallery
  - manager
  - assistant
  - inpainting
  - exploration
  - settings
- `scripts/`
  - packaging and build wrappers for Windows and Linux

### State Model

Translate the Pinia stores into Kotlin state holders.

Preferred approach:

- `StateFlow` or Compose state for long-lived screen state
- one state holder per feature
- one shared app-level state holder for:
  - current image preset
  - current LLM preset
  - theme
  - global metrics
  - generation queue and history

Avoid a single monolithic port of `generation.ts`.
Split it during migration.

### Persistence

Replace browser persistence with desktop persistence:

- `localStorage` -> app settings file or preferences storage
- theme and layout settings -> desktop preferences
- prompt history -> local persisted state if still desired

The `qwen-tts-studio` reference uses a simple user-home properties file and `MutableStateFlow` backed view-models. That is a good starting point for DiffusionDesk too, especially for:

- theme and layout settings
- sidebar and assistant placement
- gallery density preferences
- last used model and output directories
- last selected image and LLM preset

## UI Mapping

### Easy to Medium Ports

These are straightforward Compose screens:

- sidebar and navigation
- settings
- setup wizard
- generation forms
- model/preset selection
- progress panel
- standard dialogs and confirmation flows

### Medium Ports

These need careful state and list handling:

- gallery
- manager
- assistant
- exploration grid

### Hardest Port

The inpainting canvas is the most custom frontend surface.

Compose plan:

- keep the same mask data model,
- draw source image plus mask overlay in a custom `Canvas`,
- implement pointer handling for brush strokes,
- keep offscreen image buffers for the mask,
- perform blur/invert/export in Kotlin on the mask image.

This is still much easier than a raw Skia app because the windowing, layout, input dispatch, and standard controls stay inside Compose.

## Blueprint Findings From `qwen-tts-studio`

The local reference repo provides several patterns worth reusing directly.

### Gradle Shape

Reusable pattern:

- root project with one `:composeApp` module
- root-level helper tasks for native build and packaging
- Compose Desktop plugin configured only in the app module

This is a good fit for DiffusionDesk.

### Desktop Entry Point

Reusable pattern:

- a thin `main.kt`
- one `Window`
- one root `App()` composable
- small amount of platform-specific window polish

DiffusionDesk should follow the same shape:

- `main.kt` launches the window
- root app creates feature view-models
- root app owns top-level navigation and theme

### Screen and ViewModel Split

Reusable pattern:

- screens are UI-only
- view-models hold `StateFlow` state and business logic
- root app wires view-models into screens

This should replace the current Vue pattern of very large mixed component/store files.

### Desktop File Picking

Reusable pattern:

- `filekit` is used for directory picking in setup

This is directly relevant for DiffusionDesk for:

- model directory selection
- output directory selection
- init image selection
- reference image selection

### Packaging Scripts

Reusable pattern:

- root Gradle tasks call PowerShell scripts
- native build happens first
- Compose distributable is created second
- native artifacts are copied into the packaged app image

DiffusionDesk should adopt the same packaging strategy.

The difference is artifact shape:

- `qwen-tts-studio` packages JNI/native DLLs
- DiffusionDesk must package executables and static assets:
  - `diffusion_desk_server`
  - `diffusion_desk_sd_worker`
  - `diffusion_desk_llm_worker`
  - `public/`
  - default config assets

### Native Bootstrap Boundary

The reference repo has `QwenEngine.kt`, which acts as a native/bootstrap boundary.

Do not copy its JNI loading strategy directly.

For DiffusionDesk, the equivalent boundary should be a `BackendManager` layer that:

- locates packaged executables
- launches the orchestrator
- waits for readiness
- monitors process exit
- shuts down child processes on app exit
- exposes backend endpoint information to the UI layer

### What Not To Reuse

Do not copy these parts as-is:

- JNI and JNA loading flow
- CLI fallback logic inside the engine wrapper
- audio-specific playback patterns
- checked-in native build outputs under `external/build-*`

For DiffusionDesk, reuse the reference repo's structure, not its in-process runtime coupling.

## Packaging Recommendations Based On The Blueprint

DiffusionDesk packaging should look like this:

1. Build backend binaries with the existing native build pipeline.
2. Build `:composeApp:createDistributable`.
3. Copy backend executables and required runtime assets into the packaged app image.
4. Ensure the desktop app launches the orchestrator from the packaged location, not from the repo root.
5. Store user config and mutable state under the user profile, not inside the packaged install directory.

Recommended package contents:

- desktop launcher
- orchestrator executable
- worker executables
- `public/` assets if still needed by the orchestrator internally
- default config template
- app icon resources

## Concrete Changes To Make Up Front

Before starting feature porting, implement these repository-level changes:

1. Add a new `:composeApp` module rather than a loose `desktop/` folder.
2. Add root Gradle tasks similar to the reference repo for:
   - native backend build
   - Windows package
   - Linux package
3. Add a desktop `main.kt` plus a thin root `App.kt`.
4. Add `BackendManager` before any screen work.
5. Add a small settings view-model backed by a user-home properties file.
6. Add file-picker support early for setup and image selection.

## Delivery Phases

### Phase 0: Foundation

Goal:

- create desktop module and build pipeline
- launch `diffusion_desk_server` from desktop app
- confirm API and WebSocket connectivity

Deliverables:

- desktop app shell
- environment/config bootstrap
- API client layer
- DTO coverage for existing routes used by the web UI

### Phase 1: Core Shell and Generation

Goal:

- make the desktop app usable for primary generation workflows

Deliverables:

- app window, sidebar, routing
- text-to-image screen
- image-to-image screen
- image result display
- generation history navigation
- WebSocket progress and VRAM display
- settings persistence

### Phase 2: Inpainting and Upscale

Goal:

- reach parity on image editing workflows

Deliverables:

- inpainting canvas
- mask export pipeline
- upscale workflow
- reference-image edit mode

### Phase 3: Gallery

Goal:

- restore post-generation asset workflows

Deliverables:

- virtualized image grid
- filters
- modal viewer
- delete flows
- tag and rating editing
- reuse/send-to-img2img flows

### Phase 4: Manager

Goal:

- restore administrative and creative library flows

Deliverables:

- image preset CRUD
- LLM preset CRUD
- styles CRUD
- tag cleanup
- model metadata editing
- LoRA preview flows

### Phase 5: Assistant and Exploration

Goal:

- restore power-user workflows

Deliverables:

- assistant panel
- tool call rendering
- image attachment and drag/drop
- exploration grid
- mutation-builder parity

### Phase 6: Packaging and Cleanup

Goal:

- make the desktop app shippable

Deliverables:

- packaged backend binaries
- first-run setup
- logging and crash handling
- asset path validation
- release packaging

## Recommended Port Order By File

Map the port in this order:

1. `webui/src/router/index.ts`
2. `webui/src/App.vue`
3. `webui/src/components/Sidebar.vue`
4. `webui/src/components/FloatingActionBar.vue`
5. `webui/src/stores/generation.ts`
6. `webui/src/components/GenerationForm.vue`
7. `webui/src/components/ImageDisplay.vue`
8. `webui/src/components/GenerationProgress.vue`
9. `webui/src/components/InpaintingCanvas.vue`
10. `webui/src/components/ImageGallery.vue`
11. `webui/src/views/ManagerView.vue`
12. `webui/src/stores/assistant.ts`
13. `webui/src/components/AssistantPanel.vue`
14. `webui/src/stores/exploration.ts`
15. `webui/src/services/mutation-builder.ts`

## Qwen3 TTS Blueprint Reuse

The local reference repo `qwen-tts-studio` has now been inspected and should be treated as the structural blueprint for the desktop migration.

Use that repo as a blueprint for:

- Gradle and Compose project layout
- desktop app bootstrap
- packaging structure
- resource bundling
- settings persistence
- screen and view-model separation
- release tasks

Do not use it as a blueprint for:

- JNI loading
- native library fallback behavior
- engine/runtime coupling
- checked-in native build output layout

The most important adaptation is this:

- `qwen-tts-studio` loads native code in-process
- DiffusionDesk must launch and supervise its backend as a separate process

See:

- `docs/COMPOSE_BLUEPRINT_DIFF.md`

## Risks

### Risk: Porting the giant generation store one-to-one

Mitigation:

- split it into focused feature state holders early

### Risk: Gallery performance regressions

Mitigation:

- design the gallery as a virtualized lazy grid from the start

### Risk: Inpainting canvas takes too long

Mitigation:

- isolate it as its own feature
- do not block the rest of the desktop shell on it

### Risk: Backend process startup and packaging

Mitigation:

- solve process bootstrap in phase 0 before any large UI work

### Risk: Desktop-specific input gaps

Mitigation:

- allow narrow Swing/AWT interop where it saves time
- do not escalate immediately to raw Skia

## Effort Estimate

Single experienced engineer:

- MVP desktop shell with core generation: 6-10 weeks
- near-parity app: 3-5 months
- packaging and polish to production quality: 4-6 months total

This assumes:

- backend remains separate and unchanged,
- the Qwen3 TTS repo can be reused for Compose packaging and bootstrap,
- no backend API redesign is done during the migration.

## Exit Criteria

The desktop migration is complete when:

- the desktop app starts and supervises the backend locally,
- all primary generation workflows work without opening a browser,
- progress and metrics match current behavior,
- gallery and manager reach functional parity,
- assistant and exploration are restored,
- the app can be packaged and launched as a standalone desktop application.
