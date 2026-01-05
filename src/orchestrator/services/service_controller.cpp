#include "service_controller.hpp"
#include "proxy.hpp"
#include "sd/api_utils.hpp"
#include <fstream>
#include <iostream>
#include <regex>

namespace mysti {

ServiceController::ServiceController(std::shared_ptr<Database> db,
                                     std::shared_ptr<ResourceManager> res_mgr,
                                     std::shared_ptr<WsManager> ws_mgr,
                                     std::shared_ptr<ToolService> tool_svc,
                                     int sd_port, int llm_port,
                                     const std::string& token)
    : m_db(db), m_res_mgr(res_mgr), m_ws_mgr(ws_mgr), m_tool_svc(tool_svc),
      m_sd_port(sd_port), m_llm_port(llm_port), m_token(token) {}

void ServiceController::generate_style_preview(Style style, std::string output_dir) {
    if (style.prompt.empty()) return;

    std::string subject = "a generic test subject";
    std::string final_prompt = style.prompt;
    if (final_prompt.find("{prompt}") != std::string::npos) {
        final_prompt = std::regex_replace(final_prompt, std::regex("\\{prompt\\}"), subject);
    } else {
        final_prompt = subject + ", " + final_prompt;
    }

    try {
        httplib::Client cli("127.0.0.1", m_sd_port);
        cli.set_read_timeout(120);
        httplib::Headers h;
        if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);

        int preview_steps = 15;
        float preview_cfg = 7.0f;

        if (auto c_res = cli.Get("/v1/config", h)) {
            auto c_j = mysti::json::parse(c_res->body);
            std::string model_path = c_j.value("model", "");
            if (!model_path.empty() && m_db) {
                auto meta = m_db->get_model_metadata(model_path);
                if (!meta.empty()) {
                    if (meta.contains("sample_steps") && meta["sample_steps"].is_number()) preview_steps = meta["sample_steps"].get<int>();
                    if (meta.contains("cfg_scale") && meta["cfg_scale"].is_number()) preview_cfg = meta["cfg_scale"].get<float>();
                }
            }
        }
        
        mysti::json req;
        req["prompt"] = final_prompt;
        req["negative_prompt"] = style.negative_prompt;
        req["width"] = 512;
        req["height"] = 512;
        req["sample_steps"] = preview_steps;
        req["cfg_scale"] = preview_cfg;
        req["n"] = 1;
        req["save_image"] = false; 

        auto res = cli.Post("/v1/images/generations", h, req.dump(), "application/json");
        if (res && res->status == 200) {
            auto j = mysti::json::parse(res->body);
            if (j.contains("data") && !j["data"].empty()) {
                std::string url = j["data"][0].value("url", "");
                if (!url.empty()) {
                    fs::path preview_dir = fs::path(output_dir) / "previews";
                    if (!fs::exists(preview_dir)) fs::create_directories(preview_dir);

                    std::string filename = "style_" + style.name + ".png";
                    std::replace(filename.begin(), filename.end(), ' ', '_');
                    fs::path filepath = preview_dir / filename;

                    // Map URL to local path
                    std::string rel_path = url;
                    if (rel_path.find("/outputs/") == 0) {
                        rel_path = rel_path.substr(9);
                    }
                    fs::path source_path = fs::path(output_dir) / rel_path;

                    if (fs::exists(source_path)) {
                        fs::copy_file(source_path, filepath, fs::copy_options::overwrite_existing);

                        style.preview_path = "/outputs/previews/" + filepath.filename().string();
                        if (m_db) m_db->save_style(style);
                    }
                }
            }
        }
    } catch (...) {}
}

void ServiceController::generate_model_preview(std::string model_id, std::string output_dir) {
    if (model_id.empty() || !m_db) return;

    auto meta = m_db->get_model_metadata(model_id);
    if (meta.empty()) return;

    std::string type = meta.value("type", "");
    std::string name = meta.value("name", model_id);
    std::string trigger = meta.value("trigger_word", "");
    
    // We generate a preview using the trigger word if it's a LoRA
    std::string prompt = "a high quality portrait";
    if (type == "lora" && !trigger.empty()) {
        prompt = trigger + ", " + prompt;
    } else if (type == "lora") {
        // No trigger word, just the generic prompt with the LoRA tag
        fs::path p(model_id);
        std::string stem = p.stem().string();
        prompt = prompt + " <lora:" + stem + ":1.0>";
    }

    try {
        httplib::Client cli("127.0.0.1", m_sd_port);
        cli.set_read_timeout(120);
        httplib::Headers h;
        if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);

        mysti::json req;
        req["prompt"] = prompt;
        req["width"] = 512;
        req["height"] = 512;
        req["sample_steps"] = 15;
        req["n"] = 1;
        req["save_image"] = false;

        auto res = cli.Post("/v1/images/generations", h, req.dump(), "application/json");
        if (res && res->status == 200) {
            auto j = mysti::json::parse(res->body);
            if (j.contains("data") && !j["data"].empty()) {
                std::string url = j["data"][0].value("url", "");
                if (!url.empty()) {
                    fs::path preview_dir = fs::path(output_dir) / "previews";
                    if (!fs::exists(preview_dir)) fs::create_directories(preview_dir);

                    // Sanitize model_id for filename
                    std::string safe_id = model_id;
                    std::replace(safe_id.begin(), safe_id.end(), '/', '_');
                    std::replace(safe_id.begin(), safe_id.end(), '\\', '_');
                    std::replace(safe_id.begin(), safe_id.end(), ':', '_');
                    
                    std::string filename = "model_" + safe_id + ".png";
                    fs::path filepath = preview_dir / filename;

                    // Map URL to local path
                    std::string rel_path = url;
                    if (rel_path.find("/outputs/") == 0) {
                        rel_path = rel_path.substr(9);
                    }
                    fs::path source_path = fs::path(output_dir) / rel_path;

                    if (fs::exists(source_path)) {
                        fs::copy_file(source_path, filepath, fs::copy_options::overwrite_existing);

                        meta["preview_path"] = "/outputs/previews/" + filepath.filename().string();
                        m_db->save_model_metadata(model_id, meta);
                    }
                }
            }
        }
    } catch (...) {}
}

