#pragma once

#include <string>
#include <vector>
#include <map>
#include <filesystem>
#include <sstream>
#include <iomanip>
#include <cmath>
#include <functional>
#include <regex>

// SD Headers (from submodule)
#include "stable-diffusion.h"
#include "util.h"

#include "utils/common.hpp"

namespace fs = std::filesystem;

enum SDMode {
    IMG_GEN,
    VID_GEN,
    CONVERT,
    UPSCALE,
    MODE_COUNT
};

std::string sd_basename(const std::string& path);

// Resource Safety Wrappers for SD
struct SdCtxDeleter {
    void operator()(sd_ctx_t* ptr) const {
        if (ptr) free_sd_ctx(ptr);
    }
};
struct UpscalerCtxDeleter {
    void operator()(upscaler_ctx_t* ptr) const {
        if (ptr) free_upscaler_ctx(ptr);
    }
};

using SdCtxPtr = std::unique_ptr<sd_ctx_t, SdCtxDeleter>;
using UpscalerCtxPtr = std::unique_ptr<upscaler_ctx_t, UpscalerCtxDeleter>;

void sd_log_cb(enum sd_log_level_t level, const char* log, void* data);

struct SDContextParams {
    int n_threads = -1;
    std::string model_path;
    std::string clip_l_path;
    std::string clip_g_path;
    std::string clip_vision_path;
    std::string t5xxl_path;
    std::string llm_path;
    std::string llm_vision_path;
    std::string llm_model_path; 
    std::string diffusion_model_path;
    std::string high_noise_diffusion_model_path;
    std::string vae_path;
    std::string taesd_path;
    std::string esrgan_path;
    std::string control_net_path;
    std::string embedding_dir;
    std::string photo_maker_path;
    sd_type_t wtype = SD_TYPE_COUNT;
    std::string tensor_type_rules;
    std::string lora_model_dir;

    std::map<std::string, std::string> embedding_map;
    std::vector<sd_embedding_t> embedding_vec;

    rng_type_t rng_type         = CUDA_RNG;
    rng_type_t sampler_rng_type = RNG_TYPE_COUNT;
    bool offload_params_to_cpu  = false;
    bool control_net_cpu        = false;
    bool clip_on_cpu            = false;
    bool vae_on_cpu             = false;
    bool diffusion_flash_attn   = false;
    bool diffusion_conv_direct  = false;
    bool vae_conv_direct        = false;

    bool chroma_use_dit_mask = true;
    bool chroma_use_t5_mask  = false;
    int chroma_t5_mask_pad   = 1;

    prediction_t prediction           = PREDICTION_COUNT;
    lora_apply_mode_t lora_apply_mode = LORA_APPLY_AUTO;

    sd_tiling_params_t vae_tiling_params = {false, 0, 0, 0.5f, 0.0f, 0.0f};
    bool force_sdxl_vae_conv_scale       = false;

    float scale_factor = INFINITY;
    float shift_factor = INFINITY;
    float flow_shift = INFINITY;

    ArgOptions get_options();
    void build_embedding_map();
    bool process_and_check(SDMode mode);
    std::string to_string() const;
    sd_ctx_params_t to_sd_ctx_params_t(bool vae_decode_only, bool free_params_immediately, bool taesd_preview);
};

struct SDGenerationParams {
    std::string prompt;
    std::string prompt_with_lora;  
    std::string negative_prompt;
    int clip_skip   = -1;
    int width       = 512;
    int height      = 512;
    int batch_count = 1;
    std::string init_image_path;
    std::string end_image_path;
    std::string mask_image_path;
    std::string control_image_path;
    std::vector<std::string> ref_image_paths;
    std::string control_video_path;
    bool auto_resize_ref_image = true;
    bool increase_ref_index    = false;

    std::vector<int> skip_layers = {7, 8, 9};
    sd_sample_params_t sample_params;

    std::vector<int> high_noise_skip_layers = {7, 8, 9};
    sd_sample_params_t high_noise_sample_params;

    std::vector<float> custom_sigmas;

    std::string easycache_option;
    sd_easycache_params_t easycache_params;

    float moe_boundary  = 0.875f;
    int video_frames    = 1;
    int fps             = 16;
    float vace_strength = 1.f;

    float strength         = 0.75f;
    float control_strength = 0.9f;

    int64_t seed = 42;

    std::string pm_id_images_dir;
    std::string pm_id_embed_path;
    float pm_style_strength = 20.f;

    bool clip_on_cpu = false;

    bool hires_fix = false;
    std::string hires_upscale_model;
    float hires_upscale_factor = 2.0f;
    float hires_denoising_strength = 0.5f;
    int hires_steps = 20;

    int upscale_repeats   = 1;
    int upscale_tile_size = 128;

    std::map<std::string, float> lora_map;
    std::map<std::string, float> high_noise_lora_map;
    std::vector<sd_lora_t> lora_vec;

    SDGenerationParams();
    ArgOptions get_options();
    bool from_json_str(const std::string& json_str);
    void extract_and_remove_lora(const std::string& lora_model_dir);
    bool process_and_check(SDMode mode, const std::string& lora_model_dir);
    std::string to_string() const;
};
