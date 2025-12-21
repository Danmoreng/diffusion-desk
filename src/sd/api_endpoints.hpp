#pragma once

#include "httplib.h"
#include <string>
#include "utils/common.hpp"
#include "server_state.hpp"
#include <mutex>

#include "server/llama_server.hpp"
#include "utils/common.hpp"

// Parameters struct for the server instance
struct SDSvrParams {
    std::string listen_ip = "127.0.0.1";
    int listen_port       = 1234;
    std::string model_dir = "./models";
    std::string output_dir = "./outputs";
    bool normal_exit      = false;
    bool verbose          = false;
    bool color            = false;

    ArgOptions get_options();
    bool process_and_check();
    std::string to_string() const;
};

// Global context required by endpoints
struct ServerContext {
    SDSvrParams& svr_params;
    SDContextParams& ctx_params;
    SDGenerationParams& default_gen_params;
    sd_ctx_t*& sd_ctx;
    std::mutex& sd_ctx_mutex;
    upscaler_ctx_t*& upscaler_ctx;
    std::string& current_upscale_model_path;
    LlamaServer& llm_server;
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

// LLM Handlers
void handle_load_llm_model(const httplib::Request& req, httplib::Response& res, ServerContext& ctx);
