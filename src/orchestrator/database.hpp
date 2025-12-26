#pragma once

#include "utils/common.hpp"
#include <SQLiteCpp/SQLiteCpp.h>
#include <string>
#include <memory>
#include <vector>
#include <optional>
#include <mutex>

namespace mysti {

struct Generation {
    std::string uuid;
    std::string file_path;
    std::string prompt;
    std::string negative_prompt;
    int64_t seed = -1;
    int width = 512;
    int height = 512;
    int steps = 20;
    float cfg_scale = 7.0f;
    double generation_time = 0.0;
    std::string model_hash;
    bool is_favorite = false;
    bool auto_tagged = false;
};

struct TagInfo {
    std::string name;
    std::string category;
    int count;
};

class Database {
public:
    explicit Database(const std::string& db_path);
    ~Database();

    // Prevent copying
    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;

    // Initialization
    void init_schema();

    // Queries
    mysti::json get_generations(int limit = 50, int offset = 0, const std::string& tag = "");
    mysti::json get_tags();
    
    // Existence Check
    bool generation_exists(const std::string& file_path);

    // Insertion
    void insert_generation(const Generation& gen);

    // Tag Management
    void add_tag(const std::string& uuid, const std::string& tag, const std::string& source = "user");
    // Overload for internal use when we already have the ID (avoids lookups if we trust the ID)
    void add_tag_by_id(int generation_id, const std::string& tag, const std::string& source = "user");
    
    void remove_tag(const std::string& uuid, const std::string& tag);
    
    // Internal / Services
    // Returns list of (id, uuid, prompt)
    std::vector<std::tuple<int, std::string, std::string>> get_untagged_generations(int limit = 10);
    void mark_as_tagged(int id);

private:
    SQLite::Database m_db;
    std::mutex m_mutex; // Protects access to m_db
};

} // namespace mysti