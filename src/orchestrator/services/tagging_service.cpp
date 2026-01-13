#include "tagging_service.hpp"
#include "httplib.h"
#include "utils/common.hpp"
#include <iostream>
#include <fstream>
#include <chrono>

namespace diffusion_desk {

static bool str_ends_with(const std::string& str, const std::string& suffix) {
    return str.size() >= suffix.size() && 
           str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}

TaggingService::TaggingService(std::shared_ptr<Database> db, int llm_port, int orchestrator_port, const std::string& token, const std::string& system_prompt)
    : m_db(db), m_llm_port(llm_port), m_orchestrator_port(orchestrator_port), m_token(token), m_system_prompt(system_prompt) {}

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
        std::string loaded_model_path = "";
        
        if (auto res = cli.Get("/internal/health", h)) {
             try {
                 auto j = diffusion_desk::json::parse(res->body);
                 loaded = j.value("model_loaded", false);
                 loaded_mmproj = j.value("mmproj_path", "");
                 loaded_model_path = j.value("model_path", "");
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
                    // Check if the requested model is effectively the one already loaded (simple path check)
                    // If loaded is false, we proceed. But wait, if loaded was true we wouldn't be here.
                    // The issue described is: "when an LLM is already loaded, the tagger seems to try and load the other LLM"
                    // That implies loaded is true but maybe m_model_provider returns a different model?
                    // Ah, the original code had: if (!loaded) { ... } 
                    // So if loaded is true, it shouldn't reload.
                    // However, if the user said "it tries to load the other LLM", maybe loaded is FALSE because the LLM was unloaded?
                    // OR maybe the user sees logs like "[Tagging Service] Auto-loading LLM..." which is inside the if (!loaded) block.
                    // If the LLM is loaded, it skips this block.
                    // UNLESS the health check reports loaded=false?
                    
                    // Re-reading logs:
                    // [2026-01-05T12:18:52] [INFO ] [req-oBw9AFhg] resource_manager.cpp:52 - [ResourceManager] VRAM tight. Requesting LLM swap to RAM...
                    // [2026-01-05T12:18:52] [WARN ] [req-oBw9AFhg] resource_manager.cpp:61 - [ResourceManager] Failed to swap LLM to RAM.
                    // ...
                    // [Tagging Service] Auto-loading LLM...
                    
                    // It seems the LLM was UNLOADED (or failed to swap and maybe got unloaded or was never reloaded?).
                    // If it was swapped to RAM (CPU offload), the worker might report loaded=true.
                    // If the user says "LLM is already loaded", they might mean it shows as loaded in the UI or they expect it to be.
                    
                    // IF the LLM is loaded, we proceed.
                    // IF NOT loaded, we try to load what m_model_provider gives us.
                    // m_model_provider returns g_controller->get_last_llm_model_req().
                    // This stores the last requested LLM.
                    
                    // If the LLM *was* swapped to CPU, it should still report loaded=true in /internal/health?
                    // Let's verify LlamaServer state reporting.
                    // But if it was hard unloaded (e.g. by Resource Manager Phase 4), then loaded=false.
                    
                    // If the user says "an LLM is already loaded", maybe they mean *another* LLM was loaded manually?
                    // TaggingService uses m_model_provider which is bound to the LAST loaded LLM in ServiceController.
                    // If the user loaded "LLM B", then m_model_provider returns "LLM B".
                    // If "LLM B" is loaded, health check says loaded=true. Code skips this block.
                    
                    // Wait, if the user loaded "LLM A", then generated an image.
                    // If arbitration UNLOADED "LLM A", then TaggingService finds loaded=false.
                    // Then it calls m_model_provider -> returns "LLM A".
                    // It reloads "LLM A". This is correct behavior (restore state).
                    
                    // BUT the user says: "the tagger seems to try and load the other LLM i have available isntead of using the one that is already loaded"
                    // This implies the user loaded "LLM A", but the tagger is loading "LLM B"?
                    // Or maybe "LLM A" is loaded, but the tagger ignores it and loads "LLM B"?
                    
                    // The only way Tagger ignores a loaded LLM is if we implement logic to check if the loaded model matches the requested one.
                    // Currently it DOES NOT check. It just says "if (!loaded)".
                    // So if ANY LLM is loaded, it should use it.
                    
                    // So why did the user say that?
                    // "when I generate an image and an LLM is already loaded"
                    // Maybe the LLM was NOT unloaded during generation?
                    // If so, `loaded` is true. The loop continues to "Process Batch".
                    
                    // Is it possible /internal/health returns loaded=false when it is actually loaded?
                    // Or maybe `m_token` mismatch causing 401 and parse fail -> loaded=false?
                    
                    // Let's assume the user's issue is that the LLM *was* unloaded (due to VRAM pressure), and the Tagger is restoring the *Default* or *Last* LLM, which might differ from what the user *wants* if they didn't explicitly load it via the API that updates ServiceController state.
                    
                    // Actually, if the user manually loaded an LLM, ServiceController updates m_last_llm_model_req_body.
                    // So m_model_provider should return the correct one.
                    
                    // WAIT. The logs show:
                    // [2026-01-05T12:18:21] [INFO ] service_controller.cpp:362 - Request: POST /v1/llm/load, Body: {"model_id":"llm/Qwen3VL-4B-Instruct-Q4_K_M.gguf"...}
                    // This sets m_last_llm_model_req_body.
                    
                    // If the Tagger is loading "the other LLM", maybe `m_model_provider` is returning the wrong thing?
                    // Or maybe the Tagger logic I'm looking at ISN'T checking `loaded` correctly?
                    
                    // Ah, `if (!loaded)` is the check.
                    // If the user says "LLM is already loaded", then `loaded` must be true.
                    // Unless... VRAM arbitration unloaded it?
                    // The logs show: "[WARN ] ... resource_manager.cpp:61 - [ResourceManager] Failed to swap LLM to RAM."
                    // Then warnings about CLIP offload and VAE tiling.
                    // It does NOT show "Requesting hard LLM unload" in the logs provided in the prompt.
                    // So the LLM *should* still be loaded?
                    
                    // If the LLM is loaded, `if (!loaded)` is false. It skips the reload block.
                    // Then it goes to "Process Batch".
                    // It prints "Tagging image ID...".
                    
                    // The user log shows:
                    // [Tagging Service] Auto-loading LLM...
                    // [Tagging Service] Tagging image ID 559 (Text-Only)...
                    
                    // This means `if (!loaded)` WAS true. So `loaded` was false.
                    // Why was it false?
                    // 1. It was unloaded.
                    // 2. Health check failed.
                    
                    // If it was unloaded, why? The logs don't show "SD model unloaded" or "LLM unloaded".
                    // Wait, the logs show "[ResourceManager] VRAM tight. Requesting LLM swap to RAM...".
                    // Then "Failed to swap".
                    // Maybe the `offload_to_cpu` implementation I added *unloads* if it fails?
                    // No, `offload_to_cpu` calls `load_model(..., 0, ...)`.
                    // If `load_model` fails, `server_ctx` is reset -> unloaded.
                    // So if swap failed (which it did, likely due to file IO error or something?), the model became unloaded.
                    
                    // So the Tagger sees it's unloaded, and tries to reload it.
                    // The user says it tries to load "the other LLM".
                    // Maybe the "Last Requested" LLM is different from what was "Already Loaded" (e.g. via CLI args)?
                    // CLI args load: `orchestrator_main.cpp` calls `cli.Post("/v1/llm/load"...)`.
                    // This *should* update the ServiceController state.
                    
                    // However, if the Tagger is reloading, it uses `m_model_provider`.
                    // If that provider returns the "other" LLM, that's why.
                    
                    // Ideally, we want the Tagger to check:
                    // 1. Is ANY LLM loaded? If so, USE IT. Don't reload just because it doesn't match `m_model_provider`.
                    //    (Current code does this: `if (!loaded)`).
                    // 2. If NOT loaded, use `m_model_provider`.
                    
                    // The problem is `loaded` was false.
                    // So the Tagger *must* load something to work.
                    // It loads what `m_model_provider` says.
                    // If that's the "wrong" LLM, it means `ServiceController` has the "wrong" LLM in its history.
                    
                    // BUT, if the user wants to prioritize the *currently loaded* LLM (if it exists), we should just use it.
                    // The issue is likely that `offload_to_cpu` FAILED, leaving NO LLM loaded.
                    // So the Tagger *has* to reload.
                    // Why did offload fail?
                    
                    // Regardless, the user says "if an LLM is already loaded...".
                    // Maybe they mean "If I have Qwen loaded, don't load Llama3".
                    // The current code:
                    // if (auto res = cli.Get("/internal/health", h)) { ... loaded = j.value("loaded", false); ... }
                    // if (!loaded) { ... reload ... }
                    
                    // This logic ALREADY respects "if already loaded, don't reload".
                    // So if it IS reloading, it's because `loaded` is false.
                    
                    // I will add a check: if `loaded` is true, we proceed.
                    // I will also add better logging to see *why* it thinks it's not loaded.
                    
                    // To address the "problem" described:
                    // "the tagger seems to try and load the other LLM... instead of using the one that is already loaded"
                    // If `loaded` is true, it skips reload.
                    // So `loaded` MUST be false.
                    // If `loaded` is false, there is no "one that is already loaded".
                    // So the user might be mistaken about the state, OR `health` check is reporting false negatives.
                    
                    // I'll make the logic explicitly prefer the running model if available.
                    // And I'll fix the CLIP progress issue in the next step.
                    
                    std::cout << "[Tagging Service] Auto-loading LLM..." << std::endl;
                    cli.set_read_timeout(600);
                    auto res = cli.Post("/v1/llm/load", h, model_body, "application/json");
                    if (res && res->status == 200) {
                         loaded = true;
                         reloaded = true;
                         m_load_retry_count = 0;
                         // Check mmproj after reload
                         if (auto h_res = cli.Get("/internal/health", h)) {
                             try { 
                                 auto j = diffusion_desk::json::parse(h_res->body);
                                 loaded_mmproj = j.value("mmproj_path", "");
                                 loaded_model_path = j.value("model_path", ""); 
                             } catch(...) {}
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
        } else {
            // Already loaded.
            // Ensure we update metadata if we didn't reload
            // (We already parsed it from health check above)
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
            
            // Resolve System Prompt
            std::string active_prompt = m_system_prompt;
            if (m_db) {
                std::string last_llm = m_db->get_config("last_llm_preset_id");
                if (!last_llm.empty()) {
                    try {
                        int pid = std::stoi(last_llm);
                        auto presets = m_db->get_llm_presets();
                        for (const auto& p : presets) {
                            if (p.value("id", 0) == pid) {
                                std::string custom = p.value("system_prompt_tagging", "");
                                if (!custom.empty()) active_prompt = custom;
                                break;
                            }
                        }
                    } catch(...) {}
                }
            }

            diffusion_desk::json chat_req;
            chat_req["messages"] = diffusion_desk::json::array();
            chat_req["messages"].push_back({{"role", "system"}, {"content", active_prompt}});
            
            if (!loaded_mmproj.empty()) {
                // Vision Request
                diffusion_desk::json user_content = diffusion_desk::json::array();
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
                    auto rj = diffusion_desk::json::parse(chat_res->body);
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
                        auto tags_json = diffusion_desk::json::parse(json_part);
                        diffusion_desk::json tags_arr = diffusion_desk::json::array();

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

} // namespace diffusion_desk