#include "sd_common.hpp"
#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <map>
#include <regex>
#include <random>
#include <filesystem>
#include <cstdarg>
#include <cstring>
#include <cstdio>

namespace fs = std::filesystem;

std::string sd_basename(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos != std::string::npos) {
        return path.substr(pos + 1);
    }
    pos = path.find_last_of('\\');
    if (pos != std::string::npos) {
        return path.substr(pos + 1);
    }
    return path;
}

void sd_log_cb(enum sd_log_level_t level, const char* log, void* data) {
    SDSvrParams* svr_params = (SDSvrParams*)data;
    log_print(static_cast<DDLogLevel>(level), log, svr_params->verbose, svr_params->color);
}

ArgOptions SDContextParams::get_options() {
    ArgOptions options;
    options.string_options = {
        {"-m", "--model", "path to full model", &model_path},
        {"", "--clip_l", "path to the clip-l text encoder", &clip_l_path},
        {"", "--clip_g", "path to the clip-g text encoder", &clip_g_path},
        {"", "--clip_vision", "path to the clip-vision encoder", &clip_vision_path},
        {"", "--t5xxl", "path to the t5xxl text encoder", &t5xxl_path},
        {"", "--llm", "path to the llm text encoder. For example: (qwenvl2.5 for qwen-image, mistral-small3.2 for flux2, ...)", &llm_path},
        {"-lm", "--llm-model", "path to the chat/instruct LLM model to load on startup", &llm_model_path},
        {"", "--llm_vision", "path to the llm vit", &llm_vision_path},
        {"", "--qwen2vl", "alias of --llm. Deprecated.", &llm_path},
        {"", "--qwen2vl_vision", "alias of --llm_vision. Deprecated.", &llm_vision_path},
        {"", "--diffusion-model", "path to the standalone diffusion model", &diffusion_model_path},
        {"", "--high-noise-diffusion-model", "path to the standalone high noise diffusion model", &high_noise_diffusion_model_path},
        {"", "--vae", "path to standalone vae model", &vae_path},
        {"", "--taesd", "path to taesd. Using Tiny AutoEncoder for fast decoding (low quality)", &taesd_path},
        {"", "--tae", "alias of --taesd", &taesd_path},
        {"", "--control-net", "path to control net model", &control_net_path},
        {"", "--embd-dir", "embeddings directory", &embedding_dir},
        {"", "--lora-model-dir", "lora model directory", &lora_model_dir},
        {"", "--tensor-type-rules", "weight type per tensor pattern (example: \"^vae\\.=f16,model\\.=q8_0\")", &tensor_type_rules},
        {"", "--photo-maker", "path to PHOTOMAKER model", &photo_maker_path},
        {"", "--upscale-model", "path to esrgan model.", &esrgan_path},
    };

    options.int_options = {
        {"-t", "--threads", "number of threads to use during computation (default: -1). If threads <= 0, then threads will be set to the number of CPU physical cores", &n_threads},
        {"", "--chroma-t5-mask-pad", "t5 mask pad size of chroma", &chroma_t5_mask_pad},
    };

    options.float_options = {
        {"", "--vae-tile-overlap", "tile overlap for vae tiling, in fraction of tile size (default: 0.5)", &vae_tiling_params.target_overlap},
        {"", "--flow-shift", "shift value for Flow models like SD3.x or WAN (default: auto)", &flow_shift},
    };

    options.bool_options = {
        {"", "--vae-tiling", "process vae in tiles to reduce memory usage", true, &vae_tiling_params.enabled},
        {"", "--force-sdxl-vae-conv-scale", "force use of conv scale on sdxl vae", true, &force_sdxl_vae_conv_scale},
        {"", "--mmap", "enable memory mapped file input (default: true)", true, &enable_mmap},
        {"", "--offload-to-cpu", "place the weights in RAM to save VRAM, and automatically load them into VRAM when needed", true, &offload_params_to_cpu},
        {"", "--control-net-cpu", "keep controlnet in cpu (for low vram)", true, &control_net_cpu},
        {"", "--clip-on-cpu", "keep clip in cpu (for low vram)", true, &clip_on_cpu},
        {"", "--vae-on-cpu", "keep vae in cpu (for low vram)", true, &vae_on_cpu},
        {"", "--diffusion-fa", "use flash attention in the diffusion model", true, &diffusion_flash_attn},
        {"", "--diffusion-conv-direct", "use ggml_conv2d_direct in the diffusion model", true, &diffusion_conv_direct},
        {"", "--vae-conv-direct", "use ggml_conv2d_direct in the vae model", true, &vae_conv_direct},
        {"", "--chroma-disable-dit-mask", "disable dit mask for chroma", false, &chroma_use_dit_mask},
        {"", "--chroma-enable-t5-mask", "enable t5 mask for chroma", true, &chroma_use_t5_mask},
    };

    auto on_type_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        wtype = str_to_sd_type(argv[index]);
        if (wtype == SD_TYPE_COUNT) {
            DD_LOG_ERROR("error: invalid weight format %s", argv[index]);
            return -1;
        }
        return 1;
    };

    auto on_rng_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        rng_type = str_to_rng_type(argv[index]);
        if (rng_type == RNG_TYPE_COUNT) {
            DD_LOG_ERROR("error: invalid rng type %s", argv[index]);
            return -1;
        }
        return 1;
    };

    auto on_sampler_rng_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        sampler_rng_type = str_to_rng_type(argv[index]);
        if (sampler_rng_type == RNG_TYPE_COUNT) {
            DD_LOG_ERROR("error: invalid sampler rng type %s", argv[index]);
            return -1;
        }
        return 1;
    };

    auto on_prediction_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        prediction = str_to_prediction(argv[index]);
        if (prediction == PREDICTION_COUNT) {
            DD_LOG_ERROR("error: invalid prediction type %s", argv[index]);
            return -1;
        }
        return 1;
    };

    auto on_lora_apply_mode_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        lora_apply_mode = str_to_lora_apply_mode(argv[index]);
        if (lora_apply_mode == LORA_APPLY_MODE_COUNT) {
            DD_LOG_ERROR("error: invalid lora apply model %s", argv[index]);
            return -1;
        }
        return 1;
    };

    auto on_tile_size_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        std::string tile_size_str = argv[index];
        size_t x_pos = tile_size_str.find('x');
        try {
            if (x_pos != std::string::npos) {
                vae_tiling_params.tile_size_x = std::stoi(tile_size_str.substr(0, x_pos));
                vae_tiling_params.tile_size_y = std::stoi(tile_size_str.substr(x_pos + 1));
            } else {
                vae_tiling_params.tile_size_x = vae_tiling_params.tile_size_y = std::stoi(tile_size_str);
            }
        } catch (...) { return -1; }
        return 1;
    };

    auto on_relative_tile_size_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        std::string rel_size_str = argv[index];
        size_t x_pos = rel_size_str.find('x');
        try {
            if (x_pos != std::string::npos) {
                vae_tiling_params.rel_size_x = std::stof(rel_size_str.substr(0, x_pos));
                vae_tiling_params.rel_size_y = std::stof(rel_size_str.substr(x_pos + 1));
            } else {
                vae_tiling_params.rel_size_x = vae_tiling_params.rel_size_y = std::stof(rel_size_str);
            }
        } catch (...) { return -1; }
        return 1;
    };

    options.manual_options = {
        {"", "--type", "weight type (examples: f32, f16, q4_0, q4_1, q5_0, q5_1, q8_0, q2_K, q3_K, q4_K). If not specified, the default is the type of the weight file", on_type_arg},
        {"", "--rng", "RNG, one of [std_default, cuda, cpu], default: cuda(sd-webui), cpu(comfyui)", on_rng_arg},
        {"", "--sampler-rng", "sampler RNG, one of [std_default, cuda, cpu]. If not specified, use --rng", on_sampler_rng_arg},
        {"", "--prediction", "prediction type override, one of [eps, v, edm_v, sd3_flow, flux_flow, flux2_flow]", on_prediction_arg},
        {"", "--lora-apply-mode", "the way to apply LoRA, one of [auto, immediately, at_runtime], default is auto. In auto mode, if the model weights contain any quantized parameters, the at_runtime mode will be used; otherwise, immediately will be used.", on_lora_apply_mode_arg},
        {"", "--vae-tile-size", "tile size for vae tiling, format [X]x[Y] (default: 32x32)", on_tile_size_arg},
        {"", "--vae-relative-tile-size", "relative tile size for vae tiling, format [X]x[Y], in fraction of image size if < 1, in number of tiles per dim if >=1 (overrides --vae-tile-size)", on_relative_tile_size_arg},
    };

    return options;
}

