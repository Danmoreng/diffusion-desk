#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#endif

#include "orchestrator_main.hpp"
#include <iostream>
#include <fstream>
#include <thread>
#include <chrono>
#include <mutex>
#include <atomic>
#include <cstring>
#include "process_manager.hpp"
#include "proxy.hpp"
#include "ws_manager.hpp"
#include "database.hpp"
#include "httplib.h"
#include "services/health_service.hpp"
#include "services/resource_manager.hpp"
#include "services/service_controller.hpp"
#include "services/tagging_service.hpp"
#include "services/import_service.hpp"

static ProcessManager pm;
static ProcessManager::ProcessInfo sd_process;
static ProcessManager::ProcessInfo llm_process;
static int sd_port = 0;
static int llm_port = 0;
static std::shared_ptr<mysti::Database> g_db;
static std::shared_ptr<mysti::ResourceManager> g_res_mgr;
static std::shared_ptr<mysti::ServiceController> g_controller;
static std::shared_ptr<mysti::WsManager> g_ws_mgr;
static std::unique_ptr<mysti::TaggingService> g_tagging_svc;
static std::unique_ptr<mysti::HealthService> g_health_svc;
static std::unique_ptr<mysti::ImportService> g_import_svc;
static std::string g_internal_token;
static std::atomic<bool> is_shutting_down{false};

#ifdef _WIN32
BOOL WINAPI ConsoleCtrlHandler(DWORD dwCtrlType) {
    if (dwCtrlType == CTRL_C_EVENT || dwCtrlType == CTRL_BREAK_EVENT || dwCtrlType == CTRL_CLOSE_EVENT) {
        is_shutting_down = true;
        if (g_tagging_svc) g_tagging_svc->stop();
        if (g_health_svc) g_health_svc->stop();
        if (pm.is_running(sd_process)) pm.terminate(sd_process);
        if (pm.is_running(llm_process)) pm.terminate(llm_process);
        exit(0);
        return TRUE;
    }
    return FALSE;
}
#endif

bool wait_for_health_simple(int port, const std::string& token, int timeout_sec = 30) {
    httplib::Client cli("127.0.0.1", port);
    cli.set_connection_timeout(1);
    httplib::Headers headers;
    if (!token.empty()) headers.emplace("X-Internal-Token", token);
    for (int i = 0; i < timeout_sec; ++i) {
        if (is_shutting_down) return false;
        if (auto res = cli.Get("/internal/health", headers)) {
            if (res->status == 200) return true;
        }
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    return false;
}

int run_orchestrator(int argc, const char** argv, SDSvrParams& svr_params) {
#ifdef _WIN32
    SetConsoleCtrlHandler(ConsoleCtrlHandler, TRUE);
#endif
    try {
        g_db = std::make_shared<mysti::Database>("mysti.db");
        g_db->init_schema();
        sd_port = svr_params.listen_port + 1;
        llm_port = svr_params.listen_port + 2;
        g_internal_token = svr_params.internal_token;
        g_res_mgr = std::make_shared<mysti::ResourceManager>(sd_port, llm_port, g_internal_token);
        g_ws_mgr = std::make_shared<mysti::WsManager>(svr_params.listen_port + 3, "127.0.0.1");
        g_controller = std::make_shared<mysti::ServiceController>(g_db, g_res_mgr, g_ws_mgr, sd_port, llm_port, g_internal_token);
        g_import_svc = std::make_unique<mysti::ImportService>(g_db);
        g_import_svc->auto_import_outputs(svr_params.output_dir);
    } catch (const std::exception& e) {
        LOG_ERROR("Failed to initialize core services: %s", e.what());
        return 1;
    }
    std::string sd_exe_path, llm_exe_path;
    fs::path bin_dir = fs::path(argv[0]).parent_path();
#ifdef _WIN32
    sd_exe_path = (bin_dir / "mysti_sd_worker.exe").string();
    llm_exe_path = (bin_dir / "mysti_llm_worker.exe").string();
#else
    sd_exe_path = (bin_dir / "mysti_sd_worker").string();
    llm_exe_path = (bin_dir / "mysti_llm_worker").string();
#endif
    std::vector<std::string> common_args;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--mode" || arg == "-l" || arg == "--listen-ip" || arg == "--listen-port" || arg == "--internal-token") { i++; continue; }
        common_args.push_back(arg);
    }
    std::vector<std::string> sd_args = common_args;
    sd_args.push_back("--listen-port"); sd_args.push_back(std::to_string(sd_port));
    sd_args.push_back("--listen-ip"); sd_args.push_back("127.0.0.1");
    if (!g_internal_token.empty()) { sd_args.push_back("--internal-token"); sd_args.push_back(g_internal_token); }
    std::vector<std::string> llm_args = common_args;
    llm_args.push_back("--listen-port"); llm_args.push_back(std::to_string(llm_port));
    llm_args.push_back("--listen-ip"); llm_args.push_back("127.0.0.1");
    if (!g_internal_token.empty()) { llm_args.push_back("--internal-token"); llm_args.push_back(g_internal_token); }
    if (!pm.spawn(sd_exe_path, sd_args, sd_process, "sd_worker.log") || !pm.spawn(llm_exe_path, llm_args, llm_process, "llm_worker.log")) return 1;
    
    g_health_svc = std::make_unique<mysti::HealthService>(pm, sd_process, llm_process, sd_port, llm_port, sd_exe_path, llm_exe_path, sd_args, llm_args, "sd_worker.log", "llm_worker.log", g_internal_token);
    g_health_svc->set_model_state_callbacks([]() { return g_controller->get_last_sd_model_req(); }, []() { return g_controller->get_last_llm_model_req(); });
    g_health_svc->start();
    g_tagging_svc = std::make_unique<mysti::TaggingService>(g_db, llm_port, g_internal_token);
    g_tagging_svc->set_model_provider([]() { return g_controller->get_last_llm_model_req(); });
    g_tagging_svc->start();
    g_controller->set_on_generation_callback([]() { if (g_tagging_svc) g_tagging_svc->notify_new_generation(); });
    g_ws_mgr->start();

    // Auto-initialize SD Model State from Args
    std::string initial_sd_model;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if ((arg == "--model" || arg == "-m" || arg == "--diffusion-model") && i + 1 < argc) {
            initial_sd_model = argv[i + 1]; break;
        }
    }
    if (!initial_sd_model.empty()) LOG_INFO("Initial SD model from args: %s", initial_sd_model.c_str());

    // Auto-load LLM Logic (Background)
    std::string preload_llm_model;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if ((arg == "--llm-model" || arg == "-lm") && i + 1 < argc) {
            preload_llm_model = argv[i + 1]; break;
        }
    }
    if (!preload_llm_model.empty()) {
        std::thread([preload_llm_model]() {
            if (wait_for_health_simple(llm_port, g_internal_token)) {
                mysti::json body; body["model_id"] = preload_llm_model;
                std::string body_str = body.dump();
                g_controller->set_last_llm_model_req(body_str);
                httplib::Client cli("127.0.0.1", llm_port);
                cli.set_read_timeout(600);
                httplib::Headers h; if (!g_internal_token.empty()) h.emplace("X-Internal-Token", g_internal_token);
                auto res = cli.Post("/v1/llm/load", h, body_str, "application/json");
                if (res && res->status == 200) LOG_INFO("Successfully pre-loaded LLM model.");
                else LOG_ERROR("Failed to pre-load LLM model.");
            }
        }).detach();
    }

    // Background metrics loop
    std::thread([&, svr_params]() {
        while (!is_shutting_down) {
            std::this_thread::sleep_for(std::chrono::seconds(2));
            try {
                mysti::json msg; 
                msg["type"] = "metrics";
                auto vram = g_res_mgr->get_vram_status();
                msg["vram_total_gb"] = vram["total_gb"]; 
                msg["vram_free_gb"] = vram["free_gb"];

                float sd_vram = 0, llm_vram = 0; 
                std::string llm_model = ""; 
                bool llm_loaded = false;

                httplib::Headers h; 
                if (!g_internal_token.empty()) h.emplace("X-Internal-Token", g_internal_token);

                httplib::Client cli_sd("127.0.0.1", sd_port);
                if (auto res = cli_sd.Get("/internal/health", h)) {
                    auto j = mysti::json::parse(res->body);
                    sd_vram = j.value("vram_gb", 0.0f);
                }

                httplib::Client cli_llm("127.0.0.1", llm_port);
                if (auto res = cli_llm.Get("/internal/health", h)) {
                    auto j = mysti::json::parse(res->body);
                    llm_vram = j.value("vram_gb", 0.0f); 
                    llm_model = j.value("model_path", ""); 
                    llm_loaded = j.value("loaded", false);
                    if (!llm_model.empty()) {
                        fs::path mp(llm_model);
                        if (mp.is_absolute()) {
                            try { 
                                llm_model = fs::relative(mp, svr_params.model_dir).string(); 
                                std::replace(llm_model.begin(), llm_model.end(), '\\', '/'); 
                            } catch(...) { llm_model = mp.filename().string(); }
                        }
                    }
                }

                mysti::json status_msg; 
                status_msg["path"] = llm_model; 
                status_msg["loaded"] = llm_loaded;
                cli_sd.Post("/internal/llm_status", h, status_msg.dump(), "application/json");

                msg["workers"] = {
                    {"sd", {{"vram_gb", sd_vram}}}, 
                    {"llm", {{"vram_gb", llm_vram}, {"model", llm_model}, {"loaded", llm_loaded}}}
                };
                g_ws_mgr->broadcast(msg);
            } catch (...) {}
        }
    }).detach();

    // Background thread to proxy SD progress to WebSockets
    std::thread([&]() {
        std::string sse_buffer;
        while (!is_shutting_down) {
            if (wait_for_health_simple(sd_port, g_internal_token, 5)) {
                httplib::Client cli("127.0.0.1", sd_port);
                cli.set_connection_timeout(5); cli.set_read_timeout(3600); 
                httplib::Headers h; if (!g_internal_token.empty()) h.emplace("X-Internal-Token", g_internal_token);
                cli.Get("/v1/stream/progress", h, 
                    [&](const httplib::Response& response) { return response.status == 200; },
                    [&](const char *data, size_t data_length) {
                        sse_buffer.append(data, data_length);
                        while (true) {
                            size_t pos = sse_buffer.find("\n\n");
                            if (pos == std::string::npos) break;
                            std::string block = sse_buffer.substr(0, pos);
                            sse_buffer.erase(0, pos + 2);
                            if (block.find("data: ") != std::string::npos) {
                                try {
                                    auto j = mysti::json::parse(block.substr(block.find("data: ") + 6));
                                    mysti::json msg; msg["type"] = "progress"; msg["data"] = j;
                                    g_ws_mgr->broadcast(msg);
                                } catch (...) {}
                            }
                        }
                        return !is_shutting_down;
                    }
                );
            }
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    }).detach();

    httplib::Server svr;
    g_controller->register_routes(svr, svr_params);
    LOG_INFO("Orchestrator listening on %s:%d", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);
    is_shutting_down = true;
    if (g_tagging_svc) g_tagging_svc->stop();
    if (g_health_svc) g_health_svc->stop();
    pm.terminate(sd_process); 
    pm.terminate(llm_process);
    return 0;
}

