#pragma once

#include "orchestrator/database.hpp"
#include <atomic>
#include <string>
#include <memory>
#include <thread>
#include <condition_variable>
#include <mutex>
#include <functional>

namespace diffusion_desk {

class TaggingService {
public:
    TaggingService(std::shared_ptr<Database> db, int llm_port, int orchestrator_port, const std::string& token, const std::string& system_prompt);
    ~TaggingService();

    void start();
    void stop();

    // To pause tagging while generating images (VRAM priority)
    void set_generation_active(bool active);
    
    // Trigger immediate tagging check
    void notify_new_generation();

    // Set callback to get the last loaded LLM model configuration
    void set_model_provider(std::function<std::string()> provider);

private:
    void loop();

    std::shared_ptr<Database> m_db;
    int m_llm_port;
    int m_orchestrator_port;
    std::string m_token;
    std::string m_system_prompt;
    
    std::atomic<bool> m_running{false};
    std::atomic<bool> m_generation_active{false};
    std::thread m_thread;
    
    std::condition_variable m_cv;
    std::mutex m_cv_mutex;
    
    std::function<std::string()> m_model_provider;

    std::chrono::steady_clock::time_point m_last_load_fail_time = std::chrono::steady_clock::now() - std::chrono::hours(1);
    int m_load_retry_count = 0;
};

} // namespace diffusion_desk
