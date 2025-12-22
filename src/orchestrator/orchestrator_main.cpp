#include "orchestrator_main.hpp"
#include "process_manager.hpp"
#include "proxy.hpp"
#include "httplib.h"
#include <iostream>
#include <thread>
#include <chrono>

static ProcessManager pm;
static ProcessManager::ProcessInfo sd_process;
static ProcessManager::ProcessInfo llm_process;
static int sd_port = 0;
static int llm_port = 0;

void signal_handler(int sig) {
    std::cout << "Orchestrator shutting down...\n";
    if (pm.is_running(sd_process)) pm.terminate(sd_process);
    if (pm.is_running(llm_process)) pm.terminate(llm_process);
    exit(0);
}

std::string get_executable_path(const char* argv0) {
    // Basic implementation, assuming argv[0] is sufficient or in current dir
    return argv0;
}

void health_check_loop(const std::string& sd_host, int sd_p, const std::string& llm_host, int llm_p) {
    httplib::Client sd_cli(sd_host, sd_p);
    httplib::Client llm_cli(llm_host, llm_p);
    sd_cli.set_connection_timeout(1);
    llm_cli.set_connection_timeout(1);

    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(5));
        // Check SD
        if (auto res = sd_cli.Get("/internal/health")) {
             // OK
        } else {
             // TODO: Restart logic if needed
             // std::cerr << "SD Worker Unhealthy\n";
        }
        
        // Check LLM
        if (auto res = llm_cli.Get("/internal/health")) {
             // OK
        } else {
             // TODO: Restart logic if needed
             // std::cerr << "LLM Worker Unhealthy\n";
        }
    }
}

