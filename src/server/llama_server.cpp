#include "llama_server.hpp"
#include "utils/common.hpp"
#include "server-common.h"
#include <iostream>

LlamaServer::LlamaServer() {
    common_init();
}

LlamaServer::~LlamaServer() {
    stop();
}

bool LlamaServer::load_model(const std::string& model_path, int n_gpu_layers) {
    stop();

    llama_params = common_params();
    llama_params.model.path = model_path;
    if (n_gpu_layers >= 0) {
        llama_params.n_gpu_layers = n_gpu_layers;
    } else {
        llama_params.n_gpu_layers = 99;
    }
    llama_params.n_ctx = 2048;
    llama_params.n_parallel = 1;

    server_ctx = std::make_unique<server_context>();
    if (!server_ctx->load_model(llama_params)) {
        LOG_ERROR("Failed to load LLM model: %s", model_path.c_str());
        server_ctx.reset();
        return false;
    }

    routes = std::make_unique<server_routes>(llama_params, *server_ctx);

    loop_thread = std::thread([this]() {
        server_ctx->start_loop();
    });

    LOG_INFO("LLM Server logic initialized with model: %s", model_path.c_str());
    return true;
}

void LlamaServer::stop() {
    if (server_ctx) {
        server_ctx->terminate();
        if (loop_thread.joinable()) {
            loop_thread.join();
        }
        routes.reset();
        server_ctx.reset();
    }
}

void LlamaServer::bridge_handler(httplib::Server& svr, const std::string& method, const std::string& path, std::function<server_http_context::handler_t(LlamaServer*)> get_handler) {
    auto wrapper = [this, get_handler](const httplib::Request& req, httplib::Response& res) {
        auto handler = get_handler(this);
        if (!handler) {
            res.status = 503;
            res.set_content(R"({\"error\":\"LLM model not loaded\"})", "application/json");
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

        auto llama_res = handler(llama_req);
        if (!llama_res) {
            res.status = 500;
            return;
        }

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

void LlamaServer::init_server(httplib::Server& svr) {
    bridge_handler(svr, "GET",  "/v1/llm/models",        [](LlamaServer* s) { return s->routes ? s->routes->get_models : nullptr; });
    bridge_handler(svr, "POST", "/v1/chat/completions",  [](LlamaServer* s) { return s->routes ? s->routes->post_chat_completions : nullptr; });
    bridge_handler(svr, "POST", "/v1/completions",       [](LlamaServer* s) { return s->routes ? s->routes->post_completions : nullptr; });
    bridge_handler(svr, "POST", "/v1/embeddings",        [](LlamaServer* s) { return s->routes ? s->routes->post_embeddings : nullptr; });
    bridge_handler(svr, "POST", "/v1/tokenize",          [](LlamaServer* s) { return s->routes ? s->routes->post_tokenize : nullptr; });
    bridge_handler(svr, "POST", "/v1/detokenize",        [](LlamaServer* s) { return s->routes ? s->routes->post_detokenize : nullptr; });
}
