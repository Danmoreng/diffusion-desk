#include "tool_service.hpp"
#include <iostream>
#include <sstream>

namespace mysti {

ToolService::ToolService(std::shared_ptr<Database> db, int sd_port, int llm_port, const std::string& token)
    : m_db(db), m_sd_port(sd_port), m_llm_port(llm_port), m_token(token) {}

mysti::json ToolService::execute_tool(const std::string& name, const mysti::json& arguments) {
    if (name == "get_library_items") {
        return get_library_items(arguments.value("category", ""));
    } else if (name == "apply_style") {
        return apply_style(arguments.value("style_name", ""), arguments.value("current_prompt", ""));
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

mysti::json ToolService::apply_style(const std::string& style_name, const std::string& current_prompt) {
    if (!m_db) return {{"error", "db_not_available"}};
    
    auto styles = m_db->get_styles();
    for (auto& s : styles) {
        if (s["name"] == style_name) {
            std::string template_str = s["prompt"];
            std::string final_prompt = current_prompt;
            
            if (template_str.find("{prompt}") != std::string::npos) {
                // Replace placeholder
                size_t pos = template_str.find("{prompt}");
                final_prompt = template_str.replace(pos, 8, current_prompt);
            } else {
                final_prompt = current_prompt + ", " + template_str;
            }
            
            mysti::json res;
            res["new_prompt"] = final_prompt;
            res["applied_style"] = style_name;
            return res;
        }
    }
    
    return {{"error", "style_not_found"}};
}

mysti::json ToolService::search_history(const std::string& query) {
    if (!m_db) return mysti::json::array();
    
    // We'll use FTS5 search
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement q(m_db->get_db(), R"(
            SELECT uuid, prompt, timestamp 
            FROM generations 
            WHERE id IN (SELECT rowid FROM generations_fts WHERE generations_fts MATCH ?)
            ORDER BY timestamp DESC LIMIT 10
        )");
        q.bind(1, query);
        while (q.executeStep()) {
            mysti::json item;
            item["uuid"] = q.getColumn(0).getText();
            item["prompt"] = q.getColumn(1).getText();
            item["timestamp"] = q.getColumn(2).getText();
            results.push_back(item);
        }
    } catch (...) {
        // Fallback to LIKE if FTS fails
        SQLite::Statement q(m_db->get_db(), "SELECT uuid, prompt, timestamp FROM generations WHERE prompt LIKE ? ORDER BY timestamp DESC LIMIT 10");
        q.bind(1, "%" + query + "%");
        while (q.executeStep()) {
            mysti::json item;
            item["uuid"] = q.getColumn(0).getText();
            item["prompt"] = q.getColumn(1).getText();
            item["timestamp"] = q.getColumn(2).getText();
            results.push_back(item);
        }
    }
    return results;
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
