#include "resource_manager.hpp"
#include "utils/common.hpp"
#include <iostream>
#include <thread>
#include <chrono>
#include "httplib.h"

namespace mysti {

ResourceManager::ResourceManager(int sd_port, int llm_port, const std::string& internal_token)
    : m_sd_port(sd_port), m_llm_port(llm_port), m_token(internal_token) {}

ResourceManager::~ResourceManager() {}

ArbitrationResult ResourceManager::prepare_for_sd_generation(float estimated_total_needed_gb, float megapixels, const std::string& model_id, float base_gb_override, float clip_size_gb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    ArbitrationResult result;
    
    // Effective Free VRAM accounts for memory we've promised to other starting tasks
    float free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
    if (free_vram < 0.0f) free_vram = 0.0f;
    
    // Determine base requirement vs additional resolution overhead
    float base_gb = 2.5f; 
    if (base_gb_override > 0.1f) {
        base_gb = base_gb_override;
    } else if (!model_id.empty() && m_model_footprints.count(model_id)) {
        base_gb = m_model_footprints[model_id];
    }
    
    float resolution_overhead = estimated_total_needed_gb - base_gb;
    if (resolution_overhead < 0.5f) resolution_overhead = 0.5f; // Minimum safety

    // Safety margin: CUDA context and temporary buffers often take more than just 'allocations'
    resolution_overhead *= 1.15f; 

    // If the model is already loaded (sd_vram > base * 0.8), we only care about additional overhead.
    bool sd_has_model = (m_last_sd_vram_gb > base_gb * 0.7f);
    float actually_needed_additional = sd_has_model ? resolution_overhead : (base_gb + resolution_overhead);

    LOG_INFO("[ResourceManager] Arbitration | Effective Free: %.2f GB (Committed: %.2f), SD is using: %.2f GB (Base: %.2f), Add.Needed: %.2f GB", 
             free_vram, m_committed_vram_gb.load(), m_last_sd_vram_gb, base_gb, actually_needed_additional);

    httplib::Headers headers;
    if (!m_token.empty()) {
        headers.emplace("X-Internal-Token", m_token);
    }

    // New Escalation Logic
    bool llm_seems_loaded = m_last_llm_vram_gb > 0.1f;
    
    // Phase 1: If tight, try Swapping LLM to RAM (CPU Offload)
    if (free_vram < actually_needed_additional + 0.5f && llm_seems_loaded) {
        LOG_INFO("[ResourceManager] VRAM tight. Requesting LLM swap to RAM...");
        httplib::Client cli("127.0.0.1", m_llm_port);
        cli.set_connection_timeout(2);
        cli.set_read_timeout(20);
        auto ures = cli.Post("/v1/llm/offload", headers, "", "application/json");
        if (ures && ures->status == 200) {
            LOG_INFO("[ResourceManager] LLM swapped to RAM successfully.");
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            // Update local tracking
            free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
            if (free_vram < 0.0f) free_vram = 0.0f;
        } else {
            LOG_WARN("[ResourceManager] Failed to swap LLM to RAM.");
        }
    }

    // Phase 2: Hard Unload LLM (Prioritized over CLIP Offload)
    // If still tight after attempted swap, or if swap failed
    if (free_vram < actually_needed_additional + 0.5f && llm_seems_loaded) {
        LOG_WARN("[ResourceManager] VRAM still tight. Requesting hard LLM unload to avoid CPU CLIP...");
        httplib::Client cli("127.0.0.1", m_llm_port);
        auto ures = cli.Post("/v1/llm/unload", headers, "", "application/json");
        if (ures && ures->status == 200) {
            std::this_thread::sleep_for(std::chrono::milliseconds(800));
            free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
            if (free_vram < 0.0f) free_vram = 0.0f;
        }
    }

    // Phase 3: CLIP Offloading (if still tight or high res)
    if (free_vram < actually_needed_additional + 0.5f || megapixels > 2.0f) {
        LOG_WARN("[ResourceManager] VRAM tight or High Res. Recommending CLIP offload.");
        result.request_clip_offload = true;
    }

    // Phase 4: VAE Tiling (if VERY tight or very high res)
    if (free_vram < actually_needed_additional + 0.5f || megapixels > 2.5f) {
        LOG_WARN("[ResourceManager] VRAM very tight or high res. Recommending VAE tiling.");
        result.request_vae_tiling = true;
    }
    
    // Final Safety Check
    float checked_needed = actually_needed_additional;
    if (result.request_clip_offload) {
        float saved_gb = (clip_size_gb > 0.1f) ? clip_size_gb : 1.5f;
        checked_needed -= saved_gb;
    }
    float tiling_factor = result.request_vae_tiling ? 0.4f : 0.85f;
    checked_needed *= tiling_factor;
    if (checked_needed < 0.5f) checked_needed = 0.5f;

    if (free_vram < checked_needed) {
        LOG_ERROR("[ResourceManager] Insufficient VRAM! Free: %.2f GB, Needed: %.2f GB. Aborting.", 
                  free_vram, checked_needed);
        result.success = false;
    } else {
        // Atomic commitment
        result.committed_gb = actually_needed_additional;
        commit_vram(result.committed_gb);
    }

    return result; 
}

bool ResourceManager::prepare_for_llm_load(float estimated_needed_gb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    httplib::Headers headers;
    if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);

