#include "orchestrator_main.hpp"
#include "process_manager.hpp"
#include "proxy.hpp"
#include "httplib.h"
#include <iostream>
#include <thread>
#include <chrono>
#include <mutex>
#include <atomic>

static ProcessManager pm;
static ProcessManager::ProcessInfo sd_process;
static ProcessManager::ProcessInfo llm_process;
static int sd_port = 0;
static int llm_port = 0;

static std::string sd_exe_path;
static std::string llm_exe_path;
static std::vector<std::string> sd_args;
static std::vector<std::string> llm_args;

// State tracking for recovery
static std::string last_sd_model_req_body;
static std::string last_llm_model_req_body;
static std::mutex state_mtx;
static std::mutex process_mtx; // Protects access to ProcessManager

static std::atomic<bool> is_shutting_down{false};

#ifdef _WIN32
BOOL WINAPI ConsoleCtrlHandler(DWORD dwCtrlType) {
    switch (dwCtrlType) {
    case CTRL_C_EVENT:
    case CTRL_BREAK_EVENT:
    case CTRL_CLOSE_EVENT:
    case CTRL_LOGOFF_EVENT:
    case CTRL_SHUTDOWN_EVENT:
        std::cout << "\n[Orchestrator] Shutdown signal received. Cleaning up...\n";
        is_shutting_down = true;
        
        {
            std::lock_guard<std::mutex> lock(process_mtx);
            // Terminate workers
            if (pm.is_running(sd_process)) pm.terminate(sd_process);
            if (pm.is_running(llm_process)) pm.terminate(llm_process);
        }
        
        std::cout << "[Orchestrator] Workers terminated. Exiting.\n";
        exit(0);
        return TRUE;
    default:
        return FALSE;
    }
}
#else
#include <signal.h>

void signal_handler(int sig) {
    std::cout << "\n[Orchestrator] Shutdown signal received (" << sig << "). Cleaning up...\n";
    is_shutting_down = true;

    {
        std::lock_guard<std::mutex> lock(process_mtx);
        if (pm.is_running(sd_process)) pm.terminate(sd_process);
        if (pm.is_running(llm_process)) pm.terminate(llm_process);
    }
    
    std::cout << "[Orchestrator] Workers terminated. Exiting.\n";
    exit(0);
}
#endif

std::string get_executable_path(const char* argv0) {
    return argv0;
}

