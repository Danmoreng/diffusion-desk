#pragma once

#include "orchestrator/database.hpp"
#include <atomic>
#include <string>
#include <memory>
#include <thread>
#include <condition_variable>
#include <mutex>
#include <functional>

namespace mysti {

class TaggingService {
public:
    TaggingService(std::shared_ptr<Database> db, int llm_port, const std::string& token);
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
    std::string m_token;
    
    std::atomic<bool> m_running{false};
    std::atomic<bool> m_generation_active{false};
    std::thread m_thread;
    
    std::condition_variable m_cv;
    std::mutex m_cv_mutex;
    
    std::function<std::string()> m_model_provider;

    // Helper to extract JSON from LLM response
    std::string extract_json(const std::string& content);
};

} // namespace mysti
