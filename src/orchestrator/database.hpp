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
    std::string model_id;
    bool is_favorite = false;
    int rating = 0;
    bool auto_tagged = false;
    std::string params_json;
};

struct TagInfo {
    std::string name;
    std::string category;
    int count;
};

struct Style {
    std::string name;
    std::string prompt;
    std::string negative_prompt;
    std::string preview_path;
};

struct LibraryItem {
    int id = 0;
    std::string label;
    std::string content;
    std::string category;
    std::string preview_path;
    int usage_count = 0;
};

struct Job {
    int id = 0;
    std::string type;
    mysti::json payload;
    std::string status;
    std::string error;
    int priority = 0;
    std::string created_at;
};

struct ImagePreset {
    int id = 0;
    std::string name;
    std::string unet_path;
    std::string vae_path;
    std::string clip_l_path;
    std::string clip_g_path;
    std::string t5xxl_path;
    int vram_weights_mb_estimate = 0;
    int vram_weights_mb_measured = 0;
    mysti::json default_params;
    mysti::json preferred_params;
};

struct LlmPreset {
    int id = 0;
    std::string name;
    std::string model_path;
    std::string mmproj_path;
    int n_ctx = 2048;
    std::vector<std::string> capabilities;
    std::string role;
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

    // Generations
    void save_generation(const mysti::json& data);
    void set_favorite(const std::string& uuid, bool favorite);
    void set_rating(const std::string& uuid, int rating);
    void remove_generation(const std::string& uuid);
    std::string get_generation_filepath(const std::string& uuid);
    mysti::json get_generations(int limit = 50, int offset = 0, const std::vector<std::string>& tags = {}, const std::string& model = "", int min_rating = 0);
    mysti::json search_generations(const std::string& query, int limit = 50);
    mysti::json get_tags();
    
    // Styles
    void save_style(const Style& style);
    mysti::json get_styles();
    void delete_style(const std::string& name);

    // Prompt Library
    void add_library_item(const LibraryItem& item);
    mysti::json get_library_items(const std::string& category = "");
    void delete_library_item(int id);
    void increment_library_usage(int id);

    // Job Queue
    int add_job(const std::string& type, const mysti::json& payload, int priority = 0);
    std::optional<Job> get_next_job();
    void update_job_status(int id, const std::string& status, const std::string& error = "");

    // Asset Management
    void add_generation_file(int generation_id, const std::string& type, const std::string& path);
    std::vector<std::string> get_generation_files(int generation_id, const std::string& type = "");

    // Presets
    void save_image_preset(const ImagePreset& preset);
    mysti::json get_image_presets();
    void delete_image_preset(int id);

    void save_llm_preset(const LlmPreset& preset);
    mysti::json get_llm_presets();
    void delete_llm_preset(int id);
    
    // Model Metadata
    void save_model_metadata(const std::string& model_id, const mysti::json& metadata);
    mysti::json get_model_metadata(const std::string& model_id);
    mysti::json get_all_models_metadata();
    
    // Existence Check
    bool generation_exists(const std::string& file_path);

    // Insertion
    int insert_generation(const Generation& gen);
    void insert_generation_with_tags(const Generation& gen, const std::vector<std::string>& tags);
    
    // Tag Management
    void add_tag(const std::string& uuid, const std::string& tag, const std::string& source = "user");
    // Overload for internal use when we already have the ID (avoids lookups if we trust the ID)
    void add_tag_by_id(int generation_id, const std::string& tag, const std::string& source = "user");
    
    void remove_tag(const std::string& uuid, const std::string& tag);
    void delete_unused_tags();
    
    // Internal / Services
    // Returns list of (id, uuid, prompt)
    std::vector<std::tuple<int, std::string, std::string>> get_untagged_generations(int limit = 10);
    void mark_as_tagged(int id);
    // Accessor
    SQLite::Database& get_db() { return m_db; }

private:
    int get_schema_version();
    void set_schema_version(int version);

    // Migration steps
    void migrate_to_v1();
    void migrate_to_v2();
    void migrate_to_v3();

    SQLite::Database m_db;
    std::recursive_mutex m_mutex; // Protects access to m_db, recursive to allow nested calls
};

} // namespace mysti
