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

    bool load_model(const std::string& model_path, const std::string& mmproj_path = "", int n_gpu_layers = -1, int n_ctx = 2048, int image_max_tokens = -1);
    void offload_to_cpu();
    void stop();

    void init_server(httplib::Server& svr, std::function<void()> before_handler = nullptr, std::mutex* external_mutex = nullptr);

    bool is_loaded() const { return !!server_ctx; }
    void set_idle_timeout(int seconds) { idle_timeout_seconds = seconds; }

private:
    std::unique_ptr<server_context> server_ctx;
    std::unique_ptr<server_routes> routes;
    std::thread loop_thread;
    std::thread idle_thread;
    std::recursive_mutex state_mutex;
    std::mutex* ggml_mutex = nullptr;
    common_params llama_params;

    std::chrono::steady_clock::time_point last_access;
    int idle_timeout_seconds = 300;
    bool running = true;

    void update_last_access();
    void idle_check_loop();
    void bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler, std::function<void()> before_handler = nullptr);
};