// Helper to wait for a port to be ready (health check)
bool wait_for_health(int port, int timeout_sec = 30) {
    httplib::Client cli("127.0.0.1", port);
    cli.set_connection_timeout(1);
    for (int i = 0; i < timeout_sec; ++i) {
        if (is_shutting_down) return false;
        if (auto res = cli.Get("/internal/health")) {
            if (res->status == 200) return true;
        }
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    return false;
}

void restart_sd_worker() {
    if (is_shutting_down) return;
    LOG_WARN("Detected SD Worker failure. Restarting...");
    
    {
        std::lock_guard<std::mutex> lock(process_mtx);
        // Ensure dead
        pm.terminate(sd_process);

        // Respawn
        if (!pm.spawn(sd_exe_path, sd_args, sd_process)) {
            LOG_ERROR("Failed to respawn SD Worker!");
            return;
        }
    }

    // Wait for health
    if (wait_for_health(sd_port)) {
        LOG_INFO("SD Worker back online.");
        
        // Restore state
        std::string model_body;
        {
            std::lock_guard<std::mutex> lock(state_mtx);
            model_body = last_sd_model_req_body;
        }

        if (!model_body.empty()) {
            LOG_INFO("Restoring SD model...");
            httplib::Client cli("127.0.0.1", sd_port);
            cli.set_read_timeout(300); // Model load can take time
            auto res = cli.Post("/v1/models/load", model_body, "application/json");
            if (res && res->status == 200) {
                LOG_INFO("SD model restored successfully.");
            } else {
                LOG_ERROR("Failed to restore SD model.");
            }
        }
    } else {
        LOG_ERROR("SD Worker failed to recover within timeout.");
    }
}

void restart_llm_worker() {
    if (is_shutting_down) return;
    LOG_WARN("Detected LLM Worker failure. Restarting...");
    
    {
        std::lock_guard<std::mutex> lock(process_mtx);
        pm.terminate(llm_process);

        if (!pm.spawn(llm_exe_path, llm_args, llm_process)) {
            LOG_ERROR("Failed to respawn LLM Worker!");
            return;
        }
    }

    if (wait_for_health(llm_port)) {
        LOG_INFO("LLM Worker back online.");

        std::string model_body;
        {
            std::lock_guard<std::mutex> lock(state_mtx);
            model_body = last_llm_model_req_body;
        }

        if (!model_body.empty()) {
            LOG_INFO("Restoring LLM model...");
            httplib::Client cli("127.0.0.1", llm_port);
            cli.set_read_timeout(300);
            auto res = cli.Post("/v1/llm/load", model_body, "application/json");
            if (res && res->status == 200) {
                LOG_INFO("LLM model restored successfully.");
            } else {
                LOG_ERROR("Failed to restore LLM model.");
            }
        }
    } else {
        LOG_ERROR("LLM Worker failed to recover within timeout.");
    }
}

void health_check_loop(const std::string& sd_host, int sd_p, const std::string& llm_host, int llm_p) {
    httplib::Client sd_cli(sd_host, sd_p);
    httplib::Client llm_cli(llm_host, llm_p);
    sd_cli.set_connection_timeout(1);
    llm_cli.set_connection_timeout(1);

    int sd_fail_count = 0;
    int llm_fail_count = 0;
    const int MAX_FAILURES = 3;

    while (!is_shutting_down) {
        std::this_thread::sleep_for(std::chrono::seconds(2));

        // --- SD Check ---
        bool sd_alive = false;
        {
            std::lock_guard<std::mutex> lock(process_mtx);
            sd_alive = pm.is_running(sd_process);
        }

        if (sd_alive) {
            // Process is there, check HTTP
            if (auto res = sd_cli.Get("/internal/health")) {
                sd_fail_count = 0;
            } else {
                sd_fail_count++;
                // If ping fails repeatedly, it might be hung
                if (sd_fail_count >= MAX_FAILURES) {
                    LOG_WARN("SD Worker unresponsive (HTTP).");
                    sd_alive = false; // Trigger restart
                }
            }
        }
        
        if (!sd_alive) {
            restart_sd_worker();
            sd_fail_count = 0;
        }

        // --- LLM Check ---
        bool llm_alive = false;
        {
            std::lock_guard<std::mutex> lock(process_mtx);
            llm_alive = pm.is_running(llm_process);
        }

        if (llm_alive) {
            if (auto res = llm_cli.Get("/internal/health")) {
                llm_fail_count = 0;
            } else {
                llm_fail_count++;
                if (llm_fail_count >= MAX_FAILURES) {
                    LOG_WARN("LLM Worker unresponsive (HTTP).");
                    llm_alive = false;
                }
            }
        }

        if (!llm_alive) {
            restart_llm_worker();
            llm_fail_count = 0;
        }
    }
}

int run_orchestrator(int argc, const char** argv, SDSvrParams& svr_params) {
    LOG_INFO("Starting Orchestrator on port %d...", svr_params.listen_port);

#ifdef _WIN32
    if (!SetConsoleCtrlHandler(ConsoleCtrlHandler, TRUE)) {
        LOG_ERROR("Could not set control handler");
        return 1;
    }
#else
    struct sigaction sa;
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
#endif
    
    // 1. Determine Ports and Paths
    sd_port = svr_params.listen_port + 1;
    llm_port = svr_params.listen_port + 2;
    
    fs::path bin_dir = fs::path(argv[0]).parent_path();
#ifdef _WIN32
    sd_exe_path = (bin_dir / "mysti_sd_worker.exe").string();
    llm_exe_path = (bin_dir / "mysti_llm_worker.exe").string();
#else
    sd_exe_path = (bin_dir / "mysti_sd_worker").string();
    llm_exe_path = (bin_dir / "mysti_llm_worker").string();
#endif

    // 2. Prepare Args
    std::vector<std::string> common_args;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--mode" || arg == "-l" || arg == "--listen-ip" || arg == "--listen-port") {
            i++; 
            continue;
        }
        common_args.push_back(arg);
    }

    sd_args = common_args;
    // Mode flag is implicit in the binary now, but we might want to keep it if the worker checks it.
    // However, our new main_sd.cpp hardcodes it.
    sd_args.push_back("--listen-port"); sd_args.push_back(std::to_string(sd_port));
    sd_args.push_back("--listen-ip"); sd_args.push_back("127.0.0.1");
    
    llm_args = common_args;
    llm_args.push_back("--listen-port"); llm_args.push_back(std::to_string(llm_port));
    llm_args.push_back("--listen-ip"); llm_args.push_back("127.0.0.1");

    // 3. Initial Spawn
    LOG_INFO("Spawning SD Worker (%s) on port %d", sd_exe_path.c_str(), sd_port);
    if (!pm.spawn(sd_exe_path, sd_args, sd_process)) {
        LOG_ERROR("Failed to spawn SD Worker");
        return 1;
    }

    LOG_INFO("Spawning LLM Worker (%s) on port %d", llm_exe_path.c_str(), llm_port);
    if (!pm.spawn(llm_exe_path, llm_args, llm_process)) {
        LOG_ERROR("Failed to spawn LLM Worker");
        pm.terminate(sd_process);
        return 1;
    }

    // Auto-load LLM Logic (preserved from previous version)
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
            if (wait_for_health(llm_port)) {
                mysti::json body;
                body["model_id"] = preload_llm_model;
                std::string body_str = body.dump();

                // Save state for restart logic
                {
                    std::lock_guard<std::mutex> lock(state_mtx);
                    last_llm_model_req_body = body_str;
                }

                httplib::Client cli("127.0.0.1", llm_port);
                cli.set_read_timeout(600);
                auto res = cli.Post("/v1/llm/load", body_str, "application/json");
                if (res && res->status == 200) {
                    LOG_INFO("Successfully pre-loaded LLM model.");
                } else {
                    LOG_ERROR("Failed to pre-load LLM model.");
                }
            }
        }).detach();
    }

    // 4. Start Proxy Server
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

    // Proxy Helpers
    auto proxy_sd = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", sd_port);
    };

    auto proxy_llm = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", llm_port);
    };

    // State Interceptors
    auto intercept_sd_load = [&](const httplib::Request& req, httplib::Response& res) {
        // Forward first
        Proxy::forward_request(req, res, "127.0.0.1", sd_port);
        // If success, save state
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(state_mtx);
            last_sd_model_req_body = req.body;
            LOG_INFO("Saved SD model state for auto-recovery.");
        }
    };

    auto intercept_llm_load = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", llm_port);
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(state_mtx);
            last_llm_model_req_body = req.body;
            LOG_INFO("Saved LLM model state for auto-recovery.");
        }
    };

    // SD Routes
    svr.Get("/v1/models", proxy_sd);
    svr.Post("/v1/models/load", intercept_sd_load); // Intercepted
    svr.Post("/v1/upscale/load", proxy_sd); // Could also intercept if needed
    svr.Post("/v1/images/upscale", proxy_sd);
    svr.Get("/v1/history/images", proxy_sd);
    svr.Post("/v1/images/generations", proxy_sd);
    svr.Post("/v1/images/edits", proxy_sd);
    svr.Get("/v1/progress", proxy_sd);
    svr.Get("/v1/config", proxy_sd);
    svr.Post("/v1/config", proxy_sd);
    svr.Get("/v1/stream/progress", proxy_sd); 
    svr.Get("/outputs/(.*)", proxy_sd);

    // LLM Routes
    svr.Get("/v1/llm/models", proxy_llm);
    svr.Post("/v1/chat/completions", proxy_llm);
    svr.Post("/v1/completions", proxy_llm);
    svr.Post("/v1/embeddings", proxy_llm);
    svr.Post("/v1/tokenize", proxy_llm);
    svr.Post("/v1/detokenize", proxy_llm);
    svr.Post("/v1/llm/load", intercept_llm_load); // Intercepted
    svr.Post("/v1/llm/unload", proxy_llm);

    svr.Get("/health", [&](const httplib::Request& req, httplib::Response& res) {
        bool sd_ok = pm.is_running(sd_process);
        bool llm_ok = pm.is_running(llm_process);
        mysti::json j;
        j["status"] = (sd_ok && llm_ok) ? "ok" : "degraded";
        j["sd_worker"] = sd_ok ? "running" : "stopped";
        j["llm_worker"] = llm_ok ? "running" : "stopped";
        res.set_content(j.dump(), "application/json");
    });

    // Start health check / restart manager thread
    std::thread health_thread(health_check_loop, "127.0.0.1", sd_port, "127.0.0.1", llm_port);
    health_thread.detach();

    LOG_INFO("Orchestrator listening on %s:%d", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    // Cleanup
    is_shutting_down = true;
    pm.terminate(sd_process);
    pm.terminate(llm_process);

    return 0;
}