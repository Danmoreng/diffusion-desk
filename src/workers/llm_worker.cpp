#include "llm_worker.hpp"
#include "httplib.h"
#include <algorithm>
#include <cctype>
#include <filesystem>
#include <vector>

namespace fs = std::filesystem;

static std::string last_loaded_model_path;
static std::string last_loaded_mmproj_path;
static int last_n_gpu_layers = -1;
static int last_n_ctx = -1;
static int last_image_max_tokens = -1;
static std::vector<std::string> last_advanced_args;
static bool explicit_unload_requested = false;

static std::string lowercase_copy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return (char)std::tolower(c);
    });
    return value;
}

static fs::path resolve_child_case_insensitive(const fs::path& parent, const fs::path& child) {
    fs::path exact = parent / child;
    if (fs::exists(exact)) return exact;
    if (!fs::exists(parent) || !fs::is_directory(parent)) return exact;

    const std::string requested = lowercase_copy(child.filename().string());
    for (const auto& entry : fs::directory_iterator(parent)) {
        if (lowercase_copy(entry.path().filename().string()) == requested) {
            return entry.path();
        }
    }
    return exact;
}

static fs::path resolve_model_path_case_insensitive(const std::string& path, const std::string& model_dir) {
    fs::path fp(path);
    if (fp.is_absolute()) return fp;

    fs::path resolved(model_dir);
    for (const auto& part : fp) {
        if (part.empty() || part == ".") continue;
        if (part == "..") {
            resolved /= part;
        } else {
            resolved = resolve_child_case_insensitive(resolved, part);
        }
    }
    return resolved;
}

void handle_load_llm_model(const httplib::Request& req, httplib::Response& res, SDSvrParams& svr_params, LlamaServer& llm_server) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        if (!body.contains("model_id")) {
            res.status = 400;
            res.set_content(make_error_json("invalid_request", "model_id required"), "application/json");
            return;
        }
        std::string model_id = body["model_id"];
        std::string mmproj_id = body.value("mmproj_id", "");
        int n_gpu_layers = body.value("n_gpu_layers", -1);
        int n_ctx = body.value("n_ctx", -1);
        int image_max_tokens = body.value("image_max_tokens", -1);
        std::vector<std::string> advanced_args;
        if (body.contains("advanced_args") && body["advanced_args"].is_array()) {
            for (const auto& item : body["advanced_args"]) {
                if (item.is_string()) {
                    advanced_args.push_back(item.get<std::string>());
                }
            }
        }
        
        fs::path model_path = resolve_model_path_case_insensitive(model_id, svr_params.model_dir);
        fs::path mmproj_path;

        if (!fs::exists(model_path)) {
            res.status = 404;
            res.set_content(make_error_json("model_not_found", "LLM model file not found"), "application/json");
            return;
        }

        if (!mmproj_id.empty()) {
            mmproj_path = resolve_model_path_case_insensitive(mmproj_id, svr_params.model_dir);
             if (!fs::exists(mmproj_path)) {
                res.status = 404;
                res.set_content(make_error_json("mmproj_not_found", "Multimodal projector file not found"), "application/json");
                return;
            }
        }

        DD_LOG_INFO("Loading LLM model: %s (mmproj: %s, gpu_layers: %d, ctx: %d, img_max_tokens: %d)", 
                 model_path.string().c_str(), mmproj_path.string().c_str(), n_gpu_layers, n_ctx, image_max_tokens);

        if (llm_server.load_model(model_path.string(), mmproj_path.string(), n_gpu_layers, n_ctx, image_max_tokens, advanced_args)) {
            last_loaded_model_path = model_path.string();
            last_loaded_mmproj_path = mmproj_path.string();
            last_n_gpu_layers = n_gpu_layers;
            last_n_ctx = n_ctx;
            last_image_max_tokens = image_max_tokens;
            last_advanced_args = advanced_args;
            explicit_unload_requested = false;
            diffusion_desk::json out;
            out["status"] = "success";
            out["model"] = model_id;
            res.set_content(out.dump(), "application/json");
        } else {
            res.status = 500;
            res.set_content(make_error_json("load_failed", "failed to load LLM model"), "application/json");
        }
    } catch (const std::exception& e) {
        DD_LOG_ERROR("error loading LLM model: %s", e.what());
        res.status = 500;
        res.set_content(make_error_json("server_error", e.what()), "application/json");
    }
}

void handle_unload_llm_model(httplib::Response& res, LlamaServer& llm_server) {
    DD_LOG_INFO("Unloading LLM model...");
    llm_server.stop();
    last_loaded_model_path.clear();
    last_loaded_mmproj_path.clear();
    last_n_gpu_layers = -1;
    last_n_ctx = -1;
    last_image_max_tokens = -1;
    last_advanced_args.clear();
    explicit_unload_requested = true;
    res.set_content(R"({"status":"success"})", "application/json");
}

void handle_post_config(const httplib::Request& req, httplib::Response& res, SDSvrParams& svr_params) {
    try {
        diffusion_desk::json body = diffusion_desk::json::parse(req.body);
        if (body.contains("model_dir")) {
            svr_params.model_dir = body["model_dir"];
            DD_LOG_INFO("Config updated: model_dir = %s", svr_params.model_dir.c_str());
        }
        res.set_content(R"({"status":"success"})", "application/json");
    } catch (const std::exception& e) {
        res.status = 400;
        res.set_content(make_error_json("invalid_json", e.what()), "application/json");
    }
}

void ensure_llm_loaded(SDSvrParams& svr_params, LlamaServer& llm_server) {
    if (llm_server.is_loaded()) return;
    if (explicit_unload_requested) return;

    if (!last_loaded_model_path.empty()) {
        DD_LOG_INFO("Auto-reloading last LLM: %s", last_loaded_model_path.c_str());
        llm_server.load_model(last_loaded_model_path, last_loaded_mmproj_path, last_n_gpu_layers, last_n_ctx, last_image_max_tokens, last_advanced_args);
        return;
    }

    if (!svr_params.default_llm_model.empty()) {
        fs::path model_path = resolve_model_path_case_insensitive(svr_params.default_llm_model, svr_params.model_dir);
        fs::path mmproj_path;
        if (!svr_params.default_mmproj_model.empty()) {
            mmproj_path = resolve_model_path_case_insensitive(svr_params.default_mmproj_model, svr_params.model_dir);
        }

        if (fs::exists(model_path)) {
            DD_LOG_INFO("Auto-loading default LLM: %s (mmproj: %s)", svr_params.default_llm_model.c_str(), mmproj_path.string().c_str());
            llm_server.load_model(model_path.string(), mmproj_path.string(), 0, -1, -1);
        } else {
            DD_LOG_WARN("Default LLM model not found: %s", model_path.string().c_str());
        }
    }
}

int run_llm_worker(SDSvrParams& svr_params, LLMContextParams& ctx_params) {
    DD_LOG_INFO("Starting LLM Worker on port %d...", svr_params.listen_port);

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
                DD_LOG_WARN("Blocked unauthorized internal request from %s", req.remote_addr.c_str());
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
        diffusion_desk::json j;
        j["ok"] = true;
        j["service"] = "llm";
        j["version"] = version_string();
        j["model_loaded"] = llm_server.is_loaded();
        j["vram_allocated_mb"] = (int)(get_current_process_vram_usage_gb() * 1024.0f);
        j["vram_free_mb"] = (int)(get_free_vram_gb() * 1024.0f);
        j["model_path"] = last_loaded_model_path;
        j["mmproj_path"] = last_loaded_mmproj_path;
        j["n_gpu_layers"] = llm_server.is_loaded() ? llm_server.effective_gpu_layers() : last_n_gpu_layers;
        j["placement"] = (j["n_gpu_layers"].get<int>() == 0) ? "cpu" : "gpu";
        j["advanced_args"] = last_advanced_args;
        res.set_content(j.dump(), "application/json");
    });
    
    svr.Post("/internal/shutdown", [&](const httplib::Request&, httplib::Response& res) {
        res.set_content(R"({"status":"shutting_down"})", "application/json");
        svr.stop();
    });

    svr.Post("/internal/config", [&](const httplib::Request& req, httplib::Response& res) {
        handle_post_config(req, res, svr_params);
    });

    svr.Post("/v1/llm/load", [&](const httplib::Request& req, httplib::Response& res) { 
        handle_load_llm_model(req, res, svr_params, llm_server); 
    });
    
    svr.Post("/v1/llm/unload", [&](const httplib::Request& req, httplib::Response& res) { 
        handle_unload_llm_model(res, llm_server); 
    });

    svr.Post("/v1/llm/offload", [&](const httplib::Request& req, httplib::Response& res) { 
        DD_LOG_INFO("Offloading LLM to CPU...");
        llm_server.offload_to_cpu();
        res.set_content(R"({"status":"success"})", "application/json");
    });

    DD_LOG_INFO("LLM Worker listening on: %s:%d\n", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    return 0;
}