bool ServiceController::ensure_sd_model_loaded(const std::string& model_id, const SDSvrParams& params) {
    if (model_id.empty()) return false;
    
    std::unique_lock<std::mutex> lock(m_load_mutex);
    if (m_active_sd_model == model_id && m_sd_loaded) return true;

    // Check if another thread is already loading this model
    if (m_currently_loading_sd == model_id) {
        LOG_INFO("[SmartQueue] Waiting for SD model load: %s", model_id.c_str());
        m_load_cv.wait_for(lock, std::chrono::seconds(60), [this, &model_id]() {
            return m_active_sd_model == model_id && m_sd_loaded;
        });
        return m_active_sd_model == model_id && m_sd_loaded;
    }

    // Start loading
    m_currently_loading_sd = model_id;
    m_sd_loaded = false;
    lock.unlock();

    LOG_INFO("[SmartQueue] Triggering lazy load for SD model: %s", model_id.c_str());
    mysti::json load_req;
    load_req["model_id"] = model_id;
    
    // Check for preset metadata (VAE etc)
    if (m_db) {
        auto meta = m_db->get_model_metadata(model_id);
        if (!meta.empty()) {
            if (meta.contains("vae")) load_req["vae"] = meta["vae"];
            if (meta.contains("clip_l")) load_req["clip_l"] = meta["clip_l"];
            if (meta.contains("clip_g")) load_req["clip_g"] = meta["clip_g"];
            if (meta.contains("t5xxl")) load_req["t5xxl"] = meta["t5xxl"];
        }
    }

    httplib::Client cli("127.0.0.1", m_sd_port);
    cli.set_read_timeout(120);
    httplib::Headers h;
    if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);
    
    auto res = cli.Post("/v1/models/load", h, load_req.dump(), "application/json");
    
    lock.lock();
    m_currently_loading_sd = "";
    if (res && res->status == 200) {
        m_active_sd_model = model_id;
        m_sd_loaded = true;
        m_last_sd_model_req_body = load_req.dump();
        LOG_INFO("[SmartQueue] SD model loaded: %s", model_id.c_str());
        m_load_cv.notify_all();
        return true;
    } else {
        LOG_ERROR("[SmartQueue] Failed to lazy load SD model: %s", model_id.c_str());
        m_load_cv.notify_all();
        return false;
    }
}

bool ServiceController::ensure_llm_loaded(const std::string& model_id, const SDSvrParams& params) {
    if (model_id.empty()) return false;
    
    std::unique_lock<std::mutex> lock(m_load_mutex);
    if (m_active_llm_model == model_id && m_llm_loaded) return true;

    if (m_currently_loading_llm == model_id) {
        m_load_cv.wait_for(lock, std::chrono::seconds(60), [this, &model_id]() {
            return m_active_llm_model == model_id && m_llm_loaded;
        });
        return m_active_llm_model == model_id && m_llm_loaded;
    }

    m_currently_loading_llm = model_id;
    m_llm_loaded = false;
    lock.unlock();

    LOG_INFO("[SmartQueue] Triggering lazy load for LLM: %s", model_id.c_str());
    
    // Estimate size for arbitration
    float estimated_gb = 4.0f;
    uint64_t model_bytes = get_file_size((fs::path(params.model_dir) / model_id).string());
    if (model_bytes > 0) {
        estimated_gb = ((float)model_bytes / (1024.0f * 1024.0f * 1024.0f)) + 1.0f;
    }

    if (!m_res_mgr->prepare_for_llm_load(estimated_gb)) {
        lock.lock();
        m_currently_loading_llm = "";
        m_load_cv.notify_all();
        return false;
    }

    mysti::json load_req;
    load_req["model_id"] = model_id;
    
    // Attempt to lookup preset config for full context (mmproj etc)
    if (m_db) {
        auto presets = m_db->get_llm_presets();
        for (const auto& p : presets) {
            if (p.is_object() && (p["model_path"] == model_id || p["model_path"] == (fs::path(params.model_dir) / model_id).string())) {
                if (p.contains("mmproj_path") && !p["mmproj_path"].get<std::string>().empty()) {
                    load_req["mmproj_id"] = p["mmproj_path"];
                }
                if (p.contains("n_ctx")) {
                    load_req["n_ctx"] = p["n_ctx"];
                }
                LOG_INFO("[SmartQueue] Using preset config for %s", model_id.c_str());
                break;
            }
        }
    }
    
    httplib::Client cli("127.0.0.1", m_llm_port);
    cli.set_read_timeout(120);
    httplib::Headers h;
    if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);
    
    auto res = cli.Post("/v1/llm/load", h, load_req.dump(), "application/json");
    
    // Uncommit after worker has finished loading (polling will pick it up soon)
    m_res_mgr->uncommit_vram(estimated_gb * 1.1f);

    lock.lock();
    m_currently_loading_llm = "";
    if (res && res->status == 200) {
        m_active_llm_model = model_id;
        m_llm_loaded = true;
        m_last_llm_model_req_body = load_req.dump();
        LOG_INFO("[SmartQueue] LLM model loaded: %s", model_id.c_str());
        m_load_cv.notify_all();
        return true;
    } else {
        m_load_cv.notify_all();
        return false;
    }
}