void SDContextParams::build_embedding_map() {
    static const std::vector<std::string> valid_ext = { ".pt", ".safetensors", ".gguf" };
    if (!fs::exists(embedding_dir) || !fs::is_directory(embedding_dir)) return;
    for (auto& p : fs::directory_iterator(embedding_dir)) {
        if (!p.is_regular_file()) continue;
        auto path = p.path();
        std::string ext = path.extension().string();
        bool valid = false;
        for (auto& e : valid_ext) { if (ext == e) { valid = true; break; } }
        if (!valid) continue;
        embedding_map[path.stem().string()] = path.string();
    }
}

bool SDContextParams::process_and_check(SDMode mode) {
    if (mode != UPSCALE && model_path.length() == 0 && diffusion_model_path.length() == 0) {
        DD_LOG_ERROR("error: the following arguments are required: model_path/diffusion_model\n");
        return false;
    }
    if (mode == UPSCALE && esrgan_path.length() == 0) {
        DD_LOG_ERROR("error: upscale mode needs an upscaler model (--upscale-model)\n");
        return false;
    }
    if (n_threads <= 0) n_threads = sd_get_num_physical_cores();
    build_embedding_map();
    return true;
}

std::string SDContextParams::to_string() const {
    std::ostringstream emb_ss; emb_ss << "{\n";
    for (auto it = embedding_map.begin(); it != embedding_map.end(); ++it) {
        emb_ss << "    \"" << it->first << "\": \"" << it->second << "\"" << (std::next(it) != embedding_map.end() ? "," : "") << "\n";
    }
    emb_ss << "  }";
    std::ostringstream oss;
    oss << "SDContextParams {\n  n_threads: " << n_threads << ",\n  model_path: \"" << model_path << "\",\n  embeddings: " << emb_ss.str() << "\n  wtype: " << sd_type_name(wtype) << ",\n  rng_type: " << sd_rng_type_name(rng_type) << "\n}";
    return oss.str();
}

