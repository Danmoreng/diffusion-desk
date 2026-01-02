#include "resource_manager.hpp"
#include "httplib.h"
#include <iostream>
#include <thread>

namespace mysti {

ResourceManager::ResourceManager(int sd_port, int llm_port, const std::string& internal_token)
    : m_sd_port(sd_port), m_llm_port(llm_port), m_token(internal_token) {}

ResourceManager::~ResourceManager() {}

ArbitrationResult ResourceManager::prepare_for_sd_generation(float estimated_total_needed_gb, float megapixels, const std::string& model_id, float base_gb_override) {
    std::lock_guard<std::mutex> lock(m_mutex);
    ArbitrationResult result;
    
    float free_vram = get_free_vram_gb();
    
    // Determine base requirement vs additional resolution overhead
    float base_gb = 4.5f; 
    if (base_gb_override > 0.1f) {
        base_gb = base_gb_override;
    } else if (!model_id.empty() && m_model_footprints.count(model_id)) {
        base_gb = m_model_footprints[model_id];
    }
    
    float resolution_overhead = estimated_total_needed_gb - base_gb;
    if (resolution_overhead < 0.5f) resolution_overhead = 0.5f; // Minimum safety

    // Hard limit: CUDA/GGML often fails with assertions if tensors exceed 2GB or certain dimensions.
    // 2.5 Megapixels is a safe upper bound for most consumer hardware and current GGML limits.
    if (megapixels > 2.5f) {
        LOG_ERROR("[ResourceManager] Resolution too high (%.2f MP). Limit is 2.5 MP to prevent CUDA crashes.", megapixels);
        result.success = false;
        return result;
    }

    // Safety margin: CUDA context and temporary buffers often take more than just 'allocations'
    resolution_overhead *= 1.15f; 

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
    // We want at least 0.5GB breathing room after allocation (reduced from 2.0GB)
    // Direct check of m_last_llm_vram_gb to avoid deadlock (is_llm_loaded locks mutex)
    bool llm_seems_loaded = m_last_llm_vram_gb > 0.1f;
    if (free_vram < actually_needed_additional + 0.5f && llm_seems_loaded) {
        LOG_INFO("[ResourceManager] Low VRAM detected (Free: %.2f < Needed: %.2f + 0.5). Requesting LLM unload...", free_vram, actually_needed_additional);
        httplib::Client cli("127.0.0.1", m_llm_port);
        cli.set_connection_timeout(2);
        cli.set_read_timeout(5);
        auto ures = cli.Post("/v1/llm/unload", headers, "", "application/json");
        if (ures && ures->status == 200) {
            LOG_INFO("[ResourceManager] LLM unloaded successfully.");
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            free_vram = get_free_vram_gb(); // Update free count
            LOG_INFO("[ResourceManager] Free VRAM after unload: %.2f GB", free_vram);
        } else {
            LOG_WARN("[ResourceManager] Failed to unload LLM.");
        }
    }

    // Phase 2: If STILL tight, recommend offloads
    if (free_vram < actually_needed_additional + 0.5f || megapixels > 2.0f) {
        LOG_WARN("[ResourceManager] VRAM tight (Free: %.2f GB, Add.Needed: %.2f GB) or High Res. Recommending CLIP offload.", 
                 free_vram, actually_needed_additional);
        result.request_clip_offload = true;
    }

    // Phase 3: If VERY tight, recommend VAE tiling
    if (free_vram < actually_needed_additional + 0.5f || megapixels > 1.5f) {
        LOG_WARN("[ResourceManager] VRAM very tight or high res. Recommending VAE tiling.");
        result.request_vae_tiling = true;
    }
    
    // Final Safety Check: If we simply don't have enough VRAM, prevent the crash.
    float checked_needed = actually_needed_additional;

    // Adjust expectation based on mitigations
    if (result.request_clip_offload) {
        checked_needed -= 0.6f; // Conservative savings for CLIP offload
    }

    float tiling_factor = 1.0f;
    if (result.request_vae_tiling) {
        tiling_factor = 0.6f; // Significant savings from tiling
    } else {
        tiling_factor = 0.85f; // Normal operation allows some squeeze vs strict allocation
    }
    
    // Apply tiling/squeeze factor
    checked_needed *= tiling_factor;

    // Ensure we don't go below a sanity floor
    if (checked_needed < 0.5f) checked_needed = 0.5f;

    if (free_vram < checked_needed) {
        LOG_ERROR("[ResourceManager] Insufficient VRAM! Free: %.2f GB, Needed (adjusted): %.2f GB (Raw: %.2f). Aborting to prevent crash.", 
                  free_vram, checked_needed, actually_needed_additional);
        result.success = false;
    }

    return result; 
}

bool ResourceManager::is_llm_loaded() {
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_last_llm_vram_gb > 0.1f) return true; // Quick check based on metrics
    }
    
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