bool ServiceController::load_llm_preset(int preset_id, const SDSvrParams& params) {
    if (!m_db) return false;
    auto presets = m_db->get_llm_presets();
    mysti::json selected = nullptr;
    for (auto& p : presets) { if (p["id"] == preset_id) { selected = p; break; } }
    if (selected == nullptr) return false;

    std::string model_id = selected["model_path"];
    LOG_INFO("Loading LLM Preset %d: %s", preset_id, model_id.c_str());
    
    if (ensure_llm_loaded(model_id, params)) {
        m_last_llm_preset_id = preset_id;
        m_db->set_config("last_llm_preset_id", std::to_string(preset_id));
        return true;
    }
    return false;
}

void ServiceController::load_last_presets(const SDSvrParams& params) {
    if (!m_db) return;

    std::string last_img = m_db->get_config("last_image_preset_id");
    std::string last_llm = m_db->get_config("last_llm_preset_id");

    if (!last_img.empty()) {
        try {
            int id = std::stoi(last_img);
            auto presets = m_db->get_image_presets();
            for (auto& p : presets) {
                if (p["id"] == id) {
                    LOG_INFO("Restoring last Image Preset: %s", p.value("name", "unnamed").c_str());
                    ensure_sd_model_loaded(p["unet_path"], params);
                    m_last_image_preset_id = id;
                    break;
                }
            }
        } catch(...) {}
    }

    if (!last_llm.empty()) {
        try {
            int id = std::stoi(last_llm);
            load_llm_preset(id, params);
        } catch(...) {}
    }
}

void ServiceController::notify_model_loaded(const std::string& type, const std::string& model_id) {
    {
        std::lock_guard<std::mutex> lock(m_load_mutex);
        if (type == "sd") {
            m_active_sd_model = model_id;
            m_sd_loaded = !model_id.empty();
        } else if (type == "llm") {
            m_active_llm_model = model_id;
            m_llm_loaded = !model_id.empty();
        }
        m_load_cv.notify_all();
    }
    
    // Sync tracking state for restoration
    if (!model_id.empty()) {
        std::lock_guard<std::mutex> lock(m_state_mutex);
        mysti::json j;
        j["model_id"] = model_id;
        if (type == "sd") {
            if (m_last_sd_model_req_body.empty()) m_last_sd_model_req_body = j.dump();
            
            // Auto-detect matching preset
            if (m_db) {
                auto presets = m_db->get_image_presets();
                for (auto& p : presets) {
                    if (p["unet_path"] == model_id) {
                        int id = p["id"];
                        if (id != m_last_image_preset_id) {
                            m_last_image_preset_id = id;
                            m_db->set_config("last_image_preset_id", std::to_string(id));
                        }
                        break;
                    }
                }
            }
        } else if (type == "llm") {
            if (m_last_llm_model_req_body.empty()) m_last_llm_model_req_body = j.dump();

            // Auto-detect matching preset
            if (m_db) {
                auto presets = m_db->get_llm_presets();
                for (auto& p : presets) {
                    if (p["model_path"] == model_id) {
                        int id = p["id"];
                        if (id != m_last_llm_preset_id) {
                            m_last_llm_preset_id = id;
                            m_db->set_config("last_llm_preset_id", std::to_string(id));
                        }
                        break;
                    }
                }
            }
        }
    }
}

