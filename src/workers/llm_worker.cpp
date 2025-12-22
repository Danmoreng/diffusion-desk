#include "llm_worker.hpp"
#include "httplib.h"
#include "sd/server_state.hpp"

int run_llm_worker(SDSvrParams& svr_params, SDContextParams& ctx_params) {
    LOG_INFO("Starting LLM Worker on port %d...", svr_params.listen_port);

    // No SD logging callback needed here, but maybe general logging
    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    // Dummy SD variables for ServerContext references
    sd_ctx_t* sd_ctx_dummy = nullptr;
    std::mutex sd_ctx_mutex_dummy;
    upscaler_ctx_t* upscaler_ctx_dummy = nullptr;
    std::string current_upscale_model_path_dummy;
    SDGenerationParams dummy_gen_params;

    LlamaServer llm_server;
    llm_server.set_idle_timeout(svr_params.llm_idle_timeout);

    ServerContext ctx = {
        svr_params,
        ctx_params,
        dummy_gen_params,
        sd_ctx_dummy,
        sd_ctx_mutex_dummy,
        upscaler_ctx_dummy,
        current_upscale_model_path_dummy,
        llm_server
    };

    httplib::Server svr;

    // NO mutex passed to init_server -> No bridging!
    llm_server.init_server(svr, [&ctx]() {
        ensure_llm_loaded(ctx);
    }, nullptr);

    svr.Get("/internal/health", [&](const httplib::Request&, httplib::Response& res) {
        mysti::json j;
        j["ok"] = true;
        j["worker"] = "llm";
        j["loaded"] = ctx.llm_server.is_loaded();
        res.set_content(j.dump(), "application/json");
    });
    
    svr.Post("/internal/shutdown", [&](const httplib::Request&, httplib::Response& res) {
        res.set_content(R"({\"status\":\"shutting_down\"})", "application/json");
        svr.stop();
    });

    svr.Post("/v1/llm/load", [&](const httplib::Request& req, httplib::Response& res) { handle_load_llm_model(req, res, ctx); });
    svr.Post("/v1/llm/unload", [&](const httplib::Request& req, httplib::Response& res) { handle_unload_llm_model(req, res, ctx); });

    LOG_INFO("LLM Worker listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    return 0;
}
