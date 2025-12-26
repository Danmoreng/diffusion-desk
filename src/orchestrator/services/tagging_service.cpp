#include "tagging_service.hpp"
#include "httplib.h"
#include "utils/common.hpp"
#include <iostream>
#include <chrono>

namespace mysti {

TaggingService::TaggingService(std::shared_ptr<Database> db, int llm_port, const std::string& token)
    : m_db(db), m_llm_port(llm_port), m_token(token) {}

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

std::string TaggingService::extract_json(const std::string& content) {
    size_t obj_start = content.find("{");
    size_t obj_end = content.rfind("}");
    size_t arr_start = content.find("[");
    size_t arr_end = content.rfind("]");
    
    if (obj_start != std::string::npos && (arr_start == std::string::npos || obj_start < arr_start)) {
        return content.substr(obj_start, obj_end - obj_start + 1);
    } else if (arr_start != std::string::npos) {
        return content.substr(arr_start, arr_end - arr_start + 1);
    }
    return "";
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
        if (auto res = cli.Get("/internal/health", h)) {
             try {
                 auto j = mysti::json::parse(res->body);
                 loaded = j.value("loaded", false);
             } catch(...) {}
        }

        if (!loaded) {
            bool reloaded = false;
            if (m_model_provider) {
                std::string model_body = m_model_provider();
                if (!model_body.empty()) {
                    std::cout << "[Tagging Service] Auto-loading LLM..." << std::endl;
                    cli.set_read_timeout(600);
                    auto res = cli.Post("/v1/llm/load", h, model_body, "application/json");
                    if (res && res->status == 200) {
                         loaded = true;
                         reloaded = true;
                    } else {
                         std::cout << "[Tagging Service] Failed to load LLM." << std::endl;
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
            
            mysti::json chat_req;
            chat_req["messages"] = mysti::json::array({
                {{"role", "system"}, {"content", "You are a specialized image tagging engine. Output a JSON object with a 'tags' key containing an array of 5-8 descriptive tags (Subject, Style, Mood). Example: {\"tags\": [\"cat\", \"forest\", \"ethereal\"]}. Output ONLY valid JSON."}},
                {{"role", "user"}, {"content", prompt}}
            });
            chat_req["temperature"] = 0.1;
            chat_req["response_format"] = {{"type", "json_object"}};
            
            std::cout << "[Tagging Service] Tagging image ID " << id << "..." << std::endl;
            cli.set_read_timeout(120);
            
            auto chat_res = cli.Post("/v1/chat/completions", h, chat_req.dump(), "application/json");
            
            if (chat_res && chat_res->status == 200) {
                try {
                    auto rj = mysti::json::parse(chat_res->body);
                    if (!rj.contains("choices") || rj["choices"].empty()) {
                        m_db->mark_as_tagged(id); // Skip invalid
                        continue;
                    }
                    
                    auto& msg_obj = rj["choices"][0]["message"];
                    std::string content = "";
                    if (msg_obj.contains("content") && !msg_obj["content"].is_null()) {
                        content = msg_obj["content"].get<std::string>();
                    }
                    
                    std::string json_part = extract_json(content);

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
                                m_db->add_tag_by_id(id, tag, "llm_auto");
                                count++;
                            }
                            std::cout << "[Tagging Service] ID " << id << ": Saved " << count << " tags." << std::endl;
                        }
                    }
                    
                    m_db->mark_as_tagged(id);
                    
                } catch (const std::exception& e) {
                    std::cerr << "[Tagging Service] Error processing ID " << id << ": " << e.what() << std::endl;
                    m_db->mark_as_tagged(id); // Mark as tagged to avoid infinite retry loop on bad data
                }
            } else {
                std::cout << "[Tagging Service] ID " << id << ": LLM Request failed." << std::endl;
            }
        }
    }
}

} // namespace mysti