void ServiceController::register_routes(httplib::Server& svr, const SDSvrParams& params) {
    if (!svr.set_mount_point("/app", params.app_dir)) {
        LOG_WARN("failed to mount %s directory", params.app_dir.c_str());
    }

    svr.Get("/", [](const httplib::Request&, httplib::Response& res) {
        res.set_redirect("/app/");
    });

    svr.set_error_handler([params](const httplib::Request& req, httplib::Response& res) {
        if (req.path.find("/app/") == 0 && res.status == 404) {
            std::string index_path = (fs::path(params.app_dir) / "index.html").string();
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

    auto proxy_sd = [this](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", m_sd_port, "", m_token);
    };

    auto proxy_llm = [this](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", m_llm_port, "", m_token);
    };

    svr.Post("/v1/models/load", [this, params](const httplib::Request& req, httplib::Response& res) {
        LOG_INFO("Request: POST /v1/models/load, Body: %s", req.body.c_str());
        std::string model_id = "";
        try {
            auto j = mysti::json::parse(req.body);
            model_id = j.value("model_id", "");
        } catch(...) {}

        if (model_id.empty()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"model_id required\"})", "application/json");
            return;
        }

        // Use ensure helper which handles queueing
        if (ensure_sd_model_loaded(model_id, params)) {
            res.status = 200;
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } else {
            res.status = 500;
            res.set_content(R"({\"error\":\"Failed to load SD model\"})", "application/json");
        }
    });

    svr.Post("/v1/llm/load", [this, params](const httplib::Request& req, httplib::Response& res) {
        LOG_INFO("Request: POST /v1/llm/load, Body: %s", req.body.c_str());
        std::string model_id = "";
        try {
            auto j = mysti::json::parse(req.body);
            model_id = j.value("model_id", "");
        } catch(...) {}

        if (model_id.empty()) {
            res.status = 400;
            res.set_content(R"({\"error\":\"model_id required\"})", "application/json");
            return;
        }

        if (ensure_llm_loaded(model_id, params)) {
            res.status = 200;
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } else {
            res.status = 500;
            res.set_content(R"({\"error\":\"Failed to load LLM model\"})", "application/json");
        }
    });

    svr.Post("/v1/images/generations", [this, params](const httplib::Request& req, httplib::Response& res) {
        RequestIdGuard request_id_guard("req-" + generate_random_token(8));

        struct GenActiveGuard {
            std::function<void(bool)>& cb;
            GenActiveGuard(std::function<void(bool)>& c) : cb(c) { if(cb) cb(true); }
            ~GenActiveGuard() { if(cb) cb(false); }
        };
        GenActiveGuard gen_guard(m_generation_active_cb);

        LOG_INFO("Request: POST /v1/images/generations");
        std::string modified_body = req.body;
        
        // 1. Ensure Model is Loaded (Lazy Load)
        std::string requested_model_id = "";
        {
            std::lock_guard<std::mutex> lock(m_state_mutex);
            if (!m_last_sd_model_req_body.empty()) {
                try {
                    requested_model_id = mysti::json::parse(m_last_sd_model_req_body).value("model_id", "");
                } catch(...) {}
            }
        }
        
        // Check if request body overrides model (usually not in our UI, but for API compatibility)
        try {
            auto j_req = mysti::json::parse(req.body);
            if (j_req.contains("model_id")) requested_model_id = j_req["model_id"];
        } catch(...) {}

        if (!requested_model_id.empty()) {
            if (!ensure_sd_model_loaded(requested_model_id, params)) {
                res.status = 503;
                res.set_content(R"({\"error\":\"Model not loaded and lazy load failed.\"})", "application/json");
                return;
            }
        }

        // 2. Estimate VRAM and Arbitrate
        int req_width = 512, req_height = 512, req_batch = 1;
        bool req_hires = false;
        float req_hires_factor = 2.0f;
        try {
            auto j = mysti::json::parse(req.body);
            req_width = j.value("width", 512);
            req_height = j.value("height", 512);
            req_batch = j.value("n", 1);
            req_hires = j.value("hires_fix", false);
            req_hires_factor = j.value("hires_upscale_factor", 2.0f);
        } catch(...) {}

        float base_gb = 4.5f;
        float clip_gb = 0.0f;
        fs::path model_path = fs::path(params.model_dir) / requested_model_id;
        uint64_t size_bytes = get_file_size(model_path.string());
        if (size_bytes > 0) {
            base_gb = (float)size_bytes / (1024.0f * 1024.0f * 1024.0f) + 0.5f;
        }

        float mp = (float)(req_width * req_height) / (1024.0f * 1024.0f);
        float per_mp_factor = 1.5f; 
        if (requested_model_id.find("z_image") != std::string::npos || requested_model_id.find("turbo") != std::string::npos) {
             per_mp_factor = 1.2f;
        }
        
        float resolution_gb = mp * per_mp_factor * req_batch; 
        if (req_hires) resolution_gb += (mp * req_hires_factor * req_hires_factor) * 1.5f;

        // Arbitration (this now COMMITS the VRAM if successful)
        ArbitrationResult arb = m_res_mgr->prepare_for_sd_generation(base_gb + resolution_gb, mp, requested_model_id, base_gb, clip_gb);

        if (!arb.success) {
            res.status = 503;
            res.set_content(R"({\"error\":\"Resource arbitration failed. VRAM exhausted.\"})", "application/json");
            return;
        }

        // 3. Apply Mitigations and Proxy
        try {
            auto j = mysti::json::parse(modified_body);
            j["clip_on_cpu"] = arb.request_clip_offload;
            j["vae_tiling"] = arb.request_vae_tiling;

            // Apply Preset Overrides
            if (m_db) {
                auto meta = m_db->get_model_metadata(requested_model_id);
                if (meta.contains("memory")) {
                    auto mem = meta["memory"];
                    if (mem.value("force_clip_cpu", false)) j["clip_on_cpu"] = true;
                    if (mem.value("force_vae_tiling", false)) j["vae_tiling"] = true;
                }
            }
            
            modified_body = j.dump();
        } catch(...) {}

        httplib::Request mod_req = req;
        mod_req.body = modified_body;
        Proxy::forward_request(mod_req, res, "127.0.0.1", m_sd_port, "", m_token);
        
        // 4. Uncommit VRAM
        m_res_mgr->uncommit_vram(arb.committed_gb);
        
        if (res.status == 200 && m_db) {
            try {
                auto res_json = mysti::json::parse(res.body);
                float peak_vram = res_json.value("vram_peak_gb", 0.0f);
                float delta_vram = res_json.value("vram_delta_gb", 0.0f);
                if (peak_vram > 0) {
                    LOG_INFO("Image generation completed. Peak VRAM: %.2f GB (Delta: %+.2f GB)", peak_vram, delta_vram);
                } else {
                    LOG_INFO("Image generation completed successfully.");
                }

                auto req_json = mysti::json::parse(modified_body);
                if (res_json.contains("data") && res_json["data"].is_array()) {
                    for (const auto& item : res_json["data"]) {
                        std::string file_path = item.value("url", "");
                        long long item_seed = item.value("seed", -1LL);
                        std::string uuid = res_json.value("id", ""); 

                        if (uuid.empty() && !file_path.empty()) {
                            size_t last_slash = file_path.find_last_of('/');
                            uuid = (last_slash != std::string::npos) ? file_path.substr(last_slash + 1) : file_path;
                        }

                        if (!uuid.empty() && !file_path.empty()) {
                            Generation gen;
                            gen.uuid = uuid;
                            gen.file_path = file_path;
                            gen.prompt = req_json.value("prompt", "");
                            gen.negative_prompt = req_json.value("negative_prompt", "");
                            gen.seed = item_seed;
                            gen.width = req_json.value("width", 512);
                            gen.height = req_json.value("height", 512);
                            gen.steps = req_json.contains("sample_steps") ? req_json["sample_steps"].get<int>() : req_json.value("steps", 20);
                            gen.cfg_scale = req_json.value("cfg_scale", 7.0f);
                            gen.generation_time = res_json.value("generation_time", 0.0);
                            gen.params_json = modified_body;
                            {
                                std::lock_guard<std::mutex> lock(m_state_mutex);
                                if (!m_last_sd_model_req_body.empty()) {
                                    try { gen.model_id = mysti::json::parse(m_last_sd_model_req_body).value("model_id", ""); } catch(...) {}
                                }
                            }
                            int gen_id = m_db->insert_generation(gen);
                            if (gen_id > 0) {
                                mysti::json job_payload;
                                job_payload["generation_id"] = gen_id;
                                job_payload["image_path"] = gen.file_path;
                                m_db->add_job("generate_thumbnail", job_payload, 10);
                            }
                        }
                    }
                    if (m_on_generation) m_on_generation();
                }
            } catch (...) {}
        }
    });

    svr.Post("/v1/chat/completions", [this, params](const httplib::Request& req, httplib::Response& res) {
        std::string model_id = "";
        try {
            auto j = mysti::json::parse(req.body);
            model_id = j.value("model", "");
        } catch(...) {}

        if (!model_id.empty()) {
            ensure_llm_loaded(model_id, params);
        }
        Proxy::forward_request(req, res, "127.0.0.1", m_llm_port, "", m_token);
    });

    svr.Post("/v1/completions", [this, params](const httplib::Request& req, httplib::Response& res) {
        std::string model_id = "";
        try {
            auto j = mysti::json::parse(req.body);
            model_id = j.value("model", "");
        } catch(...) {}

        if (!model_id.empty()) {
            ensure_llm_loaded(model_id, params);
        }
        Proxy::forward_request(req, res, "127.0.0.1", m_llm_port, "", m_token);
    });

    svr.Post("/v1/embeddings", proxy_llm);
    svr.Post("/v1/tokenize", proxy_llm);
    svr.Post("/v1/detokenize", proxy_llm);
    svr.Post("/v1/llm/unload", proxy_llm);
    svr.Post("/v1/llm/offload", proxy_llm);

    svr.Get("/v1/models", proxy_sd);
    svr.Get("/v1/config", proxy_sd);
    svr.Post("/v1/config", proxy_sd);
    svr.Post("/v1/upscale/load", proxy_sd);
    svr.Post("/v1/images/upscale", proxy_sd);
    
    svr.Get("/v1/history/images", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { Proxy::forward_request(req, res, "127.0.0.1", m_sd_port, "", m_token); return; }
        int limit = req.has_param("limit") ? std::stoi(req.get_param_value("limit")) : 50;
        std::string cursor = req.has_param("cursor") ? req.get_param_value("cursor") : "";
        int min_rating = req.has_param("min_rating") ? std::stoi(req.get_param_value("min_rating")) : 0;
        std::vector<std::string> tags;
        auto count = req.get_param_value_count("tag");
        for (size_t i = 0; i < count; ++i) tags.push_back(req.get_param_value("tag", i));
        std::string model = req.has_param("model") ? req.get_param_value("model") : "";
        res.set_content(m_db->get_generations(limit, cursor, tags, model, min_rating).dump(), "application/json");
    });

    svr.Get("/v1/history/search", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        std::string query = req.get_param_value("q");
        int limit = req.has_param("limit") ? std::stoi(req.get_param_value("limit")) : 50;
        if (query.empty()) {
            res.set_content("[]", "application/json");
            return;
        }
        res.set_content(m_db->search_generations(query, limit).dump(), "application/json");
    });

    svr.Get("/v1/history/tags", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        res.set_content(m_db->get_tags().dump(), "application/json");
    });

    svr.Get("/v1/models/metadata", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        res.set_content(m_db->get_all_models_metadata().dump(), "application/json");
    });

    svr.Get(R"(/v1/models/metadata/(.*))", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        res.set_content(m_db->get_model_metadata(req.matches[1]).dump(), "application/json");
    });

    svr.Post("/v1/models/metadata", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string model_id = j.value("id", "");
            if (model_id.empty()) { res.status = 400; return; }
            m_db->save_model_metadata(model_id, j["metadata"]);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/models/metadata/preview", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string model_id = j.value("id", "");
            if (model_id.empty()) { res.status = 400; return; }
            std::thread(&ServiceController::generate_model_preview, this, model_id, params.output_dir).detach();
            res.set_content(R"({\"status\":\"success\",\"message\":\"Preview generation started in background\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Get("/v1/styles", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        res.set_content(m_db->get_styles().dump(), "application/json");
    });

    svr.Post("/v1/styles", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            Style s;
            s.name = j.value("name", "");
            s.prompt = j.value("prompt", "");
            s.negative_prompt = j.value("negative_prompt", "");
            s.preview_path = j.value("preview_path", "");
            if (s.name.empty()) { res.status = 400; return; }
            m_db->save_style(s);
            std::thread(&ServiceController::generate_style_preview, this, s, params.output_dir).detach();
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/styles/extract", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string input_prompt = j.value("prompt", "");
            if (input_prompt.empty()) {
                res.status = 400;
                res.set_content(R"({\"error\":\"Prompt is required\"})", "application/json");
                return;
            }

            httplib::Client cli("127.0.0.1", m_llm_port);
            cli.set_connection_timeout(2);
            httplib::Headers h;
            if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);

            mysti::json chat_req;
            chat_req["messages"] = mysti::json::array({
                {{"role", "system"}, {"content", params.style_extractor_system_prompt}},
                {{"role", "user"}, {"content", input_prompt}}
            });
            chat_req["temperature"] = 0.2;
            chat_req["max_tokens"] = 1024;
            chat_req["response_format"] = {{"type", "json_object"}};

            cli.set_read_timeout(180);
            auto chat_res = cli.Post("/v1/chat/completions", h, chat_req.dump(), "application/json");

            if (chat_res && chat_res->status == 200) {
                auto rj = mysti::json::parse(chat_res->body);
                if (rj.contains("choices") && !rj["choices"].empty()) {
                    std::string content = rj["choices"][0]["message"].value("content", "");
                    std::string json_part = extract_json_block(content);

                    if (!json_part.empty()) {
                        mysti::json styles_arr = mysti::json::array();
                        try {
                            auto styles_json = mysti::json::parse(json_part);
                            if (styles_json.is_array()) {
                                styles_arr = styles_json;
                            } else if (styles_json.is_object()) {
                                if (styles_json.contains("styles")) {
                                    styles_arr = styles_json["styles"];
                                } else if (styles_json.contains("name")) {
                                    styles_arr.push_back(styles_json);
                                }
                            }
                        } catch (...) { throw; } 

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
                                m_db->save_style(s);
                                new_styles.push_back(s);
                            }
                        }

                        if (!new_styles.empty()) {
                            std::thread([this, new_styles, out_dir = params.output_dir]() {
                                for (const auto& s : new_styles) {
                                    generate_style_preview(s, out_dir);
                                }
                            }).detach();
                        }

                        auto all_styles = m_db->get_styles();
                        res.set_content(all_styles.dump(), "application/json");
                        return;
                    }
                }
            }
            res.status = 500;
            res.set_content(R"({\"error\":\"Failed to extract styles from LLM\"})", "application/json");
        } catch(const std::exception& e) {
            res.status = 500;
            mysti::json err; err["error"] = e.what();
            res.set_content(err.dump(), "application/json");
        }
    });

    svr.Post("/v1/styles/previews/fix", [this, params](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        auto all_styles_json = m_db->get_styles();
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
            std::thread([this, missing, out_dir = params.output_dir]() {
                for (const auto& s : missing) {
                    generate_style_preview(s, out_dir);
                }
            }).detach();
        }
        res.set_content(mysti::json({{"count", missing.size()}}).dump(), "application/json");
    });

    svr.Delete("/v1/styles", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string name = j.value("name", "");
            if (name.empty()) { res.status = 400; return; }
            m_db->delete_style(name);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/tags", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            std::string tag = j.value("tag", "");
            if (uuid.empty() || tag.empty()) { res.status = 400; return; }
            m_db->add_tag(uuid, tag, "user");
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete("/v1/history/tags", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            std::string tag = j.value("tag", "");
            if (uuid.empty() || tag.empty()) { res.status = 400; return; }
            m_db->remove_tag(uuid, tag);
            m_db->delete_unused_tags();
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/tags/cleanup", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        m_db->delete_unused_tags();
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    });

    svr.Post("/v1/history/favorite", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            bool favorite = j.value("favorite", false);
            if (uuid.empty()) { res.status = 400; return; }
            m_db->set_favorite(uuid, favorite);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/history/rating", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            std::string uuid = j.value("uuid", "");
            int rating = j.value("rating", 0);
            if (uuid.empty()) { res.status = 400; return; }
            m_db->set_rating(uuid, rating);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete(R"(/v1/history/images/([^/]+))", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        std::string uuid = req.matches[1];
        bool delete_file = req.has_param("delete_file") && req.get_param_value("delete_file") == "true";

        if (delete_file) {
            std::string path_url = m_db->get_generation_filepath(uuid);
            if (!path_url.empty() && path_url.find("/outputs/") == 0) {
                std::string filename = path_url.substr(9);
                fs::path p = fs::path(params.output_dir) / filename;
                try {
                    if (fs::exists(p)) {
                        fs::remove(p);
                        auto txt_p = p; txt_p.replace_extension(".txt");
                        if (fs::exists(txt_p)) fs::remove(txt_p);
                        auto json_p = p; json_p.replace_extension(".json");
                        if (fs::exists(json_p)) fs::remove(json_p);
                    }
                } catch(...) {}
            }
        }
        m_db->remove_generation(uuid);
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    });

    svr.Post("/v1/images/edits", proxy_sd);
    svr.Get("/v1/progress", proxy_sd);
    svr.Get("/v1/stream/progress", proxy_sd); 
    
    svr.Get("/outputs/previews/([^/]+)", [params](const httplib::Request& req, httplib::Response& res) {
        fs::path p = fs::path(params.output_dir) / "previews" / std::string(req.matches[1]);
        if (fs::exists(p) && fs::is_regular_file(p)) {
            std::ifstream ifs(p.string(), std::ios::binary);
            std::string content((std::istreambuf_iterator<char>(ifs)), (std::istreambuf_iterator<char>()));
            std::string mime = (p.extension() == ".jpg" || p.extension() == ".jpeg") ? "image/jpeg" : "image/png";
            res.set_content(content, mime);
        } else res.status = 404;
    });

    svr.Get("/outputs/(.*)", proxy_sd);
    svr.Get("/v1/llm/models", proxy_llm);
    
    svr.Get("/health", [this](const httplib::Request&, httplib::Response& res) {
        auto status = m_res_mgr->get_vram_status();
        status["status"] = "ok";
        res.set_content(status.dump(), "application/json");
    });

    // --- Presets Endpoints ---

    svr.Get("/v1/presets/image", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        res.set_content(m_db->get_image_presets().dump(), "application/json");
    });

    svr.Post("/v1/presets/image", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            ImagePreset p;
            p.id = j.value("id", 0);
            p.name = j.value("name", "");
            p.unet_path = j.value("unet_path", "");
            p.vae_path = j.value("vae_path", "");
            p.clip_l_path = j.value("clip_l_path", "");
            p.clip_g_path = j.value("clip_g_path", "");
            p.t5xxl_path = j.value("t5xxl_path", "");
            p.vram_weights_mb_estimate = j.value("vram_weights_mb_estimate", 0);
            p.default_params = j.value("default_params", mysti::json::object());
            p.preferred_params = j.value("preferred_params", mysti::json::object());
            
            if (p.name.empty()) { res.status = 400; return; }

            if (p.vram_weights_mb_estimate <= 0) {
                uint64_t total_bytes = 0;
                auto check_size = [&](const std::string& rel_path) {
                    if (rel_path.empty()) return;
                    fs::path full_path = fs::path(params.model_dir) / rel_path;
                    total_bytes += get_file_size(full_path.string());
                };
                check_size(p.unet_path);
                check_size(p.vae_path);
                check_size(p.clip_l_path);
                check_size(p.clip_g_path);
                check_size(p.t5xxl_path);
                if (total_bytes > 0) p.vram_weights_mb_estimate = (int)((total_bytes * 1.05) / (1024 * 1024));
            }
            m_db->save_image_preset(p);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete(R"(/v1/presets/image/(\d+))", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        int id = std::stoi(req.matches[1]);
        m_db->delete_image_preset(id);
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    });

    svr.Get("/v1/presets/llm", [this](const httplib::Request&, httplib::Response& res) {
        if (!m_db) { res.set_content("[]", "application/json"); return; }
        res.set_content(m_db->get_llm_presets().dump(), "application/json");
    });

    svr.Post("/v1/presets/llm", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            LlmPreset p;
            p.id = j.value("id", 0);
            p.name = j.value("name", "");
            p.model_path = j.value("model_path", "");
            p.mmproj_path = j.value("mmproj_path", "");
            p.n_ctx = j.value("n_ctx", 2048);
            p.capabilities = j.value("capabilities", std::vector<std::string>());
            p.role = j.value("role", "Assistant");
            if (p.name.empty() || p.model_path.empty()) { res.status = 400; return; }
            m_db->save_llm_preset(p);
            res.set_content(R"({\"status\":\"success\"})", "application/json");
        } catch(...) { res.status = 400; }
    });

    svr.Delete(R"(/v1/presets/llm/(\d+))", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        int id = std::stoi(req.matches[1]);
        m_db->delete_llm_preset(id);
        res.set_content(R"({\"status\":\"success\"})", "application/json");
    });

    svr.Post("/v1/presets/image/load", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            int id = j.value("id", 0);
            auto presets = m_db->get_image_presets();
            mysti::json selected = nullptr;
            for (auto& p : presets) { if (p["id"] == id) { selected = p; break; } }
            if (selected == nullptr) {
                res.status = 404;
                res.set_content(R"({\"error\":\"preset not found\"})", "application/json");
                return;
            }
            
            std::string model_id = selected["unet_path"];
            if (ensure_sd_model_loaded(model_id, params)) {
                m_last_image_preset_id = id;
                m_db->set_config("last_image_preset_id", std::to_string(id));
                res.status = 200;
                res.set_content(R"({\"status\":\"success\"})", "application/json");
            } else {
                res.status = 500;
                res.set_content(R"({\"error\":\"failed to load preset model\"})", "application/json");
            }
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/presets/llm/load", [this, params](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { res.status = 500; return; }
        try {
            auto j = mysti::json::parse(req.body);
            int id = j.value("id", 0);
            if (load_llm_preset(id, params)) {
                res.status = 200;
                res.set_content(R"({\"status\":\"success\"})", "application/json");
            } else {
                res.status = 500;
                res.set_content(R"({\"error\":\"failed to load llm preset\"})", "application/json");
            }
        } catch(...) { res.status = 400; }
    });

    svr.Post("/v1/tools/execute", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_tool_svc) { 
            res.status = 500; 
            res.set_content(R"({\"error\":\"Tool service not available\"})", "application/json");
            return; 
        }
        try {
            auto j = mysti::json::parse(req.body);
            std::string name = j.value("name", "");
            auto args = j.value("arguments", mysti::json::object());
            auto result = m_tool_svc->execute_tool(name, args);
            res.set_content(result.dump(), "application/json");
        } catch(...) { 
            res.status = 400; 
            res.set_content(R"({\"error\":\"Invalid JSON\"})", "application/json");
        }
    });

    svr.Get("/v1/assistant/config", [params](const httplib::Request&, httplib::Response& res) {
        mysti::json c;
        c["system_prompt"] = params.assistant_system_prompt;
        c["tools"] = {
            {
                {"type", "function"},
                {"function", {
                    {"name", "get_library_items"},
                    {"description", "Retrieve items from the prompt library/gallery by category."},
                    {"parameters", {
                        {"type", "object"},
                        {"properties", {
                            {"category", {{"type", "string"}, {"description", "The category to browse (e.g., 'Style', 'Lighting')"}}}
                        }}
                    }}
                }}
            },
            {
                {"type", "function"},
                {"function", {
                    {"name", "search_history"},
                    {"description", "Search through past generations using keywords."} ,
                    {"parameters", {
                        {"type", "object"},
                        {"properties", {
                            {"query", {{"type", "string"}, {"description", "Keywords to search for"}}}
                        }},
                        {"required", {"query"}}
                    }}
                }}
            },
            {
                {"type", "function"},
                {"function", {
                    {"name", "get_vram_status"},
                    {"description", "Get the current VRAM usage and capacity."} ,
                    {"parameters", {
                        {"type", "object"},
                        {"properties", {}}
                    }}
                }}
            },
            {
                {"type", "function"},
                {"function", {
                    {"name", "update_generation_params"},
                    {"description", "Update the image generation parameters (prompt, steps, size, etc.) in the UI."} ,
                    {"parameters", {
                        {"type", "object"},
                        {"properties", {
                            {"prompt", {{"type", "string"}, {"description", "The positive prompt text."}}},
                            {"negative_prompt", {{"type", "string"}, {"description", "The negative prompt text."}}},
                            {"steps", {{"type", "integer"}, {"description", "Sampling steps (1-100)."}}},
                            {"width", {{"type", "integer"}, {"description", "Image width."}}},
                            {"height", {{"type", "integer"}, {"description", "Image height."}}},
                            {"cfg_scale", {{"type", "number"}, {"description", "CFG Scale."}}}
                        }}
                    }}
                }}
            }
        };
        res.set_content(c.dump(), "application/json");
    });
}

} // namespace mysti