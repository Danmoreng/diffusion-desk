# MystiCanvas

MystiCanvas is a high-performance, self-hosted Creative AI server that integrates the best of image generation and large language model capabilities. It leverages `stable-diffusion.cpp` for state-of-the-art image synthesis and `llama.cpp` for efficient LLM processing, all within a unified C++ backend.

## Features

- **Multi-Process Architecture:** Orchestrator-Worker design allows simultaneous Image and Text generation without resource conflicts or UI freezing.
- **Dual-Backend Power:** Seamlessly links against both `stable-diffusion.cpp` and `llama.cpp` using a shared GGML foundation.
- **Image Generation:** Supports FLUX, Z-Image, SDXL, and more with full GPU acceleration (CUDA).
- **Advanced Sampling:** Includes support for various schedulers, guidance scales, and Highres-Fix upscaling.
- **Built-in Upscaling:** Native integration of ESRGAN for high-quality image enhancement.
- **Modern WebUI:** A fast, responsive Vue.js (Vite) frontend for intuitive generation and model management.
- **Progress Streaming:** Real-time feedback via Server-Sent Events (SSE).
- **Flexible Model Loading:** Dynamic loading of Diffusion models, VAEs, Text Encoders, and LoRAs via a standardized directory structure and JSON sidecar configs.

## Project Structure

- `src/`: C++ Backend source code (httplib server, SD/Llama wrappers).
- `webui/`: Vue.js 3 + TypeScript frontend.
- `libs/`: Submodules for `stable-diffusion.cpp` and `llama.cpp`.
- `scripts/`: Build and automation scripts.
- `public/`: Static assets and compiled frontend location.

## Quick Start

### Prerequisites

- **C++ Compiler:** MSVC (Windows), GCC, or Clang.
- **CMake:** Version 3.14 or higher.
- **Node.js & NPM:** For building the frontend.
- **CUDA Toolkit:** (Optional) For GPU acceleration.

### Build Instructions

1. **Clone the repository with submodules:**
   ```bash
   git clone --recursive https://github.com/youruser/MystiCanvas.git
   cd MystiCanvas
   ```

2. **Build the WebUI:**
   ```bash
   cd webui
   npm install
   npm run build
   cd ..
   ```

3. **Build the Backend:**
   Use the provided PowerShell script for a streamlined build on Windows:
   ```powershell
   .\scripts\build.ps1
   ```

### Running the Server

Start the server by pointing it to your models directory:
```bash
.\build\bin\mysti_server.exe --model-dir "C:\Path\To\Your\Models"
```

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
