#include <chrono>
#include <filesystem>
#include <iostream>
#include <vector>

#include "utils/common.hpp"
#include "sd/api_endpoints.hpp"
#include "sd/model_loader.hpp"
#include "orchestrator/orchestrator_main.hpp"
#include "workers/sd_worker.hpp"
#include "workers/llm_worker.hpp"

namespace fs = std::filesystem;

void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "MystiCanvas Server v0.2\n";
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
    
    // In Orchestrator mode, model paths might not be set yet, that's fine.
    // In Worker modes, validation is stricter if needed, but handled inside worker if model load is attempted.
    
    // Legacy check removal: We don't force model path at startup anymore because we have model loading API.
    // But if provided, we load it.

    if (has_model) {
        std::string active_path = ctx_params.diffusion_model_path.empty() ? ctx_params.model_path : ctx_params.diffusion_model_path;
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

    if (svr_params.mode == "sd-worker") {
        return run_sd_worker(svr_params, ctx_params, default_gen_params);
    } else if (svr_params.mode == "llm-worker") {
        return run_llm_worker(svr_params, ctx_params);
    } else {
        // Default: Orchestrator
        return run_orchestrator(argc, argv, svr_params);
    }
}
