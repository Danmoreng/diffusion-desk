#pragma once

#include "httplib.h"
#include "database.hpp"
#include "resource_manager.hpp"
#include "ws_manager.hpp"
#include "tool_service.hpp"
#include "utils/common.hpp"
#include <memory>

namespace diffusion_desk {

class ServiceController {
public:
    ServiceController(std::shared_ptr<Database> db, 
                      std::shared_ptr<ResourceManager> res_mgr,
                      std::shared_ptr<WsManager> ws_mgr,
                      std::shared_ptr<ToolService> tool_svc,
                      int sd_port, int llm_port,
                      const std::string& token);
    
    void register_routes(httplib::Server& svr, const SDSvrParams& params);

    // Startup / State Restoration
    void load_last_presets(const SDSvrParams& params);

    // State accessors for recovery
    std::string get_last_sd_model_req() { std::lock_guard<std::mutex> lock(m_state_mutex); return m_last_sd_model_req_body; }
    std::string get_last_llm_model_req() { std::lock_guard<std::mutex> lock(m_state_mutex); return m_last_llm_model_req_body; }
    
    void set_last_llm_model_req(const std::string& body) { std::lock_guard<std::mutex> lock(m_state_mutex); m_last_llm_model_req_body = body; }

    void set_on_generation_callback(std::function<void()> cb) { m_on_generation = cb; }
    void set_generation_active_callback(std::function<void(bool)> cb) { m_generation_active_cb = cb; }

    // Smart Queue: Notify from external metrics if needed
    void notify_model_loaded(const std::string& type, const std::string& model_id);

    // Internal Smart Queue helpers
    bool ensure_sd_model_loaded(const std::string& model_id, const SDSvrParams& params);
    bool ensure_llm_loaded(const std::string& model_id, const SDSvrParams& params);
    bool load_llm_preset(int preset_id, const SDSvrParams& params);

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

    int m_last_image_preset_id = -1;
    int m_last_llm_preset_id = -1;

    // Smart Queue State
    std::mutex m_load_mutex;
    std::condition_variable m_load_cv;
    std::string m_currently_loading_sd;
    std::string m_currently_loading_llm;
    std::string m_active_sd_model;
    std::string m_active_llm_model;
    bool m_sd_loaded = false;
    bool m_llm_loaded = false;

    // Helper for generating previews (moved from main)
    void generate_style_preview(Style style, std::string output_dir);
    void generate_model_preview(std::string model_id, std::string output_dir);

    std::function<void()> m_on_generation;
    std::function<void(bool)> m_generation_active_cb;
};

} // namespace diffusion_desk
