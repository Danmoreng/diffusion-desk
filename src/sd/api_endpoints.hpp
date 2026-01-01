#pragma once

#include "httplib.h"
#include <string>
#include "utils/common.hpp"
#include "server_state.hpp"
#include <mutex>

// Forward declaration if needed, but workers usually handle their own types
class LlamaServer; 

// Global context required by endpoints
struct ServerContext {
    SDSvrParams& svr_params;
    SDContextParams& ctx_params;
    SDGenerationParams& default_gen_params;
    SdCtxPtr& sd_ctx;
    std::mutex& sd_ctx_mutex;
    UpscalerCtxPtr& upscaler_ctx;
    std::string& current_upscale_model_path;
    std::string active_llm_model_path;
    bool active_llm_model_loaded = false;
    bool was_using_loras = false; // Workaround for stable-diffusion.cpp LoRA persistence bug
    
    // B2.5: Idle timeout tracking
    std::chrono::steady_clock::time_point last_access = std::chrono::steady_clock::now();
    void update_last_access() { last_access = std::chrono::steady_clock::now(); }
};

// Endpoint handlers
void handle_get_outputs(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_health(const httplib::Request& req, httplib::Response& res);
void handle_get_config(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_post_config(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_get_progress(const httplib::Request& req, httplib::Response& res);
void handle_stream_progress(const httplib::Request& req, httplib::Response& res);
void handle_get_models(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_load_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_load_upscale_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_upscale_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_get_history(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_generate_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
void handle_edit_image(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