int run_orchestrator(int argc, const char** argv, SDSvrParams& svr_params) {
    LOG_INFO("Starting Orchestrator on port %d...", svr_params.listen_port);
    
    // 1. Determine Ports
    sd_port = svr_params.listen_port + 1;
    llm_port = svr_params.listen_port + 2;
    
    // 2. Spawn Workers
    std::string exe_path = get_executable_path(argv[0]);
    std::vector<std::string> base_args;
    
    // Reconstruct base args from argv, excluding --mode and listen params
    // Actually, simpler is to just pass everything and override mode/port
    
    // We need to construct the command line.
    // We will assume that the same arguments used to start orchestrator 
    // should be passed to workers, EXCEPT mode and port.
    
    std::vector<std::string> common_args;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--mode" || arg == "-l" || arg == "--listen-ip" || arg == "--listen-port") {
            // Skip next val
            i++; 
            continue;
        }
        // Also skip values if they were attached (not supported by common ArgOptions yet but safe to assume space separated)
        common_args.push_back(arg);
    }

    // Spawn SD Worker
    std::vector<std::string> sd_args = common_args;
    sd_args.push_back("--mode"); sd_args.push_back("sd-worker");
    sd_args.push_back("--listen-port"); sd_args.push_back(std::to_string(sd_port));
    sd_args.push_back("--listen-ip"); sd_args.push_back("127.0.0.1");
    
    LOG_INFO("Spawning SD Worker on port %d", sd_port);
    if (!pm.spawn(exe_path, sd_args, sd_process)) {
        LOG_ERROR("Failed to spawn SD Worker");
        return 1;
    }

    // Spawn LLM Worker
    std::vector<std::string> llm_args = common_args;
    llm_args.push_back("--mode"); llm_args.push_back("llm-worker");
    llm_args.push_back("--listen-port"); llm_args.push_back(std::to_string(llm_port));
    llm_args.push_back("--listen-ip"); llm_args.push_back("127.0.0.1");

    LOG_INFO("Spawning LLM Worker on port %d", llm_port);
    if (!pm.spawn(exe_path, llm_args, llm_process)) {
        LOG_ERROR("Failed to spawn LLM Worker");
        pm.terminate(sd_process);
        return 1;
    }

    // Auto-load LLM model if specified
    std::string preload_llm_model;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if ((arg == "--llm-model" || arg == "-lm") && i + 1 < argc) {
            preload_llm_model = argv[i + 1];
            break;
        }
    }

    if (!preload_llm_model.empty()) {
        std::thread([preload_llm_model]() {
            LOG_INFO("Waiting for LLM worker to be ready to load model: %s", preload_llm_model.c_str());
            httplib::Client cli("127.0.0.1", llm_port);
            cli.set_connection_timeout(2);
            
            // Wait for health
            for (int i = 0; i < 30; ++i) {
                if (auto res = cli.Get("/internal/health")) {
                    if (res->status == 200) break;
                }
                std::this_thread::sleep_for(std::chrono::seconds(1));
            }

            // Load model
            mysti::json body;
            body["model_id"] = preload_llm_model;
            auto res = cli.Post("/v1/llm/load", body.dump(), "application/json");
            if (res && res->status == 200) {
                LOG_INFO("Successfully pre-loaded LLM model: %s", preload_llm_model.c_str());
            } else {
                LOG_ERROR("Failed to pre-load LLM model: %s", preload_llm_model.c_str());
            }
        }).detach();
    }

    // 3. Start Proxy Server
    httplib::Server svr;

    if (!svr.set_mount_point("/", "./public")) {
        LOG_WARN("failed to mount ./public directory");
    }

    // CORS
    svr.set_pre_routing_handler([](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        res.set_header("Access-Control-Allow-Methods", "*");
        res.set_header("Access-Control-Allow-Headers", "*");
        if (req.method == "OPTIONS") {
            res.status = 204;
            return httplib::Server::HandlerResponse::Handled;
        }
        return httplib::Server::HandlerResponse::Unhandled;
    });

    // Proxy Handler
    auto proxy_sd = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", sd_port);
    };

    auto proxy_llm = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", llm_port);
    };

    // SD Routes
    svr.Get("/v1/models", proxy_sd);
    svr.Post("/v1/models/load", proxy_sd);
    svr.Post("/v1/upscale/load", proxy_sd);
    svr.Post("/v1/images/upscale", proxy_sd);
    svr.Get("/v1/history/images", proxy_sd);
    svr.Post("/v1/images/generations", proxy_sd);
    svr.Post("/v1/images/edits", proxy_sd);
    svr.Get("/v1/progress", proxy_sd);
    svr.Get("/v1/config", proxy_sd);
    svr.Post("/v1/config", proxy_sd);
    
    // Progress Stream (Special handling or simple proxy? Simple proxy handles GET streams if implemented right)
    // For now, let's try the simple proxy.
    svr.Get("/v1/stream/progress", proxy_sd); 

    // LLM Routes
    svr.Get("/v1/llm/models", proxy_llm); // This was missing in main, but exists in llama_server
    svr.Post("/v1/chat/completions", proxy_llm);
    svr.Post("/v1/completions", proxy_llm);
    svr.Post("/v1/embeddings", proxy_llm);
    svr.Post("/v1/tokenize", proxy_llm);
    svr.Post("/v1/detokenize", proxy_llm);
    svr.Post("/v1/llm/load", proxy_llm);
    svr.Post("/v1/llm/unload", proxy_llm);

    svr.Get("/outputs/(.*)", proxy_sd); // Forward output requests to SD worker (which has the files)

    svr.Get("/health", [&](const httplib::Request& req, httplib::Response& res) {
        bool sd_ok = pm.is_running(sd_process);
        bool llm_ok = pm.is_running(llm_process);
        mysti::json j;
        j["status"] = "ok";
        j["sd_worker"] = sd_ok ? "running" : "stopped";
        j["llm_worker"] = llm_ok ? "running" : "stopped";
        res.set_content(j.dump(), "application/json");
    });

    // Start health check thread
    std::thread health_thread(health_check_loop, "127.0.0.1", sd_port, "127.0.0.1", llm_port);
    health_thread.detach();

    LOG_INFO("Orchestrator listening on %s:%d", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    // Cleanup
    pm.terminate(sd_process);
    pm.terminate(llm_process);

    return 0;
}
