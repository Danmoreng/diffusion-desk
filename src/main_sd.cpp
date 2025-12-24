#include <iostream>
#include <vector>
#include <filesystem>

#include "utils/common.hpp"
#include "sd/model_loader.hpp"
#include "workers/sd_worker.hpp"

namespace fs = std::filesystem;

void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "MystiCanvas SD Worker v0.2\n";
    std::cout << "Usage: " << argv[0] << " [options]\n\n";
    std::cout << "Svr Options:\n";
    options_list[0].print();
    std::cout << "\nContext Options:\n";
    options_list[1].print();
    std::cout << "\nDefault Generation Options:\n";
    options_list[2].print();
}

void parse_args(int argc, const char** argv, SDSvrParams& svr_params, SDContextParams& ctx_params, SDGenerationParams& default_gen_params) {
    std::vector<ArgOptions> options_vec = {svr_params.get_options(), ctx_params.get_options(), default_gen_params.get_options()};

    if (!parse_options(argc, argv, options_vec)) {
        print_usage(argc, argv, options_vec);
        exit(svr_params.normal_exit ? 0 : 1);
    }

    bool has_model = (ctx_params.model_path.length() > 0 || ctx_params.diffusion_model_path.length() > 0);
    
    if (!svr_params.process_and_check()) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }

    if (has_model) {
        std::string active_path = ctx_params.diffusion_model_path.empty() ? ctx_params.model_path : ctx_params.diffusion_model_path;
        
        fs::path p(active_path);
        if (!p.is_absolute() && !fs::exists(p)) {
            fs::path candidate = fs::path(svr_params.model_dir) / p;
            if (fs::exists(candidate)) {
                active_path = candidate.string();
                if (ctx_params.diffusion_model_path.empty()) ctx_params.model_path = active_path;
                else ctx_params.diffusion_model_path = active_path;
            }
        }

        // Smart fallback: If we only have model_path but it looks like it should be a diffusion_model_path (GGUF),
        // and we aren't in upscale mode, move it to diffusion_model_path to avoid "get version failed" errors.
        if (ctx_params.diffusion_model_path.empty() && !ctx_params.model_path.empty()) {
            std::string ext = fs::path(ctx_params.model_path).extension().string();
            if (ext == ".gguf") {
                LOG_INFO("Smart fallback: Moving GGUF from model_path to diffusion_model_path");
                ctx_params.diffusion_model_path = ctx_params.model_path;
                ctx_params.model_path = "";
                active_path = ctx_params.diffusion_model_path;
            }
        }
        
        load_model_config(ctx_params, active_path, svr_params.model_dir);
    }

    if (ctx_params.n_threads <= 0) {
        ctx_params.n_threads = sd_get_num_physical_cores();
    }

    if (!default_gen_params.process_and_check(IMG_GEN, ctx_params.lora_model_dir)) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }
}

int main(int argc, const char** argv) {
    SDSvrParams svr_params;
    SDContextParams ctx_params;
    SDGenerationParams default_gen_params;
    
    parse_args(argc, argv, svr_params, ctx_params, default_gen_params);

    // Force mode just in case, though it should be passed via args or ignored
    svr_params.mode = "sd-worker";

    return run_sd_worker(svr_params, ctx_params, default_gen_params);
}
