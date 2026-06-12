# Diffusion Desk

Diffusion Desk is a local Windows desktop application for generating and
organizing images with `stable-diffusion.cpp`. The current application is built
with Kotlin Compose Multiplatform and runs the native image and LLM workers on
the local machine.

The primary workflow combines preset-driven generation, an image gallery,
local LLM assistance, and a visual Ideogram 4 composition editor. The older Vue
WebUI remains in the repository as a deprecated reference and is documented in
[docs/LEGACY_WEBUI_README.md](docs/LEGACY_WEBUI_README.md).

## Current Features

### Image Generation

- Local generation through the native `diffusion_desk_sd_worker`.
- JSON-backed image model presets with automatic loading when selected.
- Standard text prompts and negative prompts.
- Ideogram 4 structured JSON prompts selected by the active image preset,
  independently of the currently visible editor tab.
- Width, height, aspect-ratio presets, steps, CFG, seed, and sampler controls.
- Prompt undo and redo history.
- Queueing for separately started generations and optional Endless Generation.
- Compact progress in the action bar with phase, step count, elapsed time, and
  estimated remaining time.
- Generated-image history navigation without a batch-count workflow.
- Desktop image actions for copy, save, open, and show in Explorer.

### Ideogram 4 Composition Workflow

Ideogram presets expose three synchronized editing modes on the left:

1. **Controls** for the regular prompt and generation parameters.
2. **JSON** for raw structured prompt generation, validation, editing, and
   formatting.
3. **Composition** for editing the complete known Ideogram structure through
   dedicated controls.

The Composition editor currently supports:

- `high_level_description`.
- Style aesthetics, lighting, medium, photo or art-style description.
- Global color palette.
- Composition background.
- Object and text elements.
- Element type, literal text, description, palette, and optional bounding box.
- Additional JSON fields through a generic path/value editor.
- Adding, deleting, and converting object or text elements.
- Shared palette swatches with add, edit, and delete behavior.
- Schema limits of 16 global colors and 5 colors per element.
- Composition-wide undo and redo for manual and LLM changes.
- Preservation of valid unknown JSON fields during structured edits.
- Canonical Ideogram serialization for generation and formatted JSON for
  editing.

The right side is a shared canvas for both the planned composition and the
generated image:

- Bounding boxes can be selected, moved, and resized directly on the canvas.
- Element selection is synchronized between the editor and canvas.
- The canvas follows the configured output aspect ratio and does not upscale
  beyond the planned pixel dimensions.
- After generation, the composition can be displayed as an overlay on the
  image.
- The Composition toggle is persistent across generations and application
  restarts.
- Existing images remain visible while prompts and elements are edited, making
  it possible to arrange new objects over the previous result.
- Changing width or height switches the canvas to the new target dimensions
  until another image is generated.

### Local LLM Assistance

LLM presets can be assigned to the **Tagging**, **Assistant**, and **Prompt
Enhancer** roles. The Assistant role is configurable for future chat tooling;
the currently exposed generation features use the Prompt Enhancer role and fall
back to the Assistant role when necessary.

Available LLM actions include:

- Enhance a regular image prompt.
- Generate a complete Ideogram 4 JSON prompt from the current user prompt.
- Improve one selected composition text field without replacing the complete
  document.
- Improve individual object and text-element descriptions.
- Suggest a global palette or a palette for one element.

Composition actions are validated and applied as one atomic undoable change.
They share a typed action executor intended to be reused by future Assistant
tool calls.

When the selected LLM preset includes an `mmproj` vision projector and a current
generated image is available:

- Global actions receive the complete image.
- Element-focused actions receive a crop of the element bounding box with
  surrounding visual context.
- Element actions without a bounding box receive the complete image.
- Text-only context is used automatically when no current image or
  vision-capable preset is available.

The composition vision request is separate from the image-tagging request.

### Gallery

The Gallery indexes the configured output directory into a local SQLite
database and supports:

- Responsive thumbnail browsing with a resizable gallery layout.
- Search and keyword filtering.
- Prompt, negative prompt, model, dimensions, seed, sampler, and other metadata.
- Current sidecar metadata and embedded PNG metadata.
- Manual keyword editing.
- Local LLM tagging for one image or the pending gallery queue.
- Vision-based tagging when the Tagging preset has an `mmproj` projector.
- Reusing an older image's generation parameters in the Generate screen.
- Image deletion and desktop file actions.

Missing thumbnails are generated under `outputs/previews/`. Existing legacy
thumbnail mappings are reused when available.

### Image And LLM Presets

The Presets screen manages both image and LLM presets.

Image presets can configure:

- Diffusion model, VAE, CLIP-L, CLIP-G, T5XXL, and LLM text encoder paths.
- CPU placement and flash-attention options.
- Default width, height, steps, CFG, sampler, and negative prompt.
- Text or structured JSON prompt mode.

LLM presets can configure:

- GGUF model path.
- Optional multimodal projector (`mmproj`) path.
- CPU, GPU, or automatic placement.
- Additional worker arguments.

Image presets are stored in the app data directory under `image-presets/`.

### System And Settings

- Start, stop, and inspect the local image worker.
- Load, unload, stop, and inspect multiple local LLM workers.
- Assign LLM presets independently to Tagging, Assistant, and Prompt Enhancer
  roles.
- Optional autostart for configured LLM roles.
- Worker diagnostics including placement and VRAM information.
- Automatic or manual streaming VRAM budget.
- System, light, and dark themes.
- Top or bottom action-bar placement.
- Configurable model and output directories.
- Optional automatic saving of generated images.

## Screenshots

The screenshots below are intentionally left as capture tasks. Add the files to
`screenshots/` and uncomment the corresponding Markdown image line.

### 1. Generate And Composition Overlay

