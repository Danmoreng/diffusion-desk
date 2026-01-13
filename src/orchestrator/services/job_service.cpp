#include "job_service.hpp"
#include "../utils/common.hpp" // For logging macros if available, or use iostream
#include <iostream>
#include <chrono>

namespace diffusion_desk {

JobService::JobService(std::shared_ptr<Database> db) : m_db(db) {}

JobService::~JobService() {
    stop();
}

void JobService::start() {
    if (m_running) return;
    m_running = true;
    m_thread = std::thread(&JobService::loop, this);
    std::cout << "[JobService] Started background worker thread." << std::endl;
}

void JobService::stop() {
    if (!m_running) return;
    m_running = false;
    if (m_thread.joinable()) {
        m_thread.join();
    }
    std::cout << "[JobService] Stopped." << std::endl;
}

void JobService::register_handler(const std::string& job_type, JobHandler handler) {
    std::lock_guard<std::mutex> lock(m_handlers_mutex);
    m_handlers[job_type] = handler;
    std::cout << "[JobService] Registered handler for job type: " << job_type << std::endl;
}

void JobService::loop() {
    while (m_running) {
        try {
            auto job_opt = m_db->get_next_job();
            if (job_opt) {
                process_job(*job_opt);
            } else {
                // No jobs, sleep for a bit
                // We can make this adaptive or event-driven later if needed
                std::this_thread::sleep_for(std::chrono::seconds(2));
            }
        } catch (const std::exception& e) {
            std::cerr << "[JobService] Error in job loop: " << e.what() << std::endl;
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    }
}

void JobService::process_job(const Job& job) {
    std::cout << "[JobService] Processing Job ID " << job.id << " (Type: " << job.type << ")" << std::endl;
    
    // Mark as processing
    m_db->update_job_status(job.id, "processing");

    JobHandler handler = nullptr;
    {
        std::lock_guard<std::mutex> lock(m_handlers_mutex);
        auto it = m_handlers.find(job.type);
        if (it != m_handlers.end()) {
            handler = it->second;
        }
    }

    if (handler) {
        try {
            bool success = handler(job.payload);
            if (success) {
                m_db->update_job_status(job.id, "completed");
                std::cout << "[JobService] Job ID " << job.id << " completed successfully." << std::endl;
            } else {
                m_db->update_job_status(job.id, "failed", "Handler returned false");
                std::cerr << "[JobService] Job ID " << job.id << " failed." << std::endl;
            }
        } catch (const std::exception& e) {
            m_db->update_job_status(job.id, "failed", e.what());
            std::cerr << "[JobService] Job ID " << job.id << " threw exception: " << e.what() << std::endl;
        }
    } else {
        m_db->update_job_status(job.id, "failed", "No handler for job type: " + job.type);
        std::cerr << "[JobService] No handler for job type: " << job.type << std::endl;
    }
}

} // namespace diffusion_desk
