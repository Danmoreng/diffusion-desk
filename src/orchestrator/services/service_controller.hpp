#pragma once

#include "httplib.h"
#include "database.hpp"
#include "resource_manager.hpp"
#include "ws_manager.hpp"
#include "tool_service.hpp"
#include "utils/common.hpp"
#include <memory>

namespace mysti {

class ServiceController {
public:
    ServiceController(std::shared_ptr<Database> db, 
                      std::shared_ptr<ResourceManager> res_mgr,
                      std::shared_ptr<WsManager> ws_mgr,
                      std::shared_ptr<ToolService> tool_svc,
                      int sd_port, int llm_port,
                      const std::string& token);
    
    void register_routes(httplib::Server& svr, const SDSvrParams& params);

    // State accessors for recovery
    std::string get_last_sd_model_req() { std::lock_guard<std::mutex> lock(m_state_mutex); return m_last_sd_model_req_body; }
    std::string get_last_llm_model_req() { std::lock_guard<std::mutex> lock(m_state_mutex); return m_last_llm_model_req_body; }
    
    void set_last_llm_model_req(const std::string& body) { std::lock_guard<std::mutex> lock(m_state_mutex); m_last_llm_model_req_body = body; }

    void set_on_generation_callback(std::function<void()> cb) { m_on_generation = cb; }

private:
    std::shared_ptr<Database> m_db;
    std::shared_ptr<ResourceManager> m_res_mgr;
    std::shared_ptr<WsManager> m_ws_mgr;
    std::shared_ptr<ToolService> m_tool_svc;
    int m_sd_port;
    int m_llm_port;
    std::string m_token;

    std::string m_last_sd_model_req_body;
    std::string m_last_llm_model_req_body;
    std::mutex m_state_mutex;

    std::function<void()> m_on_generation;

    // Helper for generating previews (moved from main)
    void generate_style_preview(Style style, std::string output_dir);
    void generate_model_preview(std::string model_id, std::string output_dir);
};

} // namespace mysti
