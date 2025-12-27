#pragma once

#include "utils/common.hpp"
#include <SQLiteCpp/SQLiteCpp.h>
#include <string>
#include <memory>
#include <vector>
#include <optional>

namespace mysti {

class Database {
public:
    explicit Database(const std::string& db_path);
    ~Database();

    // Prevent copying
    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;

    // Initialization
    void init_schema();

    // Generations
    void save_generation(const mysti::json& data);
    void set_favorite(const std::string& uuid, bool favorite);
    void remove_generation(const std::string& uuid);
    std::string get_generation_filepath(const std::string& uuid);
    mysti::json get_generations(int limit = 50, int offset = 0, const std::string& tag = "", const std::string& model = "");
    mysti::json get_tags();
    
    // Model Metadata
    void save_model_metadata(const std::string& model_hash, const mysti::json& metadata);
    mysti::json get_model_metadata(const std::string& model_hash);
    
    // Tag Management
    void add_tag(const std::string& uuid, const std::string& tag, const std::string& source = "user");
    void remove_tag(const std::string& uuid, const std::string& tag);
    
    // Internal
    std::vector<std::pair<int, std::string>> get_untagged_generations(int limit = 10);
    void mark_as_tagged(int id);

    // Direct access if needed (prefer specific methods)
    SQLite::Database& get_db() { return m_db; }

private:
    SQLite::Database m_db;
};

} // namespace mysti
