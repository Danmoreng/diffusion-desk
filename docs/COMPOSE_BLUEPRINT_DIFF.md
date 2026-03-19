# Compose Blueprint Diff

This document records what should be copied, adapted, or avoided from the local reference repo:

- local reference: `C:\StableDiffusion\qwen-tts-studio`

The goal is to use it as a structural blueprint for the DiffusionDesk desktop migration without importing assumptions that do not fit DiffusionDesk's backend model.

## Copy

These patterns should be copied with minimal changes.

### 1. Project Shape

Reference:

- root `settings.gradle.kts`
- root `build.gradle.kts`
- single app module `:composeApp`

Why copy it:

- simple Gradle topology
- clear desktop ownership boundary
- easy packaging integration

### 2. Desktop Entry Pattern

Reference:

- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/main.kt`
- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/App.kt`

Why copy it:

- thin `main.kt`
- root `App()` composable
- window setup separated from feature logic

### 3. Screen and ViewModel Split

Reference:

- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/screens/`
- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/viewmodel/`

Why copy it:

- feature logic lives in view-models
- UI stays declarative
- state ownership is clear

### 4. User-Home Settings Persistence

Reference:

- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/viewmodel/SettingsViewModel.kt`

Why copy it:

- practical desktop persistence
- no browser assumptions
- easy migration target for current `localStorage` settings

### 5. Build and Packaging Wrappers

Reference:

- root `build.gradle.kts`
- `scripts/build-native.ps1`
- `scripts/package-windows.ps1`

Why copy it:

- native build and Compose package steps are explicit
- packaging remains scriptable outside the app module
- Windows packaging is already structured around native-first then app-image assembly

## Adapt

These patterns are useful, but must be adapted for DiffusionDesk.

### 1. Native Boundary

Reference:

- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/engine/QwenEngine.kt`

Adaptation for DiffusionDesk:

- replace `QwenEngine` with a `BackendManager`
- manage external processes instead of in-process JNI
- expose HTTP and WebSocket connection details to the UI layer

Target responsibilities:

- locate packaged binaries
- spawn `diffusion_desk_server`
- wait for readiness
- monitor exit state
- terminate backend cleanly on app shutdown

### 2. Setup Flow

Reference:

- `composeApp/src/desktopMain/kotlin/com/qwen/tts/studio/screens/SetupScreen.kt`

Adaptation for DiffusionDesk:

- setup must cover model directory, output directory, and potentially first-run checks
- setup should query or update the orchestrator config instead of only updating local app settings

### 3. Packaging Artifact Copy

Reference:

- `scripts/package-windows.ps1`

Adaptation for DiffusionDesk:

- copy packaged executables, not JNI DLLs only
- include:
  - `diffusion_desk_server`
  - `diffusion_desk_sd_worker`
  - `diffusion_desk_llm_worker`
  - `public/`
  - config defaults if required

### 4. State Model

Reference:

- view-models based on `MutableStateFlow`

Adaptation for DiffusionDesk:

- use the same general approach
- split current frontend state into feature-focused view-models instead of one giant store

Recommended split:

- `AppViewModel`
- `GenerationViewModel`
- `GalleryViewModel`
- `ManagerViewModel`
- `AssistantViewModel`
- `ExplorationViewModel`
- `SettingsViewModel`

### 5. File Picking

Reference:

- `filekit` usage in setup

Adaptation for DiffusionDesk:

- use it not only for setup directories, but also for:
  - init image selection
  - reference image selection
  - import/export features if added

## Avoid

These patterns from the reference repo should not be copied.

### 1. In-Process Native Coupling

Do not copy:

- JNI loading strategy
- JNA-related startup flags
- native fallback logic tied to local shared libraries

Reason:

- DiffusionDesk already has a local backend process architecture
- forcing it into JNI would make the migration riskier, not simpler

### 2. Engine-Centric App Model

Do not copy:

- app logic organized around one in-process engine class

Reason:

- DiffusionDesk is a multi-process local service app
- the desktop shell should be a client plus supervisor, not the inference runtime

### 3. Checked-In Build Output Layout

Do not copy:

- checked-in native build outputs under `external/build-*`

Reason:

- build artifacts should stay out of version control
- DiffusionDesk should keep packaging reproducible without committing generated outputs

## Concrete Takeaways For DiffusionDesk

Implement these first:

1. Create `:composeApp`.
2. Add root Gradle tasks to wrap packaging/build scripts.
3. Add desktop `main.kt` and root `App.kt`.
4. Implement `BackendManager`.
5. Implement `SettingsViewModel` with user-home persistence.
6. Add file-picker support.
7. Keep feature logic in dedicated view-models from the start.

## Useful Reference Files

- `C:\StableDiffusion\qwen-tts-studio\settings.gradle.kts`
- `C:\StableDiffusion\qwen-tts-studio\build.gradle.kts`
- `C:\StableDiffusion\qwen-tts-studio\composeApp\build.gradle.kts`
- `C:\StableDiffusion\qwen-tts-studio\composeApp\src\desktopMain\kotlin\com\qwen\tts\studio\main.kt`
- `C:\StableDiffusion\qwen-tts-studio\composeApp\src\desktopMain\kotlin\com\qwen\tts\studio\App.kt`
- `C:\StableDiffusion\qwen-tts-studio\composeApp\src\desktopMain\kotlin\com\qwen\tts\studio\viewmodel\SettingsViewModel.kt`
- `C:\StableDiffusion\qwen-tts-studio\composeApp\src\desktopMain\kotlin\com\qwen\tts\studio\engine\QwenEngine.kt`
- `C:\StableDiffusion\qwen-tts-studio\scripts\build-native.ps1`
- `C:\StableDiffusion\qwen-tts-studio\scripts\package-windows.ps1`
