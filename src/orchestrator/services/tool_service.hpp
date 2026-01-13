#pragma once

#include "database.hpp"
#include "resource_manager.hpp"
#include "httplib.h"
#include <memory>
#include <string>

namespace diffusion_desk {

class ToolService {
public:
    ToolService(std::shared_ptr<Database> db, int sd_port, int llm_port, const std::string& token);

    // Main entry point for tool execution from LLM
    diffusion_desk::json execute_tool(const std::string& name, const diffusion_desk::json& arguments);

    // Tool Definitions
    diffusion_desk::json get_library_items(const std::string& category);
    diffusion_desk::json search_history(const std::string& query);
    diffusion_desk::json get_vram_status();

private:
    std::shared_ptr<Database> m_db;
    int m_sd_port;
    int m_llm_port;
    std::string m_token;
};

} // namespace diffusion_desk
