#pragma once

#include "utils/common.hpp"
#include <string>
#include <mutex>
#include <atomic>

namespace mysti {

class ResourceManager {
public:
    ResourceManager(int sd_port, int llm_port, const std::string& internal_token);
    ~ResourceManager();

    // Prepare for high-VRAM generation task by potentially unloading LLM
    bool prepare_for_sd_generation(float estimated_vram_gb = 4.0f);
    
    // Check if workers are loaded/active
    bool is_llm_loaded();
    
    // Statistics
    mysti::json get_vram_status();

    // Track actual measured usage
    void update_model_footprint(const std::string& model_id, float vram_gb);
    float get_model_footprint(const std::string& model_id);

private:
    int m_sd_port;
    int m_llm_port;
    std::string m_token;
    std::mutex m_mutex;
    std::map<std::string, float> m_model_footprints;
};

} // namespace mysti
