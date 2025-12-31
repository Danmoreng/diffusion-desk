#include "resource_manager.hpp"
#include "httplib.h"
#include <iostream>
#include <thread>

namespace mysti {

ResourceManager::ResourceManager(int sd_port, int llm_port, const std::string& internal_token)
    : m_sd_port(sd_port), m_llm_port(llm_port), m_token(internal_token) {}

ResourceManager::~ResourceManager() {}

ArbitrationResult ResourceManager::prepare_for_sd_generation(float estimated_total_needed_gb, float megapixels, const std::string& model_id) {
    std::lock_guard<std::mutex> lock(m_mutex);
    ArbitrationResult result;
    
    float free_vram = get_free_vram_gb();
    
    // Determine base requirement vs additional resolution overhead
    float base_gb = 4.5f; 
    if (!model_id.empty() && m_model_footprints.count(model_id)) {
        base_gb = m_model_footprints[model_id];
    }
    
    float resolution_overhead = estimated_total_needed_gb - base_gb;
    if (resolution_overhead < 0.5f) resolution_overhead = 0.5f; // Minimum safety

    // Safety margin: CUDA context and temporary buffers often take more than just 'allocations'
    resolution_overhead *= 1.2f; 

    // If the model is already loaded (sd_vram > base * 0.8), we only care about additional overhead.
    bool sd_has_model = (m_last_sd_vram_gb > base_gb * 0.7f);
    float actually_needed_additional = sd_has_model ? resolution_overhead : (base_gb + resolution_overhead);

    LOG_INFO("[ResourceManager] Arbitration | Free: %.2f GB, SD is using: %.2f GB (Base: %.2f), Add.Needed: %.2f GB", 
             free_vram, m_last_sd_vram_gb, base_gb, actually_needed_additional);

    httplib::Headers headers;
    if (!m_token.empty()) {
        headers.emplace("X-Internal-Token", m_token);
    }

    // Phase 1: If tight, try unloading LLM
    if (free_vram < actually_needed_additional * 1.15f && is_llm_loaded()) {
        LOG_INFO("[ResourceManager] Low VRAM detected. Requesting LLM unload to free space...");
        httplib::Client cli("127.0.0.1", m_llm_port);
        auto ures = cli.Post("/v1/llm/unload", headers, "", "application/json");
        if (ures && ures->status == 200) {
            LOG_INFO("[ResourceManager] LLM unloaded successfully.");
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            free_vram = get_free_vram_gb(); // Update free count
        } else {
            LOG_WARN("[ResourceManager] Failed to unload LLM.");
        }
    }

    // Phase 2: If STILL tight, recommend offloads
    if (free_vram < actually_needed_additional * 1.05f) {
        LOG_WARN("[ResourceManager] VRAM tight after arbitration (Free: %.2f GB, Add.Needed: %.2f GB). Recommending offloads.", 
                 free_vram, actually_needed_additional);
        result.request_clip_offload = true;
        if (megapixels > 1.5f) {
            result.request_vae_tiling = true;
        }
    }

    return result; 
}

bool ResourceManager::is_llm_loaded() {
    if (m_last_llm_vram_gb > 0.1f) return true; // Quick check based on metrics
    
    httplib::Headers headers;
    if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);

    httplib::Client cli("127.0.0.1", m_llm_port);
    if (auto hres = cli.Get("/internal/health", headers)) {
        if (hres->status == 200) {
            try {
                auto j = mysti::json::parse(hres->body);
                return j.value("model_loaded", false);
            } catch (...) {}
        }
    }
    return false;
}

mysti::json ResourceManager::get_vram_status() {
    std::lock_guard<std::mutex> lock(m_mutex);
    mysti::json status;
    status["total_gb"] = get_total_vram_gb();
    status["free_gb"] = get_free_vram_gb();
    status["process_gb"] = get_current_process_vram_usage_gb();
    status["sd_worker_gb"] = m_last_sd_vram_gb;
    status["llm_worker_gb"] = m_last_llm_vram_gb;
    return status;
}

void ResourceManager::update_worker_usage(float sd_gb, float llm_gb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_last_sd_vram_gb = sd_gb;
    m_last_llm_vram_gb = llm_gb;
}

void ResourceManager::update_model_footprint(const std::string& model_id, float vram_gb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (vram_gb > 0.05f) {
        m_model_footprints[model_id] = vram_gb;
    }
}

float ResourceManager::get_model_footprint(const std::string& model_id) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_model_footprints.count(model_id)) {
        return m_model_footprints[model_id];
    }
    return 0.0f;
}

} // namespace mysti