Capture the full application with the **Composition** tab open, a generated
image visible on the right, bounding boxes enabled, and one element selected.
The action bar and preset selector should also be visible.

Suggested file: `screenshots/generate-composition-overlay.png`

<!-- ![Generate view with Ideogram composition overlay](screenshots/generate-composition-overlay.png) -->

### 2. Structured Composition Editor

Capture the left editor showing the Overview, Style, global palette,
Background, and at least one object and one text element. Include the Improve
and Suggest actions as well as the matching canvas on the right.

Suggested file: `screenshots/ideogram-structured-editor.png`

<!-- ![Ideogram 4 structured composition editor](screenshots/ideogram-structured-editor.png) -->

### 3. Raw JSON Workflow

Capture the **JSON** tab with a representative formatted Ideogram prompt and
the composition preview still visible on the right.

Suggested file: `screenshots/ideogram-json-editor.png`

<!-- ![Ideogram JSON editor and composition preview](screenshots/ideogram-json-editor.png) -->

### 4. Gallery

Capture a populated Gallery with the thumbnail grid, search or keyword filter,
and the selected-image details panel visible.

Suggested file: `screenshots/gallery.png`

<!-- ![Searchable generated image gallery](screenshots/gallery.png) -->

### 5. Preset Management

Capture either the image-preset editor or a split set of screenshots showing
both image and LLM preset management. For the LLM preset, show the optional
`mmproj` and placement settings without exposing private paths.

Suggested files:

- `screenshots/image-preset-editor.png`
- `screenshots/llm-preset-editor.png`

<!-- ![Image model preset editor](screenshots/image-preset-editor.png) -->
<!-- ![Local LLM preset editor](screenshots/llm-preset-editor.png) -->

### 6. Local Worker Management

Capture the System screen with the image worker, LLM role assignments, and at
least one active LLM worker. Avoid showing private model-directory paths.

Suggested file: `screenshots/system-workers.png`

<!-- ![Image and LLM worker management](screenshots/system-workers.png) -->

## Runtime Layout

The Compose app expects the native workers and DLLs in the repository or
portable application root:

- SD worker: `build/bin/diffusion_desk_sd_worker.exe`
- LLM worker: `build/bin/diffusion_desk_llm_worker.exe`
- Models: `models/`
- Generated images: `outputs/`

Recommended model layout:

- `models/stable-diffusion/`
- `models/vae/`
- `models/text-encoder/`
- `models/llm/`
- `models/lora/`

## Requirements

Windows is the primary supported development and packaging target.

- Windows 10 or 11
- Visual Studio 2022 C++ build tools
- CMake
- CUDA Toolkit, because the native build currently enables CUDA by default
- Java 25 JDK/JBR for Compose Desktop
- Java 25 JDK/JBR with `jpackage` for packaging

The run and packaging scripts can use Gradle-provisioned JDKs from
`.gradle\jdks` when `JAVA_HOME` is not set.

## Build

Clone with submodules:

```powershell
git clone --recursive https://github.com/Danmoreng/diffusion-desk.git
cd diffusion-desk
```

Initialize missing submodules when necessary:

```powershell
git submodule update --init --recursive
```

Build the native backend and workers:

```powershell
.\scripts\build.ps1
```

For the Compose app, the important native outputs are the SD and LLM workers in
`build\bin\` together with their DLL dependencies.

## Run

Use the Compose helper script:

```powershell
.\scripts\run-compose.ps1
```

Or run Gradle directly:

```powershell
.\gradlew.bat :composeApp:run
```

For hot reload during Compose UI development:

```powershell
.\scripts\run-compose-hot-reload.ps1
```

## Package On Windows

Create a portable Windows application image and zip:

```powershell
.\gradlew.bat packageWindows
```

Or run the packaging script directly:

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

Outputs:

- App folder: `composeApp\build\compose\binaries\main\app\diffusion-desk`
- Portable zip:
  `composeApp\build\compose\binaries\main\portable\diffusion-desk-windows-portable.zip`

If `build\bin` already contains a current native build:

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -SkipNativeBuild
```

To also request an MSI from Compose and `jpackage`:

```powershell
.\gradlew.bat packageWindowsMsi
```

## Repository Map

- `composeApp/`: current Kotlin Compose desktop application
- `src/workers/`: native SD and LLM worker entry points
- `src/sd/`: image worker API, generation jobs, progress, and server state
- `src/orchestrator/`: older orchestrator/database/web stack
- `webui/`: deprecated Vue WebUI
- `reference/`: external prompting and schema references used during development
- `docs/`: architecture notes, plans, and legacy documentation
- `scripts/`: build, run, packaging, and verification scripts
- `libs/`: vendored and submodule dependencies

## Legacy Web UI

The older browser application and orchestrator still exist in this repository,
but they are not the primary product path. They can still be built and run with:

```powershell
.\scripts\build.ps1
.\scripts\run.ps1
```

This serves the Vue WebUI at `http://localhost:1234/app/`. See
[docs/LEGACY_WEBUI_README.md](docs/LEGACY_WEBUI_README.md) for its archived
architecture and feature description.

## Development Notes

- Validate Compose desktop changes with
  `.\gradlew.bat :composeApp:desktopTest` or the smallest relevant Compose task.
- Validate native/backend changes with `.\scripts\build.ps1`.
- Do not edit `libs/` unless intentionally changing vendored upstream code.
- Treat `config.json`, logs, databases, models, outputs, and build products as
  local runtime artifacts.
- The active Ideogram composition roadmap is documented in
  [docs/IDEOGRAM4_COMPOSITION_ASSISTANT_PLAN.md](docs/IDEOGRAM4_COMPOSITION_ASSISTANT_PLAN.md).

## License

Diffusion Desk is released under the [MIT License](LICENSE).
