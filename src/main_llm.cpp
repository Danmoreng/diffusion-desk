#include <iostream>
#include <vector>
#include <filesystem>

#include "utils/common.hpp"
#include "workers/llm_worker.hpp"

namespace fs = std::filesystem;

void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "MystiCanvas LLM Worker v0.2\n";
    std::cout << "Usage: " << argv[0] << " [options]\n\n";
    std::cout << "Svr Options:\n";
    options_list[0].print();
    std::cout << "\nContext Options:\n";
    options_list[1].print();
    // LLM worker might not need generation params, but parse_options expects vector alignment if we reuse logic
    // For simplicity, we keep the structure consistent
}

void parse_args(int argc, const char** argv, SDSvrParams& svr_params, SDContextParams& ctx_params) {
    SDGenerationParams dummy_gen_params;
    std::vector<ArgOptions> options_vec = {svr_params.get_options(), ctx_params.get_options(), dummy_gen_params.get_options()};

    if (!parse_options(argc, argv, options_vec)) {
        print_usage(argc, argv, options_vec);
        exit(svr_params.normal_exit ? 0 : 1);
    }
    
    if (!svr_params.process_and_check()) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }
    
    if (ctx_params.n_threads <= 0) {
        ctx_params.n_threads = sd_get_num_physical_cores();
    }
}

int main(int argc, const char** argv) {
    SDSvrParams svr_params;
    SDContextParams ctx_params;
    
    parse_args(argc, argv, svr_params, ctx_params);

    svr_params.mode = "llm-worker";

    return run_llm_worker(svr_params, ctx_params);
}
