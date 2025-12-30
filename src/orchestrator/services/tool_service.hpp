#pragma once

#include "database.hpp"
#include "resource_manager.hpp"
#include "httplib.h"
#include <memory>
#include <string>

namespace mysti {

class ToolService {
public:
    ToolService(std::shared_ptr<Database> db, int sd_port, int llm_port, const std::string& token);

    // Main entry point for tool execution from LLM
    mysti::json execute_tool(const std::string& name, const mysti::json& arguments);

    // Tool Definitions
    mysti::json get_library_items(const std::string& category);
    mysti::json apply_style(const std::string& style_name, const std::string& current_prompt);
    mysti::json search_history(const std::string& query);
    mysti::json get_vram_status();

private:
    std::shared_ptr<Database> m_db;
    int m_sd_port;
    int m_llm_port;
    std::string m_token;
};

} // namespace mysti
