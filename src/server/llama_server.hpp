#pragma once

#include "server-context.h"
#include "httplib.h"
#include "utils/common.hpp"
#include <memory>
#include <string>
#include <thread>
#include <mutex>

class LlamaServer {
public:
    LlamaServer();
    ~LlamaServer();

    bool load_model(const std::string& model_path, int n_gpu_layers = -1);
    void stop();

    void init_server(httplib::Server& svr);

    bool is_loaded() const { return !!server_ctx; }

private:
    std::unique_ptr<server_context> server_ctx;
    std::unique_ptr<server_routes> routes;
    std::thread loop_thread;
    common_params llama_params;

    void bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler);
};
