#include "sd_worker.hpp"
#include "sd/model_loader.hpp"
#include "sd/server_state.hpp"
#include "httplib.h"

int run_sd_worker(SDSvrParams& svr_params, SDContextParams& ctx_params, SDGenerationParams& default_gen_params) {
    LOG_INFO("Starting SD Worker on port %d...", svr_params.listen_port);

    sd_set_log_callback(sd_log_cb, (void*)&svr_params);
    sd_set_progress_callback(on_progress, &progress_state);
    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    LOG_INFO("SD Info: %s", sd_get_system_info());

    // Load config if available (e.g. for z_image_turbo)
    if (!ctx_params.model_path.empty()) {
        load_model_config(ctx_params, ctx_params.model_path, svr_params.model_dir);
    } else if (!ctx_params.diffusion_model_path.empty()) {
        load_model_config(ctx_params, ctx_params.diffusion_model_path, svr_params.model_dir);
    }

    sd_ctx_params_t sd_ctx_params_raw = ctx_params.to_sd_ctx_params_t(false, false, false);
    sd_ctx_t* sd_ctx              = nullptr;
    upscaler_ctx_t* upscaler_ctx  = nullptr;
    std::string current_upscale_model_path;
    
    if (!ctx_params.model_path.empty() || !ctx_params.diffusion_model_path.empty()) {
        sd_ctx = new_sd_ctx(&sd_ctx_params_raw);
        if (sd_ctx == nullptr) {
            LOG_ERROR("new_sd_ctx failed for initial model - starting with empty context");
            // Do not exit, allow loading another model later
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
    
    // Mount public for convenience if needed, but Orchestrator handles it usually.
    // Worker might need to serve some static files? Probably not.

    svr.Get("/internal/health", [&](const httplib::Request&, httplib::Response& res) {
        mysti::json j;
        j["ok"] = true;
        j["worker"] = "sd";
        j["vram_free_gb"] = get_free_vram_gb();
        res.set_content(j.dump(), "application/json");
    });
    
    svr.Post("/internal/shutdown", [&](const httplib::Request&, httplib::Response& res) {
        res.set_content(R"({\"status\":\"shutting_down\"})", "application/json");
        svr.stop();
    });

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

    LOG_INFO("SD Worker listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    if (sd_ctx) free_sd_ctx(sd_ctx);
    if (upscaler_ctx) free_upscaler_ctx(upscaler_ctx);
    
    return 0;
}
