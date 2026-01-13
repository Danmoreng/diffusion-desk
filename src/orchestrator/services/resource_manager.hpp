#pragma once

#include "utils/common.hpp"
#include <string>
#include <mutex>
#include <atomic>

namespace diffusion_desk {

struct ArbitrationResult {
    bool success = true;
    bool request_clip_offload = false;
    bool request_vae_tiling = false;
    float committed_gb = 0.0f;
};

class ResourceManager {
public:
    ResourceManager(int sd_port, int llm_port, const std::string& internal_token);
    ~ResourceManager();

    // VRAM management
    ArbitrationResult prepare_for_sd_generation(float estimated_total_needed_gb, float megapixels, const std::string& model_id = "", float base_gb_override = 0.0f, float clip_size_gb = 0.0f);

    // Returns success/fail for LLM loading (may unload SD model)
    bool prepare_for_llm_load(float estimated_needed_gb);

    bool is_llm_loaded();

    // Statistics
    diffusion_desk::json get_vram_status();

    // Track actual measured usage
    void update_worker_usage(float sd_gb, float llm_gb);
    void update_model_footprint(const std::string& model_id, float vram_gb);
    float get_model_footprint(const std::string& model_id);

    // Committed VRAM management
    void commit_vram(float gb);
    void uncommit_vram(float gb);

private:
    int m_sd_port;
    int m_llm_port;
    std::string m_token;
    std::mutex m_mutex;
    float m_last_sd_vram_gb = 0.0f;
    float m_last_llm_vram_gb = 0.0f;
    std::atomic<float> m_committed_vram_gb{0.0f};
    std::map<std::string, float> m_model_footprints;
};

} // namespace diffusion_desk
