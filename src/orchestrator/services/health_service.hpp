#pragma once

#include "orchestrator/process_manager.hpp"
#include "orchestrator/ws_manager.hpp"
#include <atomic>

namespace mysti {

class HealthService {
public:
    HealthService(ProcessManager& pm, 
                 ProcessManager::ProcessInfo& sd_proc, 
                 ProcessManager::ProcessInfo& llm_proc,
                 int sd_port, int llm_port,
                 const std::string& sd_exe, const std::string& llm_exe,
                 const std::vector<std::string>& sd_args, const std::vector<std::string>& llm_args,
                 const std::string& sd_log, const std::string& llm_log,
                 const std::string& token,
                 std::shared_ptr<WsManager> ws_mgr,
                 std::atomic<bool>* external_shutdown = nullptr);
                     ~HealthService();
                 
                     void start();
                     void stop();
                 
                     bool is_sd_alive() const;
                     bool is_llm_alive() const;
                 
                     void set_model_state_callbacks(
                         std::function<std::string()> get_sd_state,
                         std::function<std::string()> get_llm_state
                     );
                 
                     void set_max_sd_crashes(int max);
                 
                 private:
                     void loop();
                     void restart_sd_worker();
                     void restart_llm_worker();
                     bool wait_for_health(int port, int timeout_sec = 30);
                 
                     ProcessManager& m_pm;
                     ProcessManager::ProcessInfo& m_sd_proc;
                     ProcessManager::ProcessInfo& m_llm_proc;
                     int m_sd_port;
                     int m_llm_port;
                     std::string m_sd_exe;
                     std::string m_llm_exe;
                     std::vector<std::string> m_sd_args;
                     std::vector<std::string> m_llm_args;
                     std::string m_sd_log;
                     std::string m_llm_log;
                     std::string m_token;
                     std::shared_ptr<WsManager> m_ws_mgr;
    int m_max_sd_crashes = 2;
    int m_sd_crash_count = 0;
    
    std::function<std::string()> m_get_sd_state;
    std::function<std::string()> m_get_llm_state;

    std::atomic<bool> m_running{false};
    std::atomic<bool>* m_external_shutdown = nullptr;
    std::thread m_thread;
    mutable std::mutex m_proc_mutex; // Protects process spawning/killing logic
};

} // namespace mysti
