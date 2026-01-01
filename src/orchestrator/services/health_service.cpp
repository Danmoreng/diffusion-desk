#include "health_service.hpp"
#include "httplib.h"
#include "utils/common.hpp"
#include <iostream>
#include <chrono>

namespace mysti {

HealthService::HealthService(ProcessManager& pm, 
             ProcessManager::ProcessInfo& sd_proc, 
             ProcessManager::ProcessInfo& llm_proc,
             int sd_port, int llm_port,
             const std::string& sd_exe, const std::string& llm_exe,
             const std::vector<std::string>& sd_args, const std::vector<std::string>& llm_args,
             const std::string& sd_log, const std::string& llm_log,
             const std::string& token,
             std::shared_ptr<WsManager> ws_mgr)
    : m_pm(pm), m_sd_proc(sd_proc), m_llm_proc(llm_proc),
      m_sd_port(sd_port), m_llm_port(llm_port),
      m_sd_exe(sd_exe), m_llm_exe(llm_exe),
      m_sd_args(sd_args), m_llm_args(llm_args),
      m_sd_log(sd_log), m_llm_log(llm_log),
      m_token(token), m_ws_mgr(ws_mgr)
{}

HealthService::~HealthService() {
    stop();
}

void HealthService::set_model_state_callbacks(
    std::function<std::string()> get_sd_state,
    std::function<std::string()> get_llm_state
) {
    m_get_sd_state = get_sd_state;
    m_get_llm_state = get_llm_state;
}

void HealthService::set_max_sd_crashes(int max) {
    m_max_sd_crashes = max;
}

void HealthService::start() {
    if (m_running) return;
    m_running = true;
    m_thread = std::thread(&HealthService::loop, this);
    std::cout << "[Health Service] Thread started." << std::endl;
}

void HealthService::stop() {
    if (!m_running) return;
    m_running = false;
    if (m_thread.joinable()) {
        m_thread.join();
    }
    std::cout << "[Health Service] Thread stopped." << std::endl;
}

bool HealthService::is_sd_alive() const {
    std::lock_guard<std::mutex> lock(m_proc_mutex);
    return m_pm.is_running(m_sd_proc);
}

bool HealthService::is_llm_alive() const {
    std::lock_guard<std::mutex> lock(m_proc_mutex);
    return m_pm.is_running(m_llm_proc);
}

bool HealthService::wait_for_health(int port, int timeout_sec) {
    httplib::Client cli("127.0.0.1", port);
    cli.set_connection_timeout(1);
    httplib::Headers headers;
    if (!m_token.empty()) {
        headers.emplace("X-Internal-Token", m_token);
    }
    for (int i = 0; i < timeout_sec; ++i) {
        if (!m_running) return false;
        if (auto res = cli.Get("/internal/health", headers)) {
            if (res->status == 200) return true;
        }
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    return false;
}

void HealthService::restart_sd_worker() {
    if (!m_running) return;
    LOG_WARN("Detected SD Worker failure. Restarting...");
    
    if (m_ws_mgr) {
        mysti::json alert;
        alert["type"] = "system_alert";
        alert["level"] = "warning";
        alert["message"] = "SD Worker crashed! Restarting and attempting to restore model state...";
        m_ws_mgr->broadcast(alert);
    }

    m_sd_crash_count++;
    std::vector<std::string> current_sd_args = m_sd_args;
    // ... rest of the code ...

    // Note: Mutating state here isn't ideal but we need to track safe mode
    // Ideally orchestrator logic would handle args logic. 
    // Assuming simple args for now.
    
    // Check safe mode logic:
    // If we crash too often, we might want to drop args or something.
    // Logic from main was: clear last_sd_model_req_body
    
    std::string model_body = "";
    if (m_sd_crash_count < m_max_sd_crashes && m_get_sd_state) {
        model_body = m_get_sd_state();
    } else if (m_sd_crash_count >= m_max_sd_crashes) {
        LOG_WARN("SD Worker entered Safe Mode (Model auto-load disabled).");
    }

    {
        std::lock_guard<std::mutex> lock(m_proc_mutex);
        m_pm.terminate(m_sd_proc);
        if (!m_pm.spawn(m_sd_exe, current_sd_args, m_sd_proc, m_sd_log)) {
            LOG_ERROR("Failed to respawn SD Worker!");
            return;
        }
    }

    if (wait_for_health(m_sd_port)) {
        LOG_INFO("SD Worker back online.");
        
        if (m_ws_mgr) {
            mysti::json alert;
            alert["type"] = "system_alert";
            alert["level"] = "success";
            alert["message"] = "SD Worker recovered successfully.";
            m_ws_mgr->broadcast(alert);
        }

        if (!model_body.empty()) {
            LOG_INFO("Restoring SD model...");
            httplib::Client cli("127.0.0.1", m_sd_port);
            cli.set_read_timeout(300);
            httplib::Headers headers;
            if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);
            
            auto res = cli.Post("/v1/models/load", headers, model_body, "application/json");
            if (res && res->status == 200) {
                LOG_INFO("SD model restored successfully.");
                m_sd_crash_count = 0; 
            } else {
                LOG_ERROR("Failed to restore SD model.");
            }
        } else {
            m_sd_crash_count = 0;
        }
    } else {
        LOG_ERROR("SD Worker failed to recover within timeout.");
    }
}

void HealthService::restart_llm_worker() {
    if (!m_running) return;
    LOG_WARN("Detected LLM Worker failure. Restarting...");
    
    if (m_ws_mgr) {
        mysti::json alert;
        alert["type"] = "system_alert";
        alert["level"] = "warning";
        alert["message"] = "LLM Worker crashed! Restarting...";
        m_ws_mgr->broadcast(alert);
    }

    {
        std::lock_guard<std::mutex> lock(m_proc_mutex);
        m_pm.terminate(m_llm_proc);
        if (!m_pm.spawn(m_llm_exe, m_llm_args, m_llm_proc, m_llm_log)) {
            LOG_ERROR("Failed to respawn LLM Worker!");
            return;
        }
    }

    if (wait_for_health(m_llm_port)) {
        LOG_INFO("LLM Worker back online.");

        if (m_ws_mgr) {
            mysti::json alert;
            alert["type"] = "system_alert";
            alert["level"] = "success";
            alert["message"] = "LLM Worker recovered successfully.";
            m_ws_mgr->broadcast(alert);
        }

        std::string model_body = "";
        if (m_get_llm_state) model_body = m_get_llm_state();

        if (!model_body.empty()) {
            LOG_INFO("Restoring LLM model...");
            httplib::Client cli("127.0.0.1", m_llm_port);
            cli.set_read_timeout(300);
            httplib::Headers headers;
            if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);

            auto res = cli.Post("/v1/llm/load", headers, model_body, "application/json");
            if (res && res->status == 200) {
                LOG_INFO("LLM model restored successfully.");
            } else {
                LOG_ERROR("Failed to restore LLM model.");
            }
        }
    } else {
        LOG_ERROR("LLM Worker failed to recover within timeout.");
    }
}

