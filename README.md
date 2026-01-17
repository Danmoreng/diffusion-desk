# DiffusionDesk

DiffusionDesk is a high-performance, self-hosted **Creative AI Workstation** built on the power of [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) and [llama.cpp](https://github.com/ggml-org/llama.cpp/). It integrates state-of-the-art image generation and large language model capabilities into a unified, local application.

Unlike simple generation frontends, DiffusionDesk functions as a complete **Asset Management System**. It persists your creative history, uses local LLMs to intelligently analyze and tag your images, and provides advanced workflows for refinement and exploration.

## Key Capabilities

### Generation & Editing
- **Dual-Backend Power:** Seamlessly links against [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) and [llama.cpp](https://github.com/ggml-org/llama.cpp/) using a shared GGML foundation.
- **Image Generation:** Supports FLUX, Z-Image, SDXL, and more with full GPU acceleration (CUDA).
- **Inpainting Canvas:** Integrated editor for masking and regenerating specific parts of an image.
- **Built-in Upscaling:** Native integration of ESRGAN for high-quality image enhancement.
- **Dynamic Exploration:** Generate variations of prompts and seeds to explore the latent space and discover new styles.

### Smart Asset Management
- **SQLite Database:** Stores all your creative assets, including:
    - **History:** Every generation prompt and result.
    - **Presets:** Saved model configurations (Model + VAE + CLIP + Settings).
    - **Styles & Prompts:** Your personal library of reusable styles.
- **Smart Tagging:** Runs a background **Tagging Service** that uses your loaded LLM to analyze generated images and automatically assign descriptive tags (Subject, Style, Mood).
- **Advanced Filtering:** Search your history by date, model, rating, or specific tags.

### System Architecture
- **Multi-Process Design:** Orchestrator-Worker architecture allowing simultaneous Image and Text generation without resource conflicts or UI freezing.
- **Job Queue:** Asynchronous background processing for tasks like auto-tagging and batch generation.
- **WebSocket Hub:** Real-time generation progress and system-wide VRAM monitoring.
- **Internal Security:** Automatic transient token authentication between the Orchestrator and backend workers.

## Project Structure

- `src/`: C++ Backend source code.
    - `orchestrator/`: Main server, database management, tagging service, and job queue.
    - `workers/`: Wrappers for SD and LLM inference.
- `webui/`: Vue.js 3 + TypeScript frontend with specialized views for History, Inpainting, and Settings.
- `libs/`: Submodules for `stable-diffusion.cpp`, `llama.cpp`, `ixwebsocket`, and `SQLiteCpp`.
- `scripts/`: Build and automation scripts.
- `models/`: Directory structure for AI models.

## Quick Start

### Prerequisites

- **C++ Compiler:** MSVC 2022 (Windows), GCC, or Clang.
- **CMake:** Version 3.14 or higher.
- **Node.js & NPM:** For building the frontend.
- **CUDA Toolkit:** Required for GPU acceleration (Project is configured for CUDA by default).

### Build Instructions

1. **Clone the repository with submodules:**
   ```bash
   git clone --recursive https://github.com/Danmoreng/diffusion-desk.git
   cd diffusion-desk
   ```
   *If you have already cloned the repository without submodules, initialize them manually:*
   ```bash
   git submodule update --init --recursive
   ```

2. **Run the One-Click Build Script:**
   The provided scripts handle NPM installation, Vue compilation, and C++ build.

   **Windows:**
   ```powershell
   .\scripts\build.ps1
   ```

   **Linux:**
   ```bash
   chmod +x scripts/build.sh
   ./scripts/build.sh
   ```

### Running the Server

Start the server using the launch script or directly via the binary:

**Windows:**
```powershell
.\scripts\run.ps1
```

**Linux:**
```bash
chmod +x scripts/run.sh
./scripts/run.sh
```
The server will initialize the SQLite database (`diffusion_desk.db`) and start the WebUI at `http://localhost:1234/app/`.

### Configuration

DiffusionDesk uses two systems for configuration:

1.  **`config.json`:** Manages **infrastructure settings** such as:
    *   Server listening IP and Port.
    *   Paths to the `models` and `outputs` directories.
    *   Default LLM to load on startup.
    *   System resource limits (timeouts, threads).

2.  **SQLite Database (`diffusion_desk.db`):** Manages **creative configurations** such as:
    *   **Image Presets:** Saved combinations of Checkpoints, VAEs, and CLIP models.
    *   **Generation Parameters:** Your default steps, CFG scale, and sampler choices.

## Model Organization

DiffusionDesk expects models to be organized in the following subdirectories:
- `models/stable-diffusion/`: Main model files (`.gguf`, `.safetensors`).
- `models/vae/`: Variational Autoencoders.
- `models/text-encoder/`: CLIP, T5, and LLM encoders (including those used for tagging).
- `models/lora/`: Low-Rank Adaptations.
- `models/esrgan/`: Upscaler models.

### Signature-Based Presets
DiffusionDesk uses a **Signature-Based Loading System**. All model configurations are managed via **Image Presets** in the Library Manager.

When you load a preset, the Orchestrator computes a unique signature based on:
- The main **UNet/Diffusion** model path.
- Associated **VAE** and **CLIP** (L/G) models.
- **T5XXL** or **LLM Text Encoders** (for architectures like Flux or Z-Image).
- **Optimization Flags** (Flash Attention, VAE Tiling, CPU Offloading).

This ensures that the system correctly reloads all necessary components when switching between presets, even if they share the same base model.

### Support for Modern Architectures
DiffusionDesk natively supports complex multi-model pipelines:
- **Flux / SD3 Support:** Dedicated slots for T5XXL and multiple CLIP encoders.
- **Z-Image / LLM-Encoding:** Models requiring LLM-based text encoders (like Qwen) can be configured using the **LLM Text Encoder 3** slot in the preset editor.

## License

DiffusionDesk is released under the [MIT License](LICENSE).