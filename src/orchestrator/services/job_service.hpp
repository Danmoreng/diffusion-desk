#pragma once

#include "../database.hpp"
#include <thread>
#include <atomic>
#include <functional>
#include <map>
#include <memory>
#include <mutex>

namespace mysti {

class JobService {
public:
    using JobHandler = std::function<bool(const mysti::json&)>;

    JobService(std::shared_ptr<Database> db);
    ~JobService();

    void start();
    void stop();

    void register_handler(const std::string& job_type, JobHandler handler);

private:
    void loop();
    void process_job(const Job& job);

    std::shared_ptr<Database> m_db;
    std::atomic<bool> m_running{false};
    std::thread m_thread;
    std::map<std::string, JobHandler> m_handlers;
    std::mutex m_handlers_mutex;
};

} // namespace mysti
