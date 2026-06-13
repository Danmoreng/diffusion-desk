# Diffusion Desk

Diffusion Desk is a Windows desktop application for generating, refining, and
organizing images on your own machine. It combines a native
[`stable-diffusion.cpp`](https://github.com/leejet/stable-diffusion.cpp) worker
with a Kotlin Compose desktop interface, so image generation and optional LLM
assistance stay local.

![Diffusion Desk Generate view with an Ideogram composition overlay](screenshots/generate-composition-overlay.png)

## What It Is

Diffusion Desk is designed as a practical workspace around local image models:

- **Generate** images from reusable model presets and keep working while jobs
  are queued.
- **Compose** structured Ideogram 4 prompts visually, including text, objects,
  colors, and bounding boxes on the image canvas.
- **Refine** prompts and composition elements with an optional local LLM. Vision
  models can use the current image or a selected region as context.
- **Browse** generated images in a searchable gallery, inspect their metadata,
  and reuse their settings for another generation.
- **Manage** image models, LLMs, workers, output folders, and GPU memory from the
  desktop application.

Everything runs locally. Diffusion Desk does not provide models and does not
require a hosted generation service.

## Project Status

Diffusion Desk is under active development and currently targets Windows with
an NVIDIA GPU. The Compose desktop application in `composeApp/` is the current
product; the Vue application in `webui/` is deprecated and kept only as a
legacy reference.

Expect the setup and preset formats to continue evolving while the desktop
workflow is being developed.

## Getting Started

### Requirements

- Windows 10 or 11
- An NVIDIA GPU with a supported CUDA setup
- Visual Studio 2022 C++ build tools
- CMake
- CUDA Toolkit
- Java 25 JDK or JBR
- Git with submodule support

### Build From Source

Clone the repository together with its submodules:

```powershell
git clone --recursive https://github.com/Danmoreng/diffusion-desk.git
cd diffusion-desk
```

If the repository was cloned without submodules, initialize them separately:

```powershell
git submodule update --init --recursive
```

Build the native image and LLM workers:

```powershell
.\scripts\build.ps1
```

Then start the desktop application:

```powershell
.\scripts\run-compose.ps1
```

The launch script looks for Java 25 through `JAVA_HOME`, `PATH`, common JBR
installations, and Gradle-provisioned JDKs. You can also pass it explicitly:

```powershell
.\scripts\run-compose.ps1 -JavaHome "C:\Path\To\jdk-25"
```

### First Launch

1. Open **Settings** and check the repository, model, and output directories.
2. Open **Presets** and create an image preset that points to your local model
   files and defines sensible generation defaults.
3. Open **System** to start and inspect the image worker.
4. Return to **Generate**, select the preset, enter a prompt, and start a
   generation.

Generated images are stored in the configured output directory and appear in
the Gallery. Local LLM features are optional; configure an LLM preset and
assign it to a role in the System screen when you want prompt enhancement,
composition assistance, or image tagging.

## Ideogram Composition

For image presets using structured Ideogram 4 prompts, the Generate screen
offers three views of the same prompt:

- **Controls** for the regular prompt and generation parameters
- **JSON** for the complete structured prompt
- **Composition** for editing descriptions, palettes, objects, text elements,
  and their placement visually

Composition elements can be selected, moved, and resized directly on the
canvas. Changes support undo and redo, and the planned composition can be shown
over the generated image. Optional local LLM actions can improve individual
fields or generate a complete composition without sending the prompt or image
to a remote service.

## Portable Windows Build

Create a portable application folder and ZIP archive with:

```powershell
.\gradlew.bat packageWindows
```

The archive is written to:

```text
composeApp\build\compose\binaries\main\portable\diffusion-desk-windows-portable.zip
```

To reuse an existing native build:

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -SkipNativeBuild
```

Packaging requires a Java 25 JDK or JBR that includes `jpackage`.

## Repository Guide

- `composeApp/`: current Kotlin Compose desktop application
- `src/`: native backend and SD/LLM workers
- `scripts/`: build, run, packaging, and verification scripts
- `docs/`: architecture notes, implementation plans, and legacy documentation
- `webui/`: deprecated Vue frontend
- `libs/`: submodules and vendored upstream dependencies

See [ARCHITECTURE.md](ARCHITECTURE.md) for the system design and
[docs/LEGACY_WEBUI_README.md](docs/LEGACY_WEBUI_README.md) for the archived web
interface.

## Development

Run the Compose application directly with Gradle:

```powershell
.\gradlew.bat :composeApp:run
```

Use the hot-reload helper while working on the desktop UI:

```powershell
.\scripts\run-compose-hot-reload.ps1
```

Run the Compose desktop tests with:

```powershell
.\gradlew.bat :composeApp:desktopTest
```

For native changes, use `.\scripts\build.ps1` so the repository's Windows
build configuration and patches are applied consistently.

## License

Diffusion Desk is released under the [MIT License](LICENSE).