void HealthService::loop() {
    httplib::Client sd_cli("127.0.0.1", m_sd_port);
    httplib::Client llm_cli("127.0.0.1", m_llm_port);
    sd_cli.set_connection_timeout(1);
    llm_cli.set_connection_timeout(1);

    int sd_fail_count = 0;
    int llm_fail_count = 0;
    const int MAX_FAILURES = 3;

    while (m_running) {
        std::this_thread::sleep_for(std::chrono::seconds(2));

        httplib::Headers headers;
        if (!m_token.empty()) headers.emplace("X-Internal-Token", m_token);

        // --- SD Check ---
        bool sd_alive = false;
        {
            std::lock_guard<std::mutex> lock(m_proc_mutex);
            sd_alive = m_pm.is_running(m_sd_proc);
        }

        if (sd_alive) {
            if (auto res = sd_cli.Get("/internal/health", headers)) {
                sd_fail_count = 0;
            } else {
                sd_fail_count++;
                if (sd_fail_count >= MAX_FAILURES) {
                    LOG_WARN("SD Worker unresponsive (HTTP).");
                    sd_alive = false; 
                }
            }
        }
        
        if (!sd_alive) {
            restart_sd_worker();
            sd_fail_count = 0;
        }

        // --- LLM Check ---
        bool llm_alive = false;
        {
            std::lock_guard<std::mutex> lock(m_proc_mutex);
            llm_alive = m_pm.is_running(m_llm_proc);
        }

        if (llm_alive) {
            if (auto res = llm_cli.Get("/internal/health", headers)) {
                llm_fail_count = 0;
            } else {
                llm_fail_count++;
                if (llm_fail_count >= MAX_FAILURES) {
                    LOG_WARN("LLM Worker unresponsive (HTTP).");
                    llm_alive = false;
                }
            }
        }

        if (!llm_alive) {
            restart_llm_worker();
            llm_fail_count = 0;
        }
    }
}

} // namespace mysti