    // Phase 1: Single LLM policy - always unload current LLM
    if (m_last_llm_vram_gb > 0.1f) {
        LOG_INFO("[ResourceManager] Unloading current LLM for new load.");
        httplib::Client cli("127.0.0.1", m_llm_port);
        cli.Post("/v1/llm/unload", headers, "", "application/json");
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
    }

    float free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
    float safety_needed = estimated_needed_gb * 1.1f; 
    
    bool can_fit = false;
    if (free_vram >= safety_needed + 0.3f) {
        can_fit = true;
    } else {
        // Phase 2: Try Offloading SD to CPU if tight
        if (m_last_sd_vram_gb > 0.5f) {
            LOG_WARN("[ResourceManager] VRAM tight for LLM. Requesting SD model offload to CPU...");
            httplib::Client cli("127.0.0.1", m_sd_port);
            auto ures = cli.Post("/v1/models/offload", headers, "", "application/json");
            if (ures && ures->status == 200) {
                std::this_thread::sleep_for(std::chrono::milliseconds(800));
                free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
                if (free_vram >= safety_needed) can_fit = true;
            }
        }

        // Phase 3: Hard Unload SD (Last resort)
        if (!can_fit && m_last_sd_vram_gb > 0.5f) {
            LOG_WARN("[ResourceManager] VRAM still tight. Requesting hard SD unload...");
            httplib::Client cli("127.0.0.1", m_sd_port);
            cli.Post("/v1/models/unload", headers, "", "application/json");
            std::this_thread::sleep_for(std::chrono::milliseconds(1000));
            free_vram = get_free_vram_gb() - m_committed_vram_gb.load();
            if (free_vram >= safety_needed) can_fit = true;
        }
    }

    if (can_fit) {
        commit_vram(safety_needed);
        return true;
    }

    LOG_ERROR("[ResourceManager] Insufficient VRAM for LLM. Need %.2f GB, have %.2f GB.", safety_needed, free_vram);
    return false;
}

bool ResourceManager::is_llm_loaded() {
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_last_llm_vram_gb > 0.1f) return true; 
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
    status["committed_gb"] = m_committed_vram_gb.load();
    status["effective_free_gb"] = (float)status["free_gb"] - (float)status["committed_gb"];
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

void ResourceManager::commit_vram(float gb) {
    float current = m_committed_vram_gb.load();
    while (!m_committed_vram_gb.compare_exchange_weak(current, current + gb));
}

void ResourceManager::uncommit_vram(float gb) {
    float current = m_committed_vram_gb.load();
    while (current >= gb && !m_committed_vram_gb.compare_exchange_weak(current, current - gb));
    if (m_committed_vram_gb.load() < 0.0f) m_committed_vram_gb.store(0.0f);
}

} // namespace mysti