sd_ctx_params_t SDContextParams::to_sd_ctx_params_t(bool vae_decode_only, bool free_params_immediately, bool taesd_preview) {
    embedding_vec.clear();
    embedding_vec.reserve(embedding_map.size());
    for (const auto& kv : embedding_map) {
        sd_embedding_t item; item.name = kv.first.c_str(); item.path = kv.second.c_str();
        embedding_vec.emplace_back(item);
    }
    sd_ctx_params_t params = {
        model_path.c_str(), clip_l_path.c_str(), clip_g_path.c_str(), clip_vision_path.c_str(), t5xxl_path.c_str(), llm_path.c_str(), llm_vision_path.c_str(),
        diffusion_model_path.c_str(), high_noise_diffusion_model_path.c_str(), vae_path.c_str(), taesd_path.c_str(), control_net_path.c_str(),
        embedding_vec.data(), static_cast<uint32_t>(embedding_vec.size()), photo_maker_path.c_str(), tensor_type_rules.c_str(), vae_decode_only, free_params_immediately,
        n_threads, wtype, rng_type, sampler_rng_type, prediction, lora_apply_mode, offload_params_to_cpu, enable_mmap, clip_on_cpu, control_net_cpu, vae_on_cpu,
        diffusion_flash_attn, taesd_preview, diffusion_conv_direct, vae_conv_direct, false, false, force_sdxl_vae_conv_scale, chroma_use_dit_mask,
        chroma_use_t5_mask, chroma_t5_mask_pad, qwen_image_zero_cond_t, flow_shift,
    };
    return params;
}

SDGenerationParams::SDGenerationParams() {
    sd_sample_params_init(&sample_params);
    sd_sample_params_init(&high_noise_sample_params);
    memset(&cache_params, 0, sizeof(cache_params));
    cache_params.mode = SD_CACHE_DISABLED;
}

ArgOptions SDGenerationParams::get_options() {
    ArgOptions options;
    options.string_options = {
        {"-", "--prompt", "the prompt to render", &prompt},
        {"-", "--negative-prompt", "the negative prompt (default: \"\")", &negative_prompt},
        {"-", "--init-img", "path to the init image", &init_image_path},
        {"", "--end-img", "path to the end image, required by flf2v", &end_image_path},
        {"", "--mask", "path to the mask image", &mask_image_path},
        {"", "--control-image", "path to control image, control net", &control_image_path},
        {"", "--control-video", "path to control video frames, It must be a directory path.", &control_video_path},
        {"", "--pm-id-images-dir", "path to PHOTOMAKER input id images dir", &pm_id_images_dir},
        {"", "--pm-id-embed-path", "path to PHOTOMAKER v2 id embed", &pm_id_embed_path},
    };
    options.int_options = {
        {"-", "--height", "image height (default: 512)", &height},
        {"-", "--width", "image width (default: 512)", &width},
        {"", "--steps", "number of sample steps (default: 20)", &sample_params.sample_steps},
        {"-", "--batch-count", "batch count", &batch_count},
        {"", "--upscale-repeats", "Run the ESRGAN upscaler this many times (default: 1)", &upscale_repeats},
    };
    options.float_options = {
        {"", "--cfg-scale", "unconditional guidance scale: (default: 7.0)", &sample_params.guidance.txt_cfg},
        {"", "--strength", "strength for noising/unnoising (default: 0.75)", &strength},
        {"", "--control-strength", "strength to apply Control Net (default: 0.9)", &control_strength},
    };

    auto on_seed_arg = [&](int argc, const char** argv, int index) {
        if (++index >= argc) return -1;
        seed = std::stoll(argv[index]);
        return 1;
    };

    options.manual_options = {
        {"-", "--seed", "RNG seed (default: 42, use random seed for < 0)", on_seed_arg},
    };
    return options;
}

bool SDGenerationParams::from_json_str(const std::string& json_str) {
    diffusion_desk::json j;
    try {
        j = diffusion_desk::json::parse(json_str);
    } catch (...) {
        DD_LOG_ERROR("json parse failed %s", json_str.c_str());
        return false;
    }

    auto load_if_exists = [&](const char* key, auto& out) {
        if (j.contains(key)) {
            using T = std::decay_t<decltype(out)>;
            if constexpr (std::is_same_v<T, std::string>) {
                if (j[key].is_string())
                    out = j[key];
            } else if constexpr (std::is_same_v<T, int> || std::is_same_v<T, int64_t>) {
                if (j[key].is_number_integer())
                    out = j[key];
            } else if constexpr (std::is_same_v<T, float>) {
                if (j[key].is_number())
                    out = j[key].get<float>();
            } else if constexpr (std::is_same_v<T, bool>) {
                if (j[key].is_boolean())
                    out = j[key];
            } else if constexpr (std::is_same_v<T, std::vector<int>>) {
                if (j[key].is_array())
                    out = j[key].get<std::vector<int>>();
            } else if constexpr (std::is_same_v<T, std::vector<std::string>>) {
                if (j[key].is_array())
                    out = j[key].get<std::vector<std::string>>();
            }
        }
    };

    auto load_with_alias = [&](const std::string& key, const std::vector<std::string>& aliases, auto& out) {
        if (j.contains(key)) {
            load_if_exists(key.c_str(), out);
        } else {
            for (const auto& alias : aliases) {
                if (j.contains(alias)) {
                    load_if_exists(alias.c_str(), out);
                    break;
                }
            }
        }
    };

    load_if_exists("prompt", prompt);
    load_if_exists("negative_prompt", negative_prompt);
    load_if_exists("easycache_option", easycache_option);

    load_if_exists("clip_skip", clip_skip);
    load_if_exists("width", width);
    load_if_exists("height", height);
    load_if_exists("batch_count", batch_count);
    load_if_exists("video_frames", video_frames);
    load_if_exists("fps", fps);
    load_if_exists("upscale_repeats", upscale_repeats);
    load_if_exists("seed", seed);

    load_if_exists("hires_fix", hires_fix);
    load_if_exists("hires_upscale_model", hires_upscale_model);
    load_if_exists("hires_upscale_factor", hires_upscale_factor);
    load_if_exists("hires_denoising_strength", hires_denoising_strength);
    load_if_exists("hires_steps", hires_steps);

    load_if_exists("strength", strength);
    load_if_exists("control_strength", control_strength);
    load_if_exists("pm_style_strength", pm_style_strength);
    load_if_exists("clip_on_cpu", clip_on_cpu);
    load_if_exists("moe_boundary", moe_boundary);
    load_if_exists("vace_strength", vace_strength);

    load_if_exists("auto_resize_ref_image", auto_resize_ref_image);
    load_if_exists("increase_ref_index", increase_ref_index);

    load_if_exists("skip_layers", skip_layers);
    load_if_exists("high_noise_skip_layers", high_noise_skip_layers);

    load_with_alias("cfg_scale", {"guidance_scale"}, sample_params.guidance.txt_cfg);
    load_if_exists("sample_steps", sample_params.sample_steps);
    load_if_exists("img_cfg_scale", sample_params.guidance.img_cfg);
    load_if_exists("guidance", sample_params.guidance.distilled_guidance);

    if (j.contains("sampling_method") && j["sampling_method"].is_string()) {
        std::string sm              = j["sampling_method"];
        sample_params.sample_method = str_to_sample_method(sm.c_str());
    }
    if (j.contains("scheduler") && j["scheduler"].is_string()) {
        std::string s           = j["scheduler"];
        sample_params.scheduler = str_to_scheduler(s.c_str());
    }

    return true;
}

static bool is_abs_path(const std::string& p) {
#ifdef _WIN32
    return p.size() > 1 && std::isalpha(static_cast<unsigned char>(p[0])) && p[1] == ':';
#else
    return !p.empty() && p[0] == '/';
#endif
}

void SDGenerationParams::extract_and_remove_lora(const std::string& lora_model_dir) {
    if (lora_model_dir.empty()) return;
    static const std::regex re(R"(<lora:([^:>]+):([^>]+)>)");
    std::smatch m;
    std::string tmp = prompt;
    while (std::regex_search(tmp, m, re)) {
        std::string raw_path = m[1].str();
        float mul = std::stof(m[2].str());
        fs::path final_path = is_abs_path(raw_path) ? fs::path(raw_path) : fs::path(lora_model_dir) / raw_path;
        lora_map[final_path.lexically_normal().string()] += mul;
        prompt = std::regex_replace(prompt, re, "", std::regex_constants::format_first_only);
        tmp = m.suffix().str();
    }
    for (const auto& kv : lora_map) {
        sd_lora_t item; item.is_high_noise = false; item.path = kv.first.c_str(); item.multiplier = kv.second;
        lora_vec.emplace_back(item);
    }
}

bool SDGenerationParams::process_and_check(SDMode mode, const std::string& lora_model_dir) {
    prompt_with_lora = prompt;
    if (width <= 0 || height <= 0 || sample_params.sample_steps <= 0) return false;
    if (seed < 0) { srand((int)time(nullptr)); seed = rand(); }
    extract_and_remove_lora(lora_model_dir);
    return true;
}

std::string SDGenerationParams::to_string() const {
    char* sample_str = sd_sample_params_to_str(&sample_params);
    std::ostringstream oss;
    oss << "SDGenerationParams {\n  prompt: \"" << prompt << "\",\n  sample_params: " << sample_str << ",\n  seed: " << seed << "\n}";
    free(sample_str);
    return oss.str();
}
