#include "service_controller.hpp"
#include "proxy.hpp"
#include <fstream>
#include <iostream>
#include <regex>

namespace mysti {

static std::string base64_decode_str(const std::string& in) {
    std::string out;
    std::vector<int> T(256, -1);
    const char* chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    for (int i = 0; i < 64; i++) T[(unsigned char)chars[i]] = i;
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
                std::string b64 = j["data"][0].value("b64_json", "");
                if (!b64.empty()) {
                    fs::path preview_dir = fs::path(output_dir) / "previews";
                    if (!fs::exists(preview_dir)) fs::create_directories(preview_dir);

                    std::string filename = "style_" + style.name + ".png";
                    std::replace(filename.begin(), filename.end(), ' ', '_');
                    fs::path filepath = preview_dir / filename;

                    std::string decoded = base64_decode_str(b64);
                    std::ofstream out(filepath, std::ios::binary);
                    out.write(decoded.data(), decoded.size());
                    out.close();

                    style.preview_path = "/outputs/previews/" + filepath.filename().string();
                    if (m_db) m_db->save_style(style);
                }
            }
        }
    } catch (...) {}
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

    svr.Post("/v1/models/load", [this](const httplib::Request& req, httplib::Response& res) {
        std::string modified_body = req.body;
        if (m_db) {
            try {
                auto j = mysti::json::parse(req.body);
                std::string model_id = j.value("model_id", "");
                if (!model_id.empty()) {
                    auto meta = m_db->get_model_metadata(model_id);
                    if (!meta.empty()) {
                        if (meta.contains("vae") && !meta["vae"].get<std::string>().empty()) j["vae"] = meta["vae"];
                        if (meta.contains("llm") && !meta["llm"].get<std::string>().empty()) j["llm"] = meta["llm"];
                        modified_body = j.dump();
                    }
                }
            } catch(...) {}
        }
        httplib::Request mod_req = req;
        mod_req.body = modified_body;
        Proxy::forward_request(mod_req, res, "127.0.0.1", m_sd_port, "", m_token);
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(m_state_mutex);
            m_last_sd_model_req_body = modified_body;
        }
    });

    svr.Post("/v1/llm/load", [this](const httplib::Request& req, httplib::Response& res) {
        Proxy::forward_request(req, res, "127.0.0.1", m_llm_port, "", m_token);
        if (res.status == 200) {
            std::lock_guard<std::mutex> lock(m_state_mutex);
            m_last_llm_model_req_body = req.body;
        }
    });

    svr.Post("/v1/images/generations", [this, params](const httplib::Request& req, httplib::Response& res) {
        m_res_mgr->prepare_for_sd_generation(4.0f);
        std::string modified_body = req.body;
        httplib::Headers h;
        if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);

        if (m_db) {
            try {
                auto req_j = mysti::json::parse(req.body);
                httplib::Client cli("127.0.0.1", m_sd_port);
                if (auto c_res = cli.Get("/v1/config", h)) {
                    auto c_j = mysti::json::parse(c_res->body);
                    std::string active_model_id = c_j.value("model", "");
                    if (!active_model_id.empty()) {
                        auto meta = m_db->get_model_metadata(active_model_id);
                        if (!meta.empty()) {
                            if (!req_j.contains("width") || req_j["width"] == 512) { if (meta.contains("width")) req_j["width"] = meta["width"]; }
                            if (!req_j.contains("height") || req_j["height"] == 512) { if (meta.contains("height")) req_j["height"] = meta["height"]; }
                            int current_steps = req_j.contains("sample_steps") ? req_j["sample_steps"].get<int>() : (req_j.contains("steps") ? req_j["steps"].get<int>() : 0);
                            if (current_steps == 0 || current_steps == 20 || current_steps == 15) {
                                if (meta.contains("sample_steps")) {
                                    req_j["sample_steps"] = meta["sample_steps"];
                                    req_j["steps"] = meta["sample_steps"];
                                }
                            }
                            if (!req_j.contains("cfg_scale") || req_j["cfg_scale"] == 7.0) { if (meta.contains("cfg_scale")) req_j["cfg_scale"] = meta["cfg_scale"]; }
                            modified_body = req_j.dump();
                        }
                    }
                }
            } catch(...) {}
        }

        httplib::Request mod_req = req;
        mod_req.body = modified_body;
        Proxy::forward_request(mod_req, res, "127.0.0.1", m_sd_port, "", m_token);
        
        if (res.status == 200 && m_db) {
            try {
                auto req_json = mysti::json::parse(modified_body);
                auto res_json = mysti::json::parse(res.body);
                std::string uuid = res_json.value("id", ""); 
                std::string file_path = "";
                long long seed = req_json.value("seed", -1LL);
                if (res_json.contains("data") && res_json["data"].is_array() && !res_json["data"].empty()) {
                    file_path = res_json["data"][0].value("url", "");
                    if (seed == -1LL) seed = res_json["data"][0].value("seed", seed);
                }
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
                    gen.seed = seed;
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
                    m_db->insert_generation(gen);
                    if (m_on_generation) m_on_generation();
                }
            } catch (...) {}
        }
    });

    svr.Get("/v1/models", proxy_sd);
    svr.Get("/v1/config", proxy_sd);
    svr.Post("/v1/config", proxy_sd);
    svr.Post("/v1/upscale/load", proxy_sd);
    svr.Post("/v1/images/upscale", proxy_sd);
    
    svr.Get("/v1/history/images", [this](const httplib::Request& req, httplib::Response& res) {
        if (!m_db) { Proxy::forward_request(req, res, "127.0.0.1", m_sd_port, "", m_token); return; }
        int limit = req.has_param("limit") ? std::stoi(req.get_param_value("limit")) : 50;
        int offset = req.has_param("offset") ? std::stoi(req.get_param_value("offset")) : 0;
        int min_rating = req.has_param("min_rating") ? std::stoi(req.get_param_value("min_rating")) : 0;
        std::vector<std::string> tags;
        auto count = req.get_param_value_count("tag");
        for (size_t i = 0; i < count; ++i) tags.push_back(req.get_param_value("tag", i));
        std::string model = req.has_param("model") ? req.get_param_value("model") : "";
        res.set_content(m_db->get_generations(limit, offset, tags, model, min_rating).dump(), "application/json");
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
                {{"role", "system"}, {"content", "You are an expert art style analyzer. Analyze the given image prompt and extract distinct art styles, artists, or aesthetic descriptors. Return a JSON object with a 'styles' key containing an array of objects. Each style object must have 'name' (concise style name), 'prompt' (keywords to append, MUST include '{prompt}' placeholder), and 'negative_prompt' (optional tags to avoid). Example: {\"styles\": [{\"name\": \"Cyberpunk\", \"prompt\": \"{prompt}\", \"cyberpunk, neon lights\", \"negative_prompt\": \"organic\"}]}"}},
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
    svr.Post("/v1/chat/completions", proxy_llm);
    svr.Post("/v1/completions", proxy_llm);
    svr.Post("/v1/embeddings", proxy_llm);
    svr.Post("/v1/tokenize", proxy_llm);
    svr.Post("/v1/detokenize", proxy_llm);
    svr.Post("/v1/llm/unload", proxy_llm);

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

    svr.Post("/v1/presets/image/load", [this](const httplib::Request& req, httplib::Response& res) {
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
            mysti::json load_req;
            load_req["model_id"] = selected["unet_path"];
            if (!selected["vae_path"].get<std::string>().empty()) load_req["vae"] = selected["vae_path"];
            if (!selected["clip_l_path"].get<std::string>().empty()) load_req["clip_l"] = selected["clip_l_path"];
            if (!selected["clip_g_path"].get<std::string>().empty()) load_req["clip_g"] = selected["clip_g_path"];
            if (!selected["t5xxl_path"].get<std::string>().empty()) load_req["t5xxl"] = selected["t5xxl_path"];
            httplib::Request mod_req = req;
            mod_req.path = "/v1/models/load";
            mod_req.body = load_req.dump();
            Proxy::forward_request(mod_req, res, "127.0.0.1", m_sd_port, "", m_token);
            if (res.status == 200) {
                std::lock_guard<std::mutex> lock(m_state_mutex);
                m_last_sd_model_req_body = mod_req.body;
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
}

} // namespace mysti
