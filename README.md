# MystiCanvas

MystiCanvas is a high-performance, self-hosted Creative AI server that integrates the best of image generation and large language model capabilities. It leverages `stable-diffusion.cpp` for state-of-the-art image synthesis and `llama.cpp` for efficient LLM processing, all within a unified C++ backend.

## Features

- **Multi-Process Architecture:** Orchestrator-Worker design allows simultaneous Image and Text generation without resource conflicts or UI freezing.
- **WebSocket Hub:** Real-time generation progress and system-wide VRAM monitoring via a robust WebSocket connection.
- **Internal Security:** Automatic transient token authentication between the Orchestrator and backend workers.
- **Dual-Backend Power:** Seamlessly links against both `stable-diffusion.cpp` and `llama.cpp` using a shared GGML foundation.
- **Image Generation:** Supports FLUX, Z-Image, SDXL, and more with full GPU acceleration (CUDA).
- **Built-in Upscaling:** Native integration of ESRGAN for high-quality image enhancement.
- **Modern WebUI:** A fast, responsive Vue.js (Vite) frontend with a server-side SPA fallback for a seamless experience.
- **Centralized Config:** Manage paths and ports via `config.json` with support for environment variables.

## Project Structure

- `src/`: C++ Backend source code (httplib server, SD/Llama wrappers, WebSocket hub).
- `webui/`: Vue.js 3 + TypeScript frontend.
- `libs/`: Submodules for `stable-diffusion.cpp`, `llama.cpp`, and `ixwebsocket`.
- `scripts/`: Build and automation scripts.
- `public/`: Static assets and compiled frontend location (`/app/`).

## Quick Start

### Prerequisites

- **C++ Compiler:** MSVC 2022 (Windows), GCC, or Clang.
- **CMake:** Version 3.14 or higher.
- **Node.js & NPM:** For building the frontend.
- **CUDA Toolkit:** (Optional) For GPU acceleration.

### Build Instructions

1. **Clone the repository with submodules:**
   ```bash
   git clone --recursive https://github.com/Danmoreng/MystiCanvas.git
   cd MystiCanvas
   ```

2. **Run the One-Click Build Script:**
   The provided PowerShell script handles NPM installation, Vue compilation, and C++ build:
   ```powershell
   .\scripts\build.ps1
   ```

### Running the Server

Start the server using the launch script or directly via the binary:
```powershell
.\scripts\run.ps1
```
The server will use the settings defined in `config.json`. By default, the UI is available at `http://localhost:1234/app/`.

## Model Organization

MystiCanvas expects models to be organized in the following subdirectories:
- `models/stable-diffusion/`: Main model files (`.gguf`, `.safetensors`).
- `models/vae/`: Variational Autoencoders.
- `models/text-encoder/`: CLIP and T5 encoders.
- `models/lora/`: Low-Rank Adaptations.
- `models/esrgan/`: Upscaler models.

Every main model in `stable-diffusion/` requires a `.json` sidecar file (e.g., `model.gguf.json`) to define its architecture and associated components.

### Example Configuration (`z_image_turbo.gguf.json`)
```json
{
  "vae": "vae/ae.safetensors",
  "llm": "text-encoder/Qwen3-4B-Instruct-2507-Q8_0.gguf",
  "flash_attn": true
}
```

### Supported Configuration Keys
- **Paths (relative to model-dir):** `vae`, `clip_l`, `clip_g`, `t5xxl`, `llm`.
- **Performance Flags:** `clip_on_cpu` (bool), `vae_on_cpu` (bool), `offload_to_cpu` (bool), `flash_attn` (bool), `vae_tiling` (bool).

## License

MystiCanvas is released under the [MIT License](LICENSE).
