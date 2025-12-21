# Debugging Results: Black & Noisy Images Resolved

## 1. Root Cause Analysis
The primary issue was a **Memory Layout Mismatch** between the MystiCanvas server and the `stable-diffusion.cpp` library.
- **The Bug:** The server was initializing the `sd_ctx_params_t` struct using an outdated definition that lacked the `circular_x` and `circular_y` fields. 
- **The Consequence:** This caused all subsequent parameters (like `force_sdxl_vae_conv_scale`, `chroma_use_dit_mask`, and `flow_shift`) to be shifted in memory. The library was reading garbage values for critical sampling and VAE flags, leading to collapsed latents (black images) or failure to denoise (noisy images).
- **Secondary Issue:** Flux-based models (including Z-Image) were occasionally using the wrong VAE (`vae.safetensors` instead of the 16-channel `ae.safetensors`), and parameters like `flash_attn` were not being reset when switching models.

## 2. Solutions Applied
- **Struct Alignment:** Updated `MystiCanvas/src/utils/common.hpp` to perfectly match the library's `sd_ctx_params_t` definition, including the missing circularity fields.
- **VAE Configuration:** Standardized JSON sidecar files for `flux1-schnell`, `flux1-dev`, and `z_image` to use `vae/ae.safetensors`.
- **State Management:** Modified `handle_load_model` in `api_endpoints.cpp` to fully reset all context flags (Flash Attention, VAE Tiling, etc.) to defaults before loading a new model config.
- **Denoiser Tuning:** Removed hardcoded `prediction: flux_flow` overrides from Z-Image and Flux Schnell configs to allow the library to use its calibrated internal defaults.

## 3. Status
- **Z-Image:** Verified working. Generates clear images with Euler and Guidance 1.0.
- **Flux Models:** Verified working with 16-channel VAE.
- **Dual Backend:** Linking against both `llama.cpp` and `stable-diffusion.cpp` is confirmed stable using `-DSD_BUILD_EXTERNAL_GGML=ON`.

## 4. Maintenance Notes
If the `stable-diffusion.cpp` submodule is updated in the future, **always** verify that the `sd_ctx_params_t` struct in `stable-diffusion.h` matches the initialization in `MystiCanvas/src/utils/common.hpp`.
