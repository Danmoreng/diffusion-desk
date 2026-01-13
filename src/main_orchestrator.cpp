#include <iostream>
#include <vector>

#include "utils/sd_common.hpp"
#include "orchestrator/orchestrator_main.hpp"

// Forward declaration of print_usage (or implement locally)
void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "DiffusionDesk Orchestrator v0.2\n";
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
    
    // Additional validation if needed, but Orchestrator mainly passes args through
    if (!svr_params.process_and_check()) {
        print_usage(argc, argv, options_vec);
        exit(1);
    }
}

int main(int argc, const char** argv) {
    SDSvrParams svr_params;
    SDContextParams ctx_params;
    SDGenerationParams default_gen_params;
    
    // 1. Try to load config.json from various locations
    std::vector<fs::path> config_search_paths = {
        "config.json",
        fs::path(argv[0]).parent_path() / "config.json"
    };

#ifdef _WIN32
    char* appdata = getenv("APPDATA");
    if (appdata) {
        config_search_paths.push_back(fs::path(appdata) / "DiffusionDesk" / "config.json");
    }
#endif

    for (const auto& p : config_search_paths) {
        if (fs::exists(p)) {
            DD_LOG_INFO("Loading config from %s", p.string().c_str());
            if (svr_params.load_from_file(p.string())) {
                break;
            }
        }
    }

    // 2. Parse args primarily to get listen_ip, listen_port, model_dir, etc.
    // Command line args override config file
    parse_args(argc, argv, svr_params, ctx_params, default_gen_params);

    if (svr_params.internal_token.empty()) {
        svr_params.internal_token = generate_random_token();
        DD_LOG_INFO("Generated transient internal token for worker security.");
    }
    
    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    // Run the orchestrator logic
    return run_orchestrator(argc, argv, svr_params);
}
