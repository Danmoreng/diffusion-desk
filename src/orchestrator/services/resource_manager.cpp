#include "resource_manager.hpp"
#include "httplib.h"
#include <iostream>
#include <thread>

namespace mysti {

ResourceManager::ResourceManager(int sd_port, int llm_port, const std::string& internal_token)
    : m_sd_port(sd_port), m_llm_port(llm_port), m_token(internal_token) {}

ResourceManager::~ResourceManager() {}

bool ResourceManager::prepare_for_sd_generation(float estimated_vram_gb) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    float free_vram = get_free_vram_gb();
    LOG_INFO("[ResourceManager] VRAM Arbitration: Current free VRAM = %.2f GB, Needed ~%.2f GB", free_vram, estimated_vram_gb);

    httplib::Headers headers;
    if (!m_token.empty()) {
        headers.emplace("X-Internal-Token", m_token);
    }

    if (free_vram < estimated_vram_gb && is_llm_loaded()) {
        LOG_INFO("[ResourceManager] Low VRAM detected. Requesting LLM unload to free space...");
        httplib::Client cli("127.0.0.1", m_llm_port);
        auto ures = cli.Post("/v1/llm/unload", headers, "", "application/json");
        if (ures && ures->status == 200) {
            LOG_INFO("[ResourceManager] LLM unloaded successfully.");
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
            return true;
        } else {
            LOG_WARN("[ResourceManager] Failed to unload LLM.");
        }
    }

    return true; // Proceed anyway, SD might manage or swap
}

bool ResourceManager::is_llm_loaded() {
    httplib::Headers headers;
    if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);

    httplib::Client cli("127.0.0.1", m_llm_port);
    if (auto hres = cli.Get("/internal/health", headers)) {
        if (hres->status == 200) {
            try {
                auto j = mysti::json::parse(hres->body);
                return j.value("loaded", false);
            } catch (...) {}
        }
    }
    return false;
}

mysti::json ResourceManager::get_vram_status() {
    mysti::json status;
    status["total_gb"] = get_total_vram_gb();
    status["free_gb"] = get_free_vram_gb();
    status["process_gb"] = get_current_process_vram_usage_gb();
    return status;
}

} // namespace mysti
