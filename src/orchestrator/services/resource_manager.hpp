#pragma once

#include "utils/common.hpp"
#include <string>
#include <mutex>
#include <atomic>

namespace mysti {

struct ArbitrationResult {
    bool success = true;
    bool request_clip_offload = false;
    bool request_vae_tiling = false;
};

class ResourceManager {
public:
    ResourceManager(int sd_port, int llm_port, const std::string& internal_token);
    ~ResourceManager();

    // VRAM management
    ArbitrationResult prepare_for_sd_generation(float estimated_total_needed_gb, float megapixels, const std::string& model_id = "");
    bool is_llm_loaded();

    // Statistics
    mysti::json get_vram_status();

    // Track actual measured usage
    void update_worker_usage(float sd_gb, float llm_gb);
    void update_model_footprint(const std::string& model_id, float vram_gb);
    float get_model_footprint(const std::string& model_id);

private:
    int m_sd_port;
    int m_llm_port;
    std::string m_token;
    std::mutex m_mutex;
    float m_last_sd_vram_gb = 0.0f;
    float m_last_llm_vram_gb = 0.0f;
    std::map<std::string, float> m_model_footprints;
};

} // namespace mysti
