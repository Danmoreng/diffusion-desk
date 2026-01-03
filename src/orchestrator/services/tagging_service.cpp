#include "tagging_service.hpp"
#include "httplib.h"
#include "utils/common.hpp"
#include <iostream>
#include <fstream>
#include <chrono>

namespace mysti {

static bool str_ends_with(const std::string& str, const std::string& suffix) {
    return str.size() >= suffix.size() && 
           str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}

TaggingService::TaggingService(std::shared_ptr<Database> db, int llm_port, const std::string& token, const std::string& system_prompt)
    : m_db(db), m_llm_port(llm_port), m_token(token), m_system_prompt(system_prompt) {}

TaggingService::~TaggingService() {
    stop();
}

void TaggingService::start() {
    if (m_running) return;
    m_running = true;
    m_thread = std::thread(&TaggingService::loop, this);
    std::cout << "[Tagging Service] Thread started." << std::endl;
}

void TaggingService::stop() {
    if (!m_running) return;
    m_running = false;
    notify_new_generation(); // Wake up thread to exit
    if (m_thread.joinable()) {
        m_thread.join();
    }
    std::cout << "[Tagging Service] Thread stopped." << std::endl;
}

void TaggingService::set_generation_active(bool active) {
    m_generation_active = active;
}

void TaggingService::set_model_provider(std::function<std::string()> provider) {
    m_model_provider = provider;
}

void TaggingService::notify_new_generation() {
    std::unique_lock<std::mutex> lock(m_cv_mutex);
    m_cv.notify_one();
}

void TaggingService::loop() {
    while (m_running) {
        // Wait for notification or timeout (10 seconds)
        {
            std::unique_lock<std::mutex> lock(m_cv_mutex);
            m_cv.wait_for(lock, std::chrono::seconds(10), [this] { return !m_running; });
        }

        if (!m_running) break;
        
        if (m_generation_active) {
            // std::cout << "[Tagging Service] SD is generating, skipping..." << std::endl;
            continue; 
        }
        if (!m_db) continue;

        auto pending = m_db->get_untagged_generations(5); 
        if (pending.empty()) continue;

        std::cout << "[Tagging Service] Found " << pending.size() << " images to tag." << std::endl;

        // Check LLM Health/Load
        httplib::Client cli("127.0.0.1", m_llm_port);
        httplib::Headers h;
        if (!m_token.empty()) h.emplace("X-Internal-Token", m_token);
        
        bool loaded = false;
        std::string loaded_mmproj = "";
        if (auto res = cli.Get("/internal/health", h)) {
             try {
                 auto j = mysti::json::parse(res->body);
                 loaded = j.value("loaded", false);
                 loaded_mmproj = j.value("mmproj_path", "");
             } catch(...) {}
        }

        if (!loaded) {
            bool reloaded = false;
            if (m_model_provider) {
                // Cooldown check: if we failed recently, don't keep hammering the VRAM
                auto now = std::chrono::steady_clock::now();
                auto elapsed_since_fail = std::chrono::duration_cast<std::chrono::seconds>(now - m_last_load_fail_time).count();
                
                if (elapsed_since_fail < 60) {
                    // std::cout << "[Tagging Service] LLM load recently failed, in cooldown (" << 60 - elapsed_since_fail << "s left)" << std::endl;
                    std::this_thread::sleep_for(std::chrono::seconds(5));
                    continue;
                }

                std::string model_body = m_model_provider();
                if (!model_body.empty()) {
                    std::cout << "[Tagging Service] Auto-loading LLM..." << std::endl;
                    cli.set_read_timeout(600);
                    auto res = cli.Post("/v1/llm/load", h, model_body, "application/json");
                    if (res && res->status == 200) {
                         loaded = true;
                         reloaded = true;
                         m_load_retry_count = 0;
                         // Check mmproj after reload
                         if (auto h_res = cli.Get("/internal/health", h)) {
                             try { loaded_mmproj = mysti::json::parse(h_res->body).value("mmproj_path", ""); } catch(...) {}
                         }
                    } else {
                         std::cout << "[Tagging Service] Failed to load LLM." << std::endl;
                         m_last_load_fail_time = std::chrono::steady_clock::now();
                         m_load_retry_count++;
                    }
                } else {
                    std::cout << "[Tagging Service] No LLM model configured for auto-load." << std::endl;
                }
            }
            
            if (!reloaded) {
                 // std::cout << "[Tagging Service] LLM not loaded, skipping." << std::endl;
                 std::this_thread::sleep_for(std::chrono::seconds(5));
                 continue; 
            }
        }

        // Process Batch
        for (const auto& item : pending) {
            if (m_generation_active || !m_running) break;

            int id = std::get<0>(item);
            std::string prompt = std::get<2>(item);
            std::string file_path = std::get<3>(item);

            // Read Image
            std::ifstream img_file(file_path, std::ios::binary | std::ios::ate);
            if (!img_file.is_open()) {
                // Try prepending "." if path starts with / and it's a relative path in context of project root
                if (file_path.size() > 0 && file_path[0] == '/') {
                    std::string rel_path = "." + file_path;
                    img_file.open(rel_path, std::ios::binary | std::ios::ate);
                    if (img_file.is_open()) {
                        file_path = rel_path;
                    }
                }
            }

            if (!img_file.is_open()) {
                std::cerr << "[Tagging Service] Could not open image: " << file_path << std::endl;
                m_db->mark_as_tagged(id);
                continue;
            }
            std::streamsize size = img_file.tellg();
            img_file.seekg(0, std::ios::beg);
            std::vector<unsigned char> buffer(size);
            if (!img_file.read((char*)buffer.data(), size)) {
                 std::cerr << "[Tagging Service] Failed to read image: " << file_path << std::endl;
                 m_db->mark_as_tagged(id);
                 continue;
            }
            
            // Base64 Encode
            std::string b64 = base64_encode(buffer.data(), (unsigned int)buffer.size());
            // Determine Mime Type (simple check)
            std::string mime_type = "image/png";
            if (str_ends_with(file_path, ".jpg") || str_ends_with(file_path, ".jpeg")) mime_type = "image/jpeg";
            else if (str_ends_with(file_path, ".webp")) mime_type = "image/webp";

            std::string data_uri = "data:" + mime_type + ";base64," + b64;
            
            mysti::json chat_req;
            chat_req["messages"] = mysti::json::array();
            chat_req["messages"].push_back({{"role", "system"}, {"content", m_system_prompt}});
            
            if (!loaded_mmproj.empty()) {
                // Vision Request
                mysti::json user_content = mysti::json::array();
                user_content.push_back({{"type", "text"}, {"text", "Analyze this image and provide descriptive tags (Subject, Style, Mood). Return JSON."}});
                user_content.push_back({{"type", "image_url"}, {"image_url", {{"url", data_uri}}}});
                chat_req["messages"].push_back({{"role", "user"}, {"content", user_content}});
                std::cout << "[Tagging Service] Tagging image ID " << id << " (Vision)..." << std::endl;
            } else {
                // Text-only Fallback
                chat_req["messages"].push_back({{"role", "user"}, {"content", prompt}});
                std::cout << "[Tagging Service] Tagging image ID " << id << " (Text-Only)..." << std::endl;
            }

            chat_req["temperature"] = 0.1;
            chat_req["response_format"] = {{"type", "json_object"}};
            
            cli.set_read_timeout(120);
            
            auto chat_res = cli.Post("/v1/chat/completions", h, chat_req.dump(), "application/json");
            
            if (chat_res && chat_res->status == 200) {
                try {
                    auto rj = mysti::json::parse(chat_res->body);
                    if (!rj.contains("choices") || rj["choices"].empty()) {
                        m_db->mark_as_tagged(id); 
                        continue;
                    }
                    
                    auto& msg_obj = rj["choices"][0]["message"];
                    std::string content = "";
                    if (msg_obj.contains("content") && !msg_obj["content"].is_null()) {
                        content = msg_obj["content"].get<std::string>();
                    }
                    
                    std::string json_part = extract_json_block(content);

                    if (!json_part.empty()) {
                        auto tags_json = mysti::json::parse(json_part);
                        mysti::json tags_arr = mysti::json::array();

                        if (tags_json.is_array()) {
                            tags_arr = tags_json;
                        } else if (tags_json.is_object()) {
                            if (tags_json.contains("tags") && tags_json["tags"].is_array()) {
                                tags_arr = tags_json["tags"];
                            } else {
                                // Take first array found in object
                                for (auto& el : tags_json.items()) {
                                    if (el.value().is_array()) {
                                        tags_arr = el.value();
                                        break;
                                    }
                                }
                            }
                        }

                        if (tags_arr.is_array() && !tags_arr.empty()) {
                            int count = 0;
                            for (const auto& tag_val : tags_arr) {
                                if (!tag_val.is_string()) continue;
                                std::string tag = tag_val.get<std::string>();
                                if (tag.length() < 2) continue;
                                m_db->add_tag_by_id(id, tag, "llm_vision");
                                count++;
                            }
                            std::cout << "[Tagging Service] ID " << id << ": Saved " << count << " tags." << std::endl;
                        }
                    }
                    
                    m_db->mark_as_tagged(id);
                    
                } catch (const std::exception& e) {
                    std::cerr << "[Tagging Service] Error processing ID " << id << ": " << e.what() << std::endl;
                    m_db->mark_as_tagged(id); 
                }
            } else {
                std::cout << "[Tagging Service] ID " << id << ": LLM Request failed (" << (chat_res ? chat_res->status : 0) << ")." << std::endl;
            }
        }
    }
}

} // namespace mysti