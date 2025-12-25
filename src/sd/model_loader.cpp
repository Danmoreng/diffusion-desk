#include "model_loader.hpp"
#include <filesystem>
#include <fstream>
#include <json.hpp>

using json = mysti::json;
namespace fs = std::filesystem;

void load_model_config(SDContextParams& ctx_params, const std::string& model_path_str, const std::string& model_dir) {
    if (model_path_str.empty()) return;

    fs::path model_path(model_path_str);
    fs::path config_path;

    if (model_path.is_absolute()) {
        config_path = model_path;
    } else {
        config_path = fs::path(model_dir) / model_path;
    }
    config_path += ".json";

    if (fs::exists(config_path)) {
        LOG_INFO("Loading model config: %s", config_path.string().c_str());
        try {
            std::ifstream f(config_path);
            json cfg = json::parse(f);

            auto resolve = [&](const std::string& p) -> std::string {
                if (p.empty()) return "";
                fs::path fp(p);
                if (fp.is_absolute()) return p;
                return (fs::path(model_dir) / p).string();
            };

            if (cfg.contains("vae")) ctx_params.vae_path = resolve(cfg["vae"]);
            else if (cfg.contains("vae_path")) ctx_params.vae_path = resolve(cfg["vae_path"]);
            else if (cfg.contains("ae")) ctx_params.vae_path = resolve(cfg["ae"]);

            if (cfg.contains("clip_l")) ctx_params.clip_l_path = resolve(cfg["clip_l"]);
            else if (cfg.contains("clip_l_path")) ctx_params.clip_l_path = resolve(cfg["clip_l_path"]);
            else if (cfg.contains("clip_path")) ctx_params.clip_l_path = resolve(cfg["clip_path"]);

            if (cfg.contains("clip_g")) ctx_params.clip_g_path = resolve(cfg["clip_g"]);
            else if (cfg.contains("clip_g_path")) ctx_params.clip_g_path = resolve(cfg["clip_g_path"]);

            if (cfg.contains("t5xxl")) ctx_params.t5xxl_path = resolve(cfg["t5xxl"]);
            else if (cfg.contains("t5xxl_path")) ctx_params.t5xxl_path = resolve(cfg["t5xxl_path"]);

            if (cfg.contains("llm")) ctx_params.llm_path = resolve(cfg["llm"]);
            else if (cfg.contains("llm_path")) ctx_params.llm_path = resolve(cfg["llm_path"]);

            if (cfg.contains("clip_on_cpu")) ctx_params.clip_on_cpu = cfg["clip_on_cpu"];
            if (cfg.contains("vae_on_cpu")) ctx_params.vae_on_cpu = cfg["vae_on_cpu"];
            if (cfg.contains("offload_to_cpu")) ctx_params.offload_params_to_cpu = cfg["offload_to_cpu"];
            if (cfg.contains("flash_attn")) ctx_params.diffusion_flash_attn = cfg["flash_attn"];
            
            // Default VAE tiling to true if using CUDA to prevent OOM on large images
            ctx_params.vae_tiling_params.enabled = true;
            if (cfg.contains("vae_tiling")) ctx_params.vae_tiling_params.enabled = cfg["vae_tiling"];

            if (cfg.contains("prediction")) {
                std::string pred = cfg["prediction"];
                ctx_params.prediction = str_to_prediction(pred.c_str());
                LOG_INFO("Config: forced prediction type: %s (%d)", pred.c_str(), ctx_params.prediction);
            }
            if (cfg.contains("flow_shift")) {
                ctx_params.flow_shift = cfg["flow_shift"];
                LOG_INFO("Config: forced flow_shift: %.3f", ctx_params.flow_shift);
            }
            if (cfg.contains("scale_factor")) {
                ctx_params.scale_factor = cfg["scale_factor"];
                LOG_INFO("Config: forced scale_factor: %.3f", ctx_params.scale_factor);
            }
            if (cfg.contains("shift_factor")) {
                ctx_params.shift_factor = cfg["shift_factor"];
                LOG_INFO("Config: forced shift_factor: %.3f", ctx_params.shift_factor);
            }

            LOG_INFO("Config applied: vae=%s, clip_l=%s, t5=%s, llm=%s, clip_on_cpu=%s, flash_attn=%s",
                     ctx_params.vae_path.c_str(),
                     ctx_params.clip_l_path.c_str(),
                     ctx_params.t5xxl_path.c_str(),
                     ctx_params.llm_path.c_str(),
                     ctx_params.clip_on_cpu ? "true" : "false",
                     ctx_params.diffusion_flash_attn ? "true" : "false");

        } catch (const std::exception& e) {
            LOG_WARN("Failed to parse model config: %s", e.what());
        }
    }
}
