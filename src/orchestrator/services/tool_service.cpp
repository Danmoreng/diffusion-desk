#include "tool_service.hpp"
#include <iostream>
#include <sstream>

namespace mysti {

ToolService::ToolService(std::shared_ptr<Database> db, int sd_port, int llm_port, const std::string& token)
    : m_db(db), m_sd_port(sd_port), m_llm_port(llm_port), m_token(token) {}

mysti::json ToolService::execute_tool(const std::string& name, const mysti::json& arguments) {
    if (name == "get_library_items") {
        return get_library_items(arguments.value("category", ""));
    } else if (name == "search_history") {
        return search_history(arguments.value("query", ""));
    } else if (name == "get_vram_status") {
        return get_vram_status();
    }
    
    mysti::json err;
    err["error"] = "unknown_tool";
    return err;
}

mysti::json ToolService::get_library_items(const std::string& category) {
    if (!m_db) return mysti::json::array();
    return m_db->get_library_items(category);
}

mysti::json ToolService::search_history(const std::string& query) {
    if (!m_db) return mysti::json::array();
    return m_db->search_generations(query, 10);
}

mysti::json ToolService::get_vram_status() {
    float total = get_total_vram_gb();
    float free = get_free_vram_gb();
    mysti::json res;
    res["total_gb"] = total;
    res["free_gb"] = free;
    res["usage_percent"] = (total > 0) ? ((total - free) / total * 100.0f) : 0.0f;
    return res;
}

} // namespace mysti
