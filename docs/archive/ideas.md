# DiffusionDesk: Comprehensive Architecture & Intelligence Plan

**Date:** December 30, 2025
**Status:** Orchestrator Refactor Complete; Transitioning to Model Preset & Assistant Architecture.

---

## 1. Executive Summary
DiffusionDesk is evolving from a Stable Diffusion/LLM UI into an **Integrated Creative AI Sandbox**. The core philosophy is **Isolation for Stability**: using a multi-process architecture where an Orchestrator manages specialized Workers. 

We have successfully refactored the Orchestrator from a monolithic "God Function" into a Service-based architecture (`ServiceController`, `ResourceManager`). The next phase focuses on **Data Formalization** (Model Bundles) and **Agentic Intelligence** (The Creative Assistant).

---

## 2. The Model Preset (Bundle) System
Current model selection is file-based and flat. We will transition to a **Preset-based System** to group dependent components.

### A. Image Generation Presets (The "Palette")
Instead of picking a UNet, then a VAE, then a Text Encoder, users will select a "Preset."
*   **Components:**
    *   `unet_path`: Primary diffusion weights.
    *   `vae_path`: Specific VAE (e.g., SDXL-fixed, TAESD).
    *   `clip_l`, `clip_g`, `t5xxl`: The text encoder stack (Crucial for Flux/SD3).
*   **Metadata:**
    *   `vram_weights_mb`: Sum of disk sizes of all included files (used for VRAM prediction).
    *   `preferred_params`: Default resolution, steps, and CFG scale for this specific bundle.

### B. LLM Intelligence Presets (The "Brain")
LLMs will also be bundled to support multi-file requirements (Vision).
*   **Components:**
    *   `model_path`: Main GGUF file.
    *   `mmproj_path`: Vision projector (for LLaVA/Qwen-VL).
*   **Metadata:**
    *   `n_ctx`: Optimal context window.
    *   `capabilities`: JSON array (e.g., `["chat", "tagging", "vision", "tool_use"]`).
    *   `role`: (e.g., "General Assistant", "Background Tagger", "Vision Analyzer").

---

## 3. The Creative Assistant (Agentic Workflow)
We propose an integrated Chat interface where the LLM acts as a **Creative Partner** with access to the system's internal tools.

### A. Function Calling (Tool Use)
The Assistant LLM will have access to JSON-RPC style functions:
*   `get_styles()`: Browse the style library.
*   `apply_style(style_name)`: Inject style keywords into the current generation prompt.
*   `enhance_prompt(user_input)`: Rewrite simple prompts into descriptive ones.
*   `search_history(query)`: Find previous generations using FTS5 search.

### B. Vision Integration
Using Vision-LLMs (LLaVA/Qwen-VL) to bridge the gap between pixels and text:
*   **Auto-Tagging v2:** Analyze the *actual image* output to generate tags (Subject, Composition, Lighting) rather than just parsing the prompt.
*   **Visual Feedback Loop:** The Assistant looks at the last generated image and suggests improvements ("The subject is too dark, should we add 'rim lighting' to the prompt?").

---

## 4. Advanced VRAM Management
To support high-end models (Flux, SD3) on consumer hardware, we will move from brute-force unloading to **Predictive & Surgical Arbitration**.

### A. Predictive Arbitration (Orchestrator Level)
The `ResourceManager` will predict usage *before* execution:
1.  **Baseline:** Look up `vram_weights_mb` from the active Preset in the DB.
2.  **Compute Delta:** Calculate expected compute buffers based on `(Width * Height * BatchSize)`.
3.  **Arbitration:** If `Baseline + Compute + Safety_Buffer > Available_VRAM`, the Orchestrator will:
    *   Attempt to "Soft Unload" LLM (clear KV cache).
    *   If still insufficient, "Hard Unload" the LLM worker.

### B. Surgical Offloading (Worker Level)
*   **Text Encoder Offloading:** Use the `clip_on_cpu` flag for massive encoders (like T5XXL) to save ~4GB of VRAM during the U-Net/Sampling phase.
*   **Dynamic VAE Tiling:** Automatically toggle VAE tiling based on image dimensions to prevent OOM during the final decode step.
*   **VAE-on-CPU:** If VRAM is critically low, perform only the VAE decode on CPU, keeping the fast U-Net weights on the GPU.

---

## 5. Database V2 (The "Memory" Layer)
Refactoring `database.cpp` to support the new intelligence features:
*   **FTS5 (Full-Text Search):** Lightning-fast searching of prompt history.
*   **Preset Tables:** `image_presets` and `llm_presets` to store the bundle configurations.
*   **Transactions:** Ensure atomic saves for images and their auto-generated tags.

---

## 6. UI/UX Refinements
*   **Integrated Sidebar Chat:** A persistent drawer for the Creative Assistant.
*   **VRAM Heatmap:** Enhance the `VramIndicator` to show projected usage vs. actual usage.
*   **Preset Manager:** A dedicated view to "build" your model stacks.

---

## 7. Consultation Questions for ChatGPT Pro
1.  **Optimization:** Given a fixed VRAM (e.g., 12GB), what is the most efficient strategy for swapping between a 4GB Text Encoder and a 6GB U-Net?
2.  **Agent Logic:** What is the best system prompt structure to ensure an LLM handles "Creative Assistance" without over-explaining or getting stuck in loops?
3.  **Schema Design:** Should model presets be strictly separated into Image/LLM, or should we support "Global Presets" (e.g., "The SDXL Productivity Stack" which loads both an SDXL model and a fast Llama-3-8B)?
4.  **Vision Workflows:** What is the most performant way to pass generated images from the SD Worker to the LLM Worker for analysis without heavy disk I/O? (Shared memory vs. local temporary files).