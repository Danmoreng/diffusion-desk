#include "llm_worker.hpp"
#include "httplib.h"
#include <filesystem>

namespace fs = std::filesystem;

static std::string last_loaded_model_path;
static std::string last_loaded_mmproj_path;
static int last_n_gpu_layers = -1;
static int last_n_ctx = 2048;
static int last_image_max_tokens = -1;

void handle_load_llm_model(const httplib::Request& req, httplib::Response& res, SDSvrParams& svr_params, LlamaServer& llm_server) {
    try {
        mysti::json body = mysti::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        std::string mmproj_id = body.value("mmproj_id", "");
        int n_gpu_layers = body.value("n_gpu_layers", -1);
        int n_ctx = body.value("n_ctx", 2048);
        int image_max_tokens = body.value("image_max_tokens", -1);
        
        fs::path model_path = fs::path(svr_params.model_dir) / model_id;
        fs::path mmproj_path;

        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "LLM model file not found"), "application/json");
            return;
        }

        if (!mmproj_id.empty()) {
            mmproj_path = fs::path(svr_params.model_dir) / mmproj_id;
             if (!fs::exists(mmproj_path)) {
                res.status = 404;
                res.set_content(make_error_json("mmproj_not_found", "Multimodal projector file not found"), "application/json");
                return;
            }
        }

        LOG_INFO("Loading LLM model: %s (mmproj: %s, gpu_layers: %d, ctx: %d, img_max_tokens: %d)", 
                 model_path.string().c_str(), mmproj_path.string().c_str(), n_gpu_layers, n_ctx, image_max_tokens);

        if (llm_server.load_model(model_path.string(), mmproj_path.string(), n_gpu_layers, n_ctx, image_max_tokens)) {
            last_loaded_model_path = model_path.string();
            last_loaded_mmproj_path = mmproj_path.string();
            last_n_gpu_layers = n_gpu_layers;
            last_n_ctx = n_ctx;
            last_image_max_tokens = image_max_tokens;
            res.set_content(R"({\"status\":\"success\",\"model\":\")" + model_id + R"("})", "application/json");
        } else {
            res.status = 500;
            res.set_content(make_error_json("load_failed", "failed to load LLM model"), "application/json");
        }
    } catch (const std::exception& e) {
        LOG_ERROR("error loading LLM model: %s", e.what());
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}

void handle_unload_llm_model(httplib::Response& res, LlamaServer& llm_server) {
    LOG_INFO("Unloading LLM model...");
    llm_server.stop();
    res.set_content(R"({\"status\":\"success\"})", "application/json");
}

void ensure_llm_loaded(SDSvrParams& svr_params, LlamaServer& llm_server) {
    if (llm_server.is_loaded()) return;

    if (!last_loaded_model_path.empty()) {
        LOG_INFO("Auto-reloading last LLM: %s", last_loaded_model_path.c_str());
        llm_server.load_model(last_loaded_model_path, last_loaded_mmproj_path, last_n_gpu_layers, last_n_ctx, last_image_max_tokens);
        return;
    }

    if (!svr_params.default_llm_model.empty()) {
        fs::path model_path = fs::path(svr_params.model_dir) / svr_params.default_llm_model;
        if (fs::exists(model_path)) {
            LOG_INFO("Auto-loading default LLM: %s", svr_params.default_llm_model.c_str());
            llm_server.load_model(model_path.string(), "", 0, 2048, -1);
        } else {
            LOG_WARN("Default LLM model not found: %s", model_path.string().c_str());
        }
    }
}

int run_llm_worker(SDSvrParams& svr_params, SDContextParams& ctx_params) {
    LOG_INFO("Starting LLM Worker on port %d...", svr_params.listen_port);

    set_log_verbose(svr_params.verbose);
    set_log_color(svr_params.color);

    LlamaServer llm_server;
    llm_server.set_idle_timeout(svr_params.llm_idle_timeout);

    httplib::Server svr;

    // Security: Check internal token
    svr.set_pre_routing_handler([&svr_params](const httplib::Request& req, httplib::Response& res) {
        g_request_id = req.get_header_value("X-Request-ID");
        if (!svr_params.internal_token.empty()) {
            std::string token = req.get_header_value("X-Internal-Token");
            if (token != svr_params.internal_token) {
                LOG_WARN("Blocked unauthorized internal request from %s", req.remote_addr.c_str());
                res.status = 401;
                res.set_content(make_error_json("unauthorized", "Unauthorized internal request"), "application/json");
                return httplib::Server::HandlerResponse::Handled;
            }
        }
        return httplib::Server::HandlerResponse::Unhandled;
    });

    // init_server registers completions, chat, etc.
    llm_server.init_server(svr, [&svr_params, &llm_server]() {
        ensure_llm_loaded(svr_params, llm_server);
    }, nullptr);

    svr.Get("/internal/health", [&](const httplib::Request&, httplib::Response& res) {
        mysti::json j;
        j["ok"] = true;
        j["service"] = "llm";
        j["version"] = version_string();
        j["model_loaded"] = llm_server.is_loaded();
        j["vram_allocated_mb"] = (int)(get_current_process_vram_usage_gb() * 1024.0f);
        j["vram_free_mb"] = (int)(get_free_vram_gb() * 1024.0f);
        j["model_path"] = last_loaded_model_path;
        j["mmproj_path"] = last_loaded_mmproj_path;
        res.set_content(j.dump(), "application/json");
    });
    
    svr.Post("/internal/shutdown", [&](const httplib::Request&, httplib::Response& res) {
        res.set_content(R"({\"status\":\"shutting_down\"})", "application/json");
        svr.stop();
    });

    svr.Post("/v1/llm/load", [&](const httplib::Request& req, httplib::Response& res) { 
        handle_load_llm_model(req, res, svr_params, llm_server); 
    });
    
    svr.Post("/v1/llm/unload", [&](const httplib::Request& req, httplib::Response& res) { 
        handle_unload_llm_model(res, llm_server); 
    });

    svr.Post("/v1/llm/offload", [&](const httplib::Request& req, httplib::Response& res) { 
        LOG_INFO("Offloading LLM to CPU...");
        llm_server.offload_to_cpu();
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    });

    LOG_INFO("LLM Worker listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    return 0;
}