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
#include "services/tagging_service.hpp"
#include "services/import_service.hpp"
#include "services/health_service.hpp"

// Globals
static ProcessManager pm;
static ProcessManager::ProcessInfo sd_process;
static ProcessManager::ProcessInfo llm_process;
static int sd_port = 0;
static int llm_port = 0;

static std::shared_ptr<mysti::Database> g_db;
static std::unique_ptr<mysti::TaggingService> g_tagging_svc;
static std::unique_ptr<mysti::HealthService> g_health_svc;
static std::unique_ptr<mysti::ImportService> g_import_svc;

static std::string g_internal_token;


static std::atomic<bool> g_is_generating{false};

// State tracking for recovery (Accessed by HealthService callbacks)
static std::string last_sd_model_req_body;
static std::string last_llm_model_req_body;
static std::mutex state_mtx;

static std::atomic<bool> is_shutting_down{false};

static std::string base64_decode(const std::string& in) {
    std::string out;
    std::vector<int> T(256, -1);
    for (int i = 0; i < 64; i++) T["ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"[i]] = i;
    int val = 0, valb = -8;
    for (unsigned char c : in) {
        if (T[c] == -1) break;
        val = (val << 6) + T[c];
        valb += 6;
        if (valb >= 0) {
            out.push_back(char((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return out;
}

void generate_style_preview(mysti::Style style, std::string output_dir) {
    if (style.prompt.empty()) return;

    // Subject for the preview
    std::string subject = "a simple glass sphere on a minimalist stone pedestal, studio lighting";
    std::string final_prompt = style.prompt;
    if (final_prompt.find("{prompt}") != std::string::npos) {
        final_prompt = std::regex_replace(final_prompt, std::regex("\\{prompt\\}"), subject);
    } else {
        final_prompt = subject + ", " + final_prompt;
    }

    LOG_INFO("[Style Preview] Generating for style: %s", style.name.c_str());

    try {
        httplib::Client cli("127.0.0.1", sd_port);
        cli.set_read_timeout(120);
        
        mysti::json req;
        req["prompt"] = final_prompt;
        req["negative_prompt"] = style.negative_prompt;
        req["width"] = 512;
        req["height"] = 512;
        req["sample_steps"] = 15; // Low steps for speed
        req["cfg_scale"] = 7.0;
        req["n"] = 1;
        req["save_image"] = false; 

        httplib::Headers h;
        if (!g_internal_token.empty()) h.emplace("X-Internal-Token", g_internal_token);

        auto res = cli.Post("/v1/images/generations", h, req.dump(), "application/json");
        if (res && res->status == 200) {
            auto j = mysti::json::parse(res->body);
            if (j.contains("data") && !j["data"].empty()) {
                std::string b64 = j["data"][0].value("b64_json", "");
                if (!b64.empty()) {
                    fs::path preview_dir = fs::path(output_dir) / "previews";
                    if (!fs::exists(preview_dir)) fs::create_directories(preview_dir);

                    std::string filename = "style_" + style.name + ".png";
                    std::replace(filename.begin(), filename.end(), ' ', '_');
                    fs::path filepath = preview_dir / filename;

                    std::string decoded = base64_decode(b64);
                    std::ofstream out(filepath, std::ios::binary);
                    out.write(decoded.data(), decoded.size());
                    out.close();

                    style.preview_path = "/outputs/previews/" + filepath.filename().string();
                    if (g_db) g_db->save_style(style);
                    LOG_INFO("[Style Preview] Saved to %s", style.preview_path.c_str());
                }
            }
        }
    } catch (const std::exception& e) {
        LOG_ERROR("[Style Preview] Failed for %s: %s", style.name.c_str(), e.what());
    }
}

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
        
        if (g_tagging_svc) g_tagging_svc->stop();
        if (g_health_svc) g_health_svc->stop();
        
        // Terminate workers via PM directly for safety
        if (pm.is_running(sd_process)) pm.terminate(sd_process);
        if (pm.is_running(llm_process)) pm.terminate(llm_process);
        
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

    if (g_tagging_svc) g_tagging_svc->stop();
    if (g_health_svc) g_health_svc->stop();

    if (pm.is_running(sd_process)) pm.terminate(sd_process);
    if (pm.is_running(llm_process)) pm.terminate(llm_process);
    
    std::cout << "[Orchestrator] Workers terminated. Exiting.\n";
    exit(0);
}
#endif

bool wait_for_health_simple(int port, const std::string& token, int timeout_sec = 30) {
    httplib::Client cli("127.0.0.1", port);
    cli.set_connection_timeout(1);
    httplib::Headers headers;
    if (!token.empty()) {
        headers.emplace("X-Internal-Token", token);
    }
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

    // Initialize Database
    try {
        g_db = std::make_shared<mysti::Database>("mysti.db");
        g_db->init_schema();
        g_import_svc = std::make_unique<mysti::ImportService>(g_db);
        g_import_svc->auto_import_outputs(svr_params.output_dir);
    } catch (const std::exception& e) {
        LOG_ERROR("Failed to initialize database: %s", e.what());
        return 1;
    }
    
    // 1. Determine Ports and Paths
    sd_port = svr_params.listen_port + 1;
    llm_port = svr_params.listen_port + 2;
    g_internal_token = svr_params.internal_token;
    
    // Local paths and args
    std::string sd_exe_path;
    std::string llm_exe_path;
    std::vector<std::string> sd_args;
    std::vector<std::string> llm_args;

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
        if (arg == "--mode" || arg == "-l" || arg == "--listen-ip" || arg == "--listen-port" || arg == "--internal-token") {
            i++; // Consume the value
            continue;
        }
        common_args.push_back(arg);
    }

    sd_args = common_args;
    sd_args.push_back("--listen-port"); sd_args.push_back(std::to_string(sd_port));
    sd_args.push_back("--listen-ip"); sd_args.push_back("127.0.0.1");
    
    llm_args = common_args;
    llm_args.push_back("--listen-port"); llm_args.push_back(std::to_string(llm_port));
    llm_args.push_back("--listen-ip"); llm_args.push_back("127.0.0.1");

    if (!g_internal_token.empty()) {
        sd_args.push_back("--internal-token");
        sd_args.push_back(g_internal_token);
        llm_args.push_back("--internal-token");
        llm_args.push_back(g_internal_token);
    }

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

    // 4. Initialize Services
    g_tagging_svc = std::make_unique<mysti::TaggingService>(g_db, llm_port, g_internal_token);
    g_health_svc = std::make_unique<mysti::HealthService>(
        pm, sd_process, llm_process, 
        sd_port, llm_port,
        sd_exe_path, llm_exe_path,
        sd_args, llm_args,
        g_internal_token
    );

    g_health_svc->set_max_sd_crashes(svr_params.safe_mode_crashes);
    g_health_svc->set_model_state_callbacks(
        []() { std::lock_guard<std::mutex> lock(state_mtx); return last_sd_model_req_body; },
        []() { std::lock_guard<std::mutex> lock(state_mtx); return last_llm_model_req_body; }
    );

    g_health_svc->start();
    
    g_tagging_svc->set_model_provider([]() {
        std::lock_guard<std::mutex> lock(state_mtx);
        return last_llm_model_req_body;
    });
    g_tagging_svc->start();

    // Auto-load LLM Logic
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
            if (wait_for_health_simple(llm_port, g_internal_token)) {
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
                httplib::Headers headers;
                if (!g_internal_token.empty()) {
                    headers.emplace("X-Internal-Token", g_internal_token);
                }
                auto res = cli.Post("/v1/llm/load", headers, body_str, "application/json");
                if (res && res->status == 200) {
                    LOG_INFO("Successfully pre-loaded LLM model.");
                } else {
                    LOG_ERROR("Failed to pre-load LLM model.");
                }
            }
        }).detach();
    }

    // 5. Start WebSocket Server
    int ws_port = svr_params.listen_port + 3;
    mysti::WsManager ws_mgr(ws_port, "127.0.0.1"); 
    if (!ws_mgr.start()) {
        LOG_WARN("Failed to start WebSocket server");
    }

    // Background thread for periodic metrics broadcast
    std::thread ws_broadcast_thread([&]() {
        while (!is_shutting_down) {
            std::this_thread::sleep_for(std::chrono::seconds(2));
            mysti::json msg;
            msg["type"] = "metrics";
            msg["vram_total_gb"] = get_total_vram_gb();
            msg["vram_free_gb"] = get_free_vram_gb();
            
            float sd_vram = 0;
            float llm_vram = 0;
            std::string sd_model = "";
            std::string llm_model = "";
            bool llm_loaded = false;
            
            httplib::Headers headers;
            if (!g_internal_token.empty()) {
                headers.emplace("X-Internal-Token", g_internal_token);
            }

            {
                httplib::Client cli("127.0.0.1", sd_port);
                if (auto res = cli.Get("/internal/health", headers)) {
                    try {
                        auto j = mysti::json::parse(res->body);
                        sd_vram = j.value("vram_gb", 0.0f);
                    } catch(...){}
                }
            }
            {
                httplib::Client cli("127.0.0.1", llm_port);
                if (auto res = cli.Get("/internal/health", headers)) {
                    try {
                        auto j = mysti::json::parse(res->body);
                        llm_vram = j.value("vram_gb", 0.0f);
                        llm_model = j.value("model_path", "");
                        llm_loaded = j.value("loaded", false);
                        
                        // Strip base dir if present
                        if (!llm_model.empty()) {
                            fs::path mp(llm_model);
                            if (mp.is_absolute()) {
                                try {
                                    llm_model = fs::relative(mp, svr_params.model_dir).string();
                                    std::replace(llm_model.begin(), llm_model.end(), '\\', '/');
                                } catch(...) {
                                    llm_model = mp.filename().string();
                                }
                            }
                        }
                    } catch(...){}
                }
            }

            // Sync LLM status to SD worker
            {
                httplib::Client cli("127.0.0.1", sd_port);
                mysti::json status_msg;
                status_msg["path"] = llm_model;
                status_msg["loaded"] = llm_loaded;
                cli.Post("/internal/llm_status", headers, status_msg.dump(), "application/json");
            }

            msg["workers"] = {
                {"sd", {{"vram_gb", sd_vram}}},
                {"llm", {{"vram_gb", llm_vram}, {"model", llm_model}, {"loaded", llm_loaded}}}
            };

            ws_mgr.broadcast(msg);
        }
    });
    ws_broadcast_thread.detach();

    // Background thread to proxy SD progress to WebSockets
    std::thread sd_progress_proxy_thread([&]() {
        std::cout << "[Orchestrator] PROXY: SD Progress Proxy Thread started. Target port: " << sd_port << std::endl;
        std::string sse_buffer;
        while (!is_shutting_down) {
            if (wait_for_health_simple(sd_port, g_internal_token, 5)) {
                std::cout << "[Orchestrator] PROXY: Connecting to SD progress stream at 127.0.0.1:" << sd_port << std::endl;
                httplib::Client cli("127.0.0.1", sd_port);
                cli.set_connection_timeout(5);
                cli.set_read_timeout(3600); 
                
                httplib::Headers headers;
                if (!g_internal_token.empty()) {
                    headers.emplace("X-Internal-Token", g_internal_token);
                }

                auto res = cli.Get("/v1/stream/progress", headers, 
                    [&](const httplib::Response& response) {
                        if (response.status != 200) {
                            return false;
                        }
                        std::cout << "[Orchestrator] PROXY: Stream connected successfully." << std::endl;
                        return true;
                    },
                    [&](const char *data, size_t data_length) {
                        sse_buffer.append(data, data_length);
                        while (true) {
                            size_t pos = sse_buffer.find("\n\n");
                            if (pos == std::string::npos) break;

                            std::string block = sse_buffer.substr(0, pos);
                            sse_buffer.erase(0, pos + 2);

                            if (block.find("data: ") != std::string::npos) {
                                size_t data_start = block.find("data: ");
                                try {
                                    std::string json_str = block.substr(data_start + 6);
                                    auto j = mysti::json::parse(json_str);
                                    mysti::json msg;
                                    msg["type"] = "progress";
                                    msg["data"] = j;
                                    ws_mgr.broadcast(msg);
                                } catch (...) {}
                            }
                        }
                        return !is_shutting_down;
                    }
                );
            }
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    });
    sd_progress_proxy_thread.detach();

    // 6. Start Proxy Server
    httplib::Server svr;

    if (!svr.set_mount_point("/app", svr_params.app_dir)) {
        LOG_WARN("failed to mount %s directory", svr_params.app_dir.c_str());
    }

    svr.Get("/", [](const httplib::Request& req, httplib::Response& res) {
        res.set_redirect("/app/");
    });

    svr.set_error_handler([&svr_params](const httplib::Request& req, httplib::Response& res) {
        if (req.path.find("/app/") == 0 && res.status == 404) {
            std::string index_path = (fs::path(svr_params.app_dir) / "index.html").string();
            std::ifstream file(index_path, std::ios::binary);
            if (file) {
                std::string content((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
                res.set_content(content, "text/html");
                res.status = 200;
                return;
            }
        }
    });

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

    auto proxy_sd = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", sd_port, "", g_internal_token);
    };

    auto proxy_llm = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", llm_port, "", g_internal_token);
    };

    // State Interceptors
    auto intercept_sd_load = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", sd_port, "", g_internal_token);
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(state_mtx);
            last_sd_model_req_body = req.body;
            LOG_INFO("Saved SD model state for auto-recovery.");
        }
    };

    auto intercept_llm_load = [&](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", llm_port, "", g_internal_token);
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(state_mtx);
            last_llm_model_req_body = req.body;
            LOG_INFO("Saved LLM model state for auto-recovery.");
        }
    };

    auto intercept_sd_generate = [&](const httplib::Request& req, httplib::Response& res) {
        // 1. VRAM Arbitration
        float free_vram = get_free_vram_gb();
        LOG_INFO("VRAM Arbitration: Current free VRAM = %.2f GB", free_vram);

        httplib::Headers headers;
        if (!g_internal_token.empty()) {
            headers.emplace("X-Internal-Token", g_internal_token);
        }

        bool llm_loaded = false;
        {
            httplib::Client cli("127.0.0.1", llm_port);
            if (auto hres = cli.Get("/internal/health", headers)) {
                if (hres->status == 200) {
                    try {
                        auto j = mysti::json::parse(hres->body);
                        llm_loaded = j.value("loaded", false);
                    } catch (...) {}
                }
            }
        }

        if (free_vram < 4.0f && llm_loaded) {
            LOG_INFO("Low VRAM detected (%.2f GB). Requesting LLM unload...", free_vram);
            httplib::Client cli("127.0.0.1", llm_port);
            auto ures = cli.Post("/v1/llm/unload", headers, "", "application/json");
            if (ures && ures->status == 200) {
                LOG_INFO("LLM unloaded successfully.");
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
            } else {
                LOG_WARN("Failed to unload LLM.");
            }
        }

        // 2. Pause Tagging
        g_is_generating = true;
        if (g_tagging_svc) g_tagging_svc->set_generation_active(true);

        // 3. Forward Request
        Proxy::forward_request(req, res, "127.0.0.1", sd_port, "", g_internal_token);
        
        // 4. Resume Tagging
        g_is_generating = false;
        if (g_tagging_svc) g_tagging_svc->set_generation_active(false);

        // 5. DB Persistence & Auto-Tagging
        if (res.status == 200 && g_db) {
            try {
                auto req_json = mysti::json::parse(req.body);
                auto res_json = mysti::json::parse(res.body);

                std::string prompt = req_json.value("prompt", "");
                std::string neg_prompt = req_json.value("negative_prompt", "");
                int width = req_json.value("width", 512);
                int height = req_json.value("height", 512);
                
                int steps = 20;
                if (req_json.contains("sample_steps")) {
                    steps = req_json.value("sample_steps", 20);
                } else if (req_json.contains("steps")) {
                    steps = req_json.value("steps", 20);
                }

                float cfg = req_json.value("cfg_scale", 7.0f);
                long long seed = req_json.value("seed", -1LL);
                double generation_time = res_json.value("generation_time", 0.0);
                
                std::string uuid = res_json.value("id", ""); 
                std::string file_path = "";
                
                if (res_json.contains("data") && res_json["data"].is_array() && !res_json["data"].empty()) {
                    file_path = res_json["data"][0].value("url", "");
                    if (seed == -1LL) {
                        seed = res_json["data"][0].value("seed", seed);
                    }
                }

                if (uuid.empty() && !file_path.empty()) {
                    size_t last_slash = file_path.find_last_of('/');
                    if (last_slash != std::string::npos) {
                        uuid = file_path.substr(last_slash + 1);
                    } else {
                        uuid = file_path;
                    }
                }

                if (!uuid.empty() && !file_path.empty()) {
                    mysti::Generation gen;
                    gen.uuid = uuid;
                    gen.file_path = file_path;
                    gen.prompt = prompt;
                    gen.negative_prompt = neg_prompt;
                    gen.seed = seed;
                    gen.width = width;
                    gen.height = height;
                    gen.steps = steps;
                    gen.cfg_scale = cfg;
                    gen.generation_time = generation_time;
                    
                    // Extract model ID from last load request
                    {
                        std::lock_guard<std::mutex> lock(state_mtx);
                        if (!last_sd_model_req_body.empty()) {
                            try {
                                auto load_j = mysti::json::parse(last_sd_model_req_body);
                                gen.model_hash = load_j.value("model_id", "");
                            } catch(...) {}
                        }
                    }

                    g_db->insert_generation(gen);
                    std::cout << "[Orchestrator] Saved generation " << uuid << " to DB." << std::endl;
                    
                    if (g_tagging_svc) {
                         g_tagging_svc->notify_new_generation();
                    }
                }

            } catch (const std::exception& e) {
                std::cerr << "[Orchestrator] Error processing result: " << e.what() << std::endl;
            }
        }
    };

    // SD Routes
    svr.Get("/v1/models", proxy_sd);
    svr.Post("/v1/models/load", intercept_sd_load); 
    svr.Post("/v1/upscale/load", proxy_sd);
    svr.Post("/v1/images/upscale", proxy_sd);
    
    // DB-backed History
    svr.Get("/v1/history/images", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) {
            Proxy::forward_request(req, res, "127.0.0.1", sd_port, "", g_internal_token);
            return;
        }
        int limit = 50;
        int offset = 0;
        int min_rating = 0;
        std::vector<std::string> tags;
        std::string model = "";
        
        if (req.has_param("limit")) limit = std::stoi(req.get_param_value("limit"));
        if (req.has_param("offset")) offset = std::stoi(req.get_param_value("offset"));
        
        // Handle multiple tags
        auto count = req.get_param_value_count("tag");
        for (size_t i = 0; i < count; ++i) {
            tags.push_back(req.get_param_value("tag", i));
        }

        if (req.has_param("model")) model = req.get_param_value("model");
        if (req.has_param("min_rating")) min_rating = std::stoi(req.get_param_value("min_rating"));

        auto results = g_db->get_generations(limit, offset, tags, model, min_rating);
        res.set_content(results.dump(), "application/json");
    });

    svr.Get("/v1/history/tags", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) {
            res.set_content("[]", "application/json");
            return;
        }
        auto results = g_db->get_tags();
        res.set_content(results.dump(), "application/json");
    });

    // Styles API
    svr.Get("/v1/styles", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.set_content("[]", "application/json"); return; }
        auto results = g_db->get_styles();
        res.set_content(results.dump(), "application/json");
    });

    svr.Post("/v1/styles", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            mysti::Style s;
            s.name = j.value("name", "");
            s.prompt = j.value("prompt", "");
            s.negative_prompt = j.value("negative_prompt", "");
            s.preview_path = j.value("preview_path", "");
            if (s.name.empty()) { res.status = 400; return; }
            g_db->save_style(s);
            
            // Trigger preview generation in background
            std::thread([s, out_dir = svr_params.output_dir]() {
                generate_style_preview(s, out_dir);
            }).detach();

            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/styles/extract", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string input_prompt = j.value("prompt", "");
            if (input_prompt.empty()) {
                res.status = 400;
                res.set_content(R"({"error":"Prompt is required"})", "application/json");
                return;
            }

            // check health
             httplib::Client cli("127.0.0.1", llm_port);
             cli.set_connection_timeout(2);
             httplib::Headers h;
             if (!g_internal_token.empty()) h.emplace("X-Internal-Token", g_internal_token);

             // Construct LLM Request
             mysti::json chat_req;
             chat_req["messages"] = mysti::json::array({
                 {{"role", "system"}, {"content", "You are an expert art style analyzer. Analyze the given image prompt and extract distinct art styles, artists, or aesthetic descriptors. Return a JSON array of objects, where each object has 'name' (concise style name), 'prompt' (the specific style keywords to append, MUST include '{prompt}' placeholder), and 'negative_prompt' (optional tags to avoid). Make sure 'name' is unique and descriptive. Example: [{\"name\": \"Cyberpunk\", \"prompt\": \"{prompt}, cyberpunk, neon lights, high tech\", \"negative_prompt\": \"organic, natural\"}]"}},
                 {{"role", "user"}, {"content", input_prompt}}
             });
             chat_req["temperature"] = 0.3;
             chat_req["response_format"] = {{"type", "json_object"}};

             cli.set_read_timeout(120);
             auto chat_res = cli.Post("/v1/chat/completions", h, chat_req.dump(), "application/json");

             if (chat_res && chat_res->status == 200) {
                 auto rj = mysti::json::parse(chat_res->body);
                 if (rj.contains("choices") && !rj["choices"].empty()) {
                     std::string content = rj["choices"][0]["message"].value("content", "");
                     std::string json_part = extract_json_block(content);
                     
                     if (!json_part.empty()) {
                         auto styles_json = mysti::json::parse(json_part);
                         mysti::json styles_arr = mysti::json::array();
                         
                         if (styles_json.is_array()) {
                             styles_arr = styles_json;
                         } else if (styles_json.is_object()) {
                             if (styles_json.contains("styles")) {
                                 styles_arr = styles_json["styles"];
                             } else if (styles_json.contains("name")) {
                                 styles_arr.push_back(styles_json);
                             }
                         }

                         int saved_count = 0;
                         std::vector<mysti::Style> new_styles;
                         for (const auto& s_obj : styles_arr) {
                             if (!s_obj.is_object()) continue;
                             mysti::Style s;
                             s.name = s_obj.value("name", "");
                             s.prompt = s_obj.value("prompt", "");
                             s.negative_prompt = s_obj.value("negative_prompt", "");
                             
                             if (!s.name.empty() && !s.prompt.empty()) {
                                 if (s.prompt.find("{prompt}") == std::string::npos) {
                                     s.prompt = "{prompt}, " + s.prompt;
                                 }
                                 g_db->save_style(s);
                                 new_styles.push_back(s);
                                 saved_count++;
                             }
                         }

                         // Trigger preview generation for each new style in background
                         if (!new_styles.empty()) {
                             std::thread([new_styles, out_dir = svr_params.output_dir]() {
                                 for (const auto& s : new_styles) {
                                     generate_style_preview(s, out_dir);
                                 }
                             }).detach();
                         }
                         
                         // Return updated styles list
                         auto all_styles = g_db->get_styles();
                         res.set_content(all_styles.dump(), "application/json");
                         return;
                     }
                 }
             }
             
             res.status = 500;
             res.set_content(R"({"error":"Failed to extract styles from LLM"})", "application/json");

        } catch(const std::exception& e) {
            res.status = 500; 
            mysti::json err; err["error"] = e.what();
            res.set_content(err.dump(), "application/json");
        }
    });

    svr.Post("/v1/styles/previews/fix", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        auto all_styles_json = g_db->get_styles();
        std::vector<mysti::Style> missing;
        for (auto& sj : all_styles_json) {
            if (sj.value("preview_path", "").empty()) {
                mysti::Style s;
                s.name = sj["name"];
                s.prompt = sj["prompt"];
                s.negative_prompt = sj.value("negative_prompt", "");
                missing.push_back(s);
            }
        }

        if (!missing.empty()) {
            std::thread([missing, out_dir = svr_params.output_dir]() {
                for (const auto& s : missing) {
                    generate_style_preview(s, out_dir);
                }
            }).detach();
        }

        res.set_content(mysti::json({{"count", missing.size()}}).dump(), "application/json");
    });

    svr.Delete("/v1/styles", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string name = j.value("name", "");
            if (name.empty()) { res.status = 400; return; }
            g_db->delete_style(name);
            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/tags", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            std::string tag = j.value("tag", "");
            if (uuid.empty() || tag.empty()) { res.status = 400; return; }
            g_db->add_tag(uuid, tag, "user");
            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete("/v1/history/tags", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            std::string tag = j.value("tag", "");
            if (uuid.empty() || tag.empty()) { res.status = 400; return; }
            g_db->remove_tag(uuid, tag);
            // Auto-cleanup unused tags
            g_db->delete_unused_tags();
            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/tags/cleanup", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        g_db->delete_unused_tags();
        res.set_content(R"({"status":"success"})", "application/json");
    });

    svr.Post("/v1/history/favorite", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            bool favorite = j.value("favorite", false);
            if (uuid.empty()) { res.status = 400; return; }
            g_db->set_favorite(uuid, favorite);
            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/rating", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            int rating = j.value("rating", 0);
            if (uuid.empty()) { res.status = 400; return; }
            g_db->set_rating(uuid, rating);
            res.set_content(R"({"status":"success"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete(R"(/v1/history/images/([^/]+))", [&](const httplib::Request& req, httplib::Response& res) {
        if (!g_db) { res.status = 500; return; }
        std::string uuid = req.matches[1];
        bool delete_file = req.has_param("delete_file") && req.get_param_value("delete_file") == "true";
        
        if (delete_file) {
            std::string path_url = g_db->get_generation_filepath(uuid);
            if (!path_url.empty()) {
                // path_url is like "/outputs/img-....png"
                // We need to resolve it relative to the output_dir
                // Or just use the filename if output_dir is standard.
                // Best is to resolve it properly.
                
                // Assuming standard layout where /outputs/ maps to svr_params.output_dir
                if (path_url.find("/outputs/") == 0) {
                    std::string filename = path_url.substr(9); // len("/outputs/")
                    fs::path p = fs::path(svr_params.output_dir) / filename;
                    try {
                        if (fs::exists(p)) {
                            fs::remove(p);
                            // Also try to remove .txt or .json sidecar
                            auto txt_p = p; txt_p.replace_extension(".txt");
                            if (fs::exists(txt_p)) fs::remove(txt_p);
                            auto json_p = p; json_p.replace_extension(".json");
                            if (fs::exists(json_p)) fs::remove(json_p);
                            LOG_INFO("Deleted file: %s", p.string().c_str());
                        }
                    } catch(const std::exception& e) {
                        LOG_ERROR("Failed to delete file %s: %s", p.string().c_str(), e.what());
                    }
                }
            }
        }

        g_db->remove_generation(uuid);
        res.set_content(R"({"status":"success"})", "application/json");
    });

    svr.Post("/v1/images/generations", intercept_sd_generate);
    svr.Post("/v1/images/edits", proxy_sd);
    svr.Get("/v1/progress", proxy_sd);
    svr.Get("/v1/config", proxy_sd);
    svr.Post("/v1/config", proxy_sd);
    svr.Get("/v1/stream/progress", proxy_sd); 
    
    svr.Get("/outputs/previews/(.*)", [&](const httplib::Request& req, httplib::Response& res) {
        std::string filename = req.matches[1];
        fs::path p = fs::path(svr_params.output_dir) / "previews" / filename;
        if (fs::exists(p) && fs::is_regular_file(p)) {
            std::ifstream ifs(p.string(), std::ios::binary);
            std::string content((std::istreambuf_iterator<char>(ifs)), (std::istreambuf_iterator<char>()));
            std::string ext = p.extension().string();
            std::string mime = "image/png";
            if (ext == ".jpg" || ext == ".jpeg") mime = "image/jpeg";
            res.set_content(content, mime);
        } else {
            res.status = 404;
        }
    });

    svr.Get("/outputs/(.*)", proxy_sd);

    // LLM Routes
    svr.Get("/v1/llm/models", proxy_llm);
    svr.Post("/v1/chat/completions", proxy_llm);
    svr.Post("/v1/completions", proxy_llm);
    svr.Post("/v1/embeddings", proxy_llm);
    svr.Post("/v1/tokenize", proxy_llm);
    svr.Post("/v1/detokenize", proxy_llm);
    svr.Post("/v1/llm/load", intercept_llm_load);
    svr.Post("/v1/llm/unload", proxy_llm);

    svr.Get("/health", [&](const httplib::Request& req, httplib::Response& res) {
        bool sd_ok = false;
        bool llm_ok = false;
        if (g_health_svc) {
            sd_ok = g_health_svc->is_sd_alive();
            llm_ok = g_health_svc->is_llm_alive();
        }
        
        float sd_vram = 0;
        float llm_vram = 0;

        httplib::Headers headers;
        if (!g_internal_token.empty()) {
            headers.emplace("X-Internal-Token", g_internal_token);
        }

        if (sd_ok) {
            httplib::Client cli("127.0.0.1", sd_port);
            if (auto res_w = cli.Get("/internal/health", headers)) {
                try {
                    auto j_w = mysti::json::parse(res_w->body);
                    sd_vram = j_w.value("vram_gb", 0.0f);
                } catch (...) {}
            }
        }

        if (llm_ok) {
            httplib::Client cli("127.0.0.1", llm_port);
            if (auto res_w = cli.Get("/internal/health", headers)) {
                try {
                    auto j_w = mysti::json::parse(res_w->body);
                    llm_vram = j_w.value("vram_gb", 0.0f);
                } catch (...) {}
            }
        }

        mysti::json j;
        j["status"] = (sd_ok && llm_ok) ? "ok" : "degraded";
        j["sd_worker"] = {{"status", sd_ok ? "running" : "stopped"}, {"vram_gb", sd_vram}};
        j["llm_worker"] = {{"status", llm_ok ? "running" : "stopped"}, {"vram_gb", llm_vram}};
        j["vram_total_gb"] = get_total_vram_gb();
        j["vram_free_gb"] = get_free_vram_gb();
        
        res.set_content(j.dump(), "application/json");
    });

    LOG_INFO("Orchestrator listening on %s:%d", svr_params.listen_ip.c_str(), svr_params.listen_port);
    svr.listen(svr_params.listen_ip, svr_params.listen_port);

    // Shutdown
    is_shutting_down = true;
    if (g_tagging_svc) g_tagging_svc->stop();
    if (g_health_svc) g_health_svc->stop();
    
    pm.terminate(sd_process);
    pm.terminate(llm_process);

    return 0;
}
