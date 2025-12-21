#include <chrono>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <sstream>
#include <vector>

#include "httplib.h"
#include "stable-diffusion.h"
#include "llama.h"

#include "utils/common.hpp"
#include "sd/api_utils.hpp"
#include "sd/server_state.hpp"
#include "sd/model_loader.hpp"
#include "sd/api_endpoints.hpp"

namespace fs = std::filesystem;

void print_usage(int argc, const char* argv[], const std::vector<ArgOptions>& options_list) {
    std::cout << "MystiCanvas Server v0.1\n";
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

    if (!has_model && svr_params.model_dir.empty()) {
        LOG_ERROR("error: either --model/--diffusion-model or --model-dir must be specified");
        exit(1);
    }

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

void sd_log_cb(enum sd_log_level_t level, const char* log, void* data) {
    SDSvrParams* svr_params = (SDSvrParams*)data;
    log_print(level, log, svr_params->verbose, svr_params->color);
}

int main(int argc, const char** argv) {
    SDSvrParams svr_params;
    SDContextParams ctx_params;
    SDGenerationParams default_gen_params;
    parse_args(argc, argv, svr_params, ctx_params, default_gen_params);

    sd_set_log_callback(sd_log_cb, (void*)&svr_params);
    sd_set_progress_callback(on_progress, &progress_state);
    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    LOG_INFO("MystiCanvas Server Starting...");
    LOG_INFO("SD Info: %s", sd_get_system_info());
    // LOG_INFO("LLM Info: %s", llama_print_system_info());

    sd_ctx_params_t sd_ctx_params_raw = ctx_params.to_sd_ctx_params_t(false, false, false);
    sd_ctx_t* sd_ctx              = nullptr;
    upscaler_ctx_t* upscaler_ctx  = nullptr;
    std::string current_upscale_model_path;
    
    if (!ctx_params.model_path.empty() || !ctx_params.diffusion_model_path.empty()) {
        sd_ctx = new_sd_ctx(&sd_ctx_params_raw);
        if (sd_ctx == nullptr) {
            LOG_ERROR("new_sd_ctx failed for initial model");
            return 1;
        }
    }

    std::mutex sd_ctx_mutex;

    ServerContext ctx = {
        svr_params,
        ctx_params,
        default_gen_params,
        sd_ctx,
        sd_ctx_mutex,
        upscaler_ctx,
        current_upscale_model_path
    };

    httplib::Server svr;

    svr.Get(R"(/outputs/(.*))", [&](const httplib::Request& req, httplib::Response& res) {
        handle_get_outputs(req, res, ctx);
    });

    if (!svr.set_mount_point("/", "./public")) {
        LOG_WARN("failed to mount ./public directory");
    }

    svr.set_pre_routing_handler([](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Access-Control-Allow-Methods", "*");
        res.set_header("Access-Control-Allow-Headers", "*");
        if (req.method == "OPTIONS") {
            res.status = 204;
            return httplib::Server::HandlerResponse::Handled;
        }
        return httplib::Server::HandlerResponse::Unhandled;
    });

    svr.Get("/health", handle_health);
    svr.Get("/v1/config", [&](const httplib::Request& req, httplib::Response& res) { handle_get_config(req, res, ctx); });
    svr.Post("/v1/config", [&](const httplib::Request& req, httplib::Response& res) { handle_post_config(req, res, ctx); });
    svr.Get("/v1/progress", handle_get_progress);
    svr.Get("/v1/stream/progress", handle_stream_progress);
    svr.Get("/v1/models", [&](const httplib::Request& req, httplib::Response& res) { handle_get_models(req, res, ctx); });
    svr.Post("/v1/models/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_model(req, res, ctx); });
    svr.Post("/v1/upscale/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_upscale_model(req, res, ctx); });
    svr.Post("/v1/images/upscale", [&](const httplib::Request& req, httplib::Response& res) { handle_upscale_image(req, res, ctx); });
    svr.Get("/v1/history/images", [&](const httplib::Request& req, httplib::Response& res) { handle_get_history(req, res, ctx); });
    svr.Post("/v1/images/generations", [&](const httplib::Request& req, httplib::Response& res) { handle_generate_image(req, res, ctx); });
    svr.Post("/v1/images/edits", [&](const httplib::Request& req, httplib::Response& res) { handle_edit_image(req, res, ctx); });

    LOG_INFO("listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    if (sd_ctx) free_sd_ctx(sd_ctx);
    if (upscaler_ctx) free_upscaler_ctx(upscaler_ctx);
    
    return 0;
}