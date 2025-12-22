#include "llama_server.hpp"
#include "utils/common.hpp"
#include "server-common.h"
#include "ggml-backend.h"
#include <iostream>

LlamaServer::LlamaServer() {
    common_init();
    last_access = std::chrono::steady_clock::now();
    idle_thread = std::thread(&LlamaServer::idle_check_loop, this);
}

LlamaServer::~LlamaServer() {
    {
        std::lock_guard<std::mutex> lock(state_mutex);
        running = false;
    }
    if (idle_thread.joinable()) {
        idle_thread.join();
    }
    stop();
}

bool LlamaServer::load_model(const std::string& model_path, int n_gpu_layers, int n_ctx) {
    std::lock_guard<std::mutex> lock(state_mutex);
    stop();

    llama_params = common_params();
    llama_params.model.path = model_path;

    if (n_gpu_layers >= 0) {
        llama_params.n_gpu_layers = n_gpu_layers;
    } else {
        // Use GPU for the small LLM as it should fit alongside SD
        llama_params.n_gpu_layers = 99; 
    }
    llama_params.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    llama_params.n_parallel = 1;
    llama_params.n_predict = 512;
    llama_params.warmup = false;
    llama_params.fit_params = false; // Disable fitting to prevent crashes in multi-process env
    // llama_params.fit_params_target = 4ULL * 1024 * 1024 * 1024; 

    LOG_INFO("Initializing LLM server context: ctx=%d, parallel=%d, predict=%d, gpu_layers=%d", 
             llama_params.n_ctx, llama_params.n_parallel, llama_params.n_predict, llama_params.n_gpu_layers);

    server_ctx = std::make_unique<server_context>();
    try {
        if (!server_ctx->load_model(llama_params)) {
            LOG_ERROR("Failed to load LLM model: %s", model_path.c_str());
            server_ctx.reset();
            return false;
        }
    } catch (const std::exception& e) {
        LOG_ERROR("Exception during LLM load_model: %s", e.what());
        server_ctx.reset();
        return false;
    } catch (...) {
        LOG_ERROR("Unknown exception during LLM load_model");
        server_ctx.reset();
        return false;
    }

    routes = std::make_unique<server_routes>(llama_params, *server_ctx);

    loop_thread = std::thread([this]() {
        server_ctx->start_loop();
    });

    update_last_access();
    LOG_INFO("LLM Server logic initialized with model: %s", model_path.c_str());
    return true;
}

void LlamaServer::stop() {
    // Note: state_mutex should be held by caller or we need a internal_stop
    if (server_ctx) {
        server_ctx->terminate();
        if (loop_thread.joinable()) {
            loop_thread.join();
        }
        routes.reset();
        server_ctx.reset();
        LOG_INFO("LLM Model unloaded.");
    }
}

void LlamaServer::update_last_access() {
    last_access = std::chrono::steady_clock::now();
}

void LlamaServer::idle_check_loop() {
    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(10));
        
        std::lock_guard<std::mutex> lock(state_mutex);
        if (!running) break;
        
        if (server_ctx && idle_timeout_seconds > 0) {
            auto now = std::chrono::steady_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::seconds>(now - last_access).count();
            if (duration > idle_timeout_seconds) {
                LOG_INFO("LLM idle timeout reached (%d seconds). Unloading...", idle_timeout_seconds);
                stop();
            }
        }
    }
}

void LlamaServer::bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler, std::function<void()> before_handler) {
    auto wrapper = [this, get_handler, before_handler, method, path](const httplib::Request& req, httplib::Response& res) {
        if (before_handler) {
            before_handler();
        }
        update_last_access();
        
        LOG_INFO("LLM Request: %s %s", method.c_str(), path.c_str());

        // Ensure we don't conflict with SD's GGML usage
        std::unique_ptr<std::lock_guard<std::mutex>> lock;
        if (ggml_mutex) {
            lock = std::make_unique<std::lock_guard<std::mutex>>(*ggml_mutex);
        }
        
        auto handler = get_handler(this);
        if (!handler) {
            LOG_ERROR("LLM handler not found for %s", path.c_str());
            res.status = 503;
            res.set_content(R"({\"error\":\"LLM model not loaded and no default available\"})", "application/json");
            return;
        }

        std::function<bool()> should_stop = []() { return false; };
        server_http_req llama_req = {
            {}, // params
            {}, // headers
            req.path,
            req.body,
            should_stop
        };

        // Map params
        for (auto& p : req.params) {
            llama_req.params[p.first] = p.second;
        }
        // Map headers
        for (auto& h : req.headers) {
            llama_req.headers[h.first] = h.second;
        }

        LOG_DEBUG("LLM calling native handler...");
        auto llama_res = handler(llama_req);
        if (!llama_res) {
            LOG_ERROR("LLM native handler returned null");
            res.status = 500;
            return;
        }

        LOG_INFO("LLM Response status: %d", llama_res->status);
        res.status = llama_res->status;
        for (auto& h : llama_res->headers) {
            res.set_header(h.first, h.second);
        }

        if (llama_res->is_stream()) {
            res.set_chunked_content_provider(llama_res->content_type, 
                [llama_res = std::shared_ptr<server_http_res>(std::move(llama_res))](size_t offset, httplib::DataSink &sink) {
                    std::string chunk;
                    if (llama_res->next(chunk)) {
                        sink.write(chunk.c_str(), chunk.size());
                        return true;
                    }
                    sink.done();
                    return false;
                });
        } else {
            res.set_content(llama_res->data, llama_res->content_type);
        }
    };

    if (method == "GET") {
        svr.Get(path, wrapper);
    } else if (method == "POST") {
        svr.Post(path, wrapper);
    }
}

void LlamaServer::init_server(httplib::Server& svr, std::function<void()> before_handler, std::mutex* external_mutex) {
    ggml_mutex = external_mutex;
    bridge_handler(svr, "GET",  "/v1/llm/models",        [](LlamaServer* s) { return s->routes ? s->routes->get_models : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/chat/completions",  [](LlamaServer* s) { return s->routes ? s->routes->post_chat_completions : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/completions",       [](LlamaServer* s) { return s->routes ? s->routes->post_completions : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/embeddings",        [](LlamaServer* s) { return s->routes ? s->routes->post_embeddings : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/tokenize",          [](LlamaServer* s) { return s->routes ? s->routes->post_tokenize : nullptr; }, before_handler);
    bridge_handler(svr, "POST", "/v1/detokenize",        [](LlamaServer* s) { return s->routes ? s->routes->post_detokenize : nullptr; }, before_handler);
}
