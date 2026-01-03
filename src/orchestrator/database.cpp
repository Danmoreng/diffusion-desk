#include "database.hpp"
#include <iostream>
#include <algorithm>

namespace mysti {

// Internal helper to parse a generation row from a query (assumes all columns are selected)
static mysti::json parse_generation_row(SQLite::Statement& q, SQLite::Database& db) {
    mysti::json gen;
    std::string file_path = q.getColumn("file_path").getText();
    std::string name = file_path;
    size_t last_slash = file_path.find_last_of("/\\");
    if (last_slash != std::string::npos) name = file_path.substr(last_slash + 1);

    gen["id"] = q.getColumn("uuid").getText();
    gen["name"] = name;
    gen["file_path"] = file_path;
    gen["timestamp"] = q.getColumn("timestamp").getText();

    mysti::json params;
    try {
        std::string pjson = q.getColumn("params_json").getText();
        if (!pjson.empty()) params = mysti::json::parse(pjson);
    } catch (...) {}

    params["prompt"] = q.getColumn("prompt").getText();
    params["negative_prompt"] = q.getColumn("negative_prompt").getText();
    params["seed"] = (long long)q.getColumn("seed").getInt64();
    params["width"] = q.getColumn("width").getInt();
    params["height"] = q.getColumn("height").getInt();
    params["steps"] = q.getColumn("steps").getInt();
    params["cfg_scale"] = q.getColumn("cfg_scale").getDouble();
    params["model_id"] = q.getColumn("model_id").getText();

    gen["params"] = params;
    gen["is_favorite"] = q.getColumn("is_favorite").getInt() != 0;
    try { gen["rating"] = q.getColumn("rating").getInt(); } catch (...) { gen["rating"] = 0; }

    mysti::json tags_arr = mysti::json::array();
    SQLite::Statement tag_query(db, "SELECT t.name FROM tags t JOIN image_tags it ON t.id = it.tag_id WHERE it.generation_id = ?");
    tag_query.bind(1, q.getColumn("id").getInt());
    while (tag_query.executeStep()) {
        tags_arr.push_back(tag_query.getColumn(0).getText());
    }
    gen["tags"] = tags_arr;
    return gen;
}

Database::Database(const std::string& db_path) 
    : m_db(db_path, SQLite::OPEN_READWRITE | SQLite::OPEN_CREATE) 
{
    try {
        m_db.exec("PRAGMA journal_mode=WAL;");
        m_db.exec("PRAGMA synchronous=NORMAL;");
        m_db.exec("PRAGMA foreign_keys=ON;");
    } catch (const std::exception& e) {
        std::cerr << "[Database] Error setting pragmas: " << e.what() << std::endl;
    }
}

Database::~Database() {}

void Database::init_schema() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        int current_version = get_schema_version();
        std::cout << "[Database] Current schema version: " << current_version << std::endl;

        if (current_version < 1) {
            migrate_to_v1();
            set_schema_version(1);
            std::cout << "[Database] Migrated to version 1 (Baseline)" << std::endl;
        }

        if (current_version < 2) {
            migrate_to_v2();
            set_schema_version(2);
            std::cout << "[Database] Migrated to version 2 (Assets, Jobs, Prompt Library)" << std::endl;
        }

        if (current_version < 3) {
            migrate_to_v3();
            set_schema_version(3);
            std::cout << "[Database] Migrated to version 3 (Presets)" << std::endl;
        }

        std::cout << "[Database] Schema initialized successfully." << std::endl;

    } catch (const std::exception& e) {
        std::cerr << "[Database] Schema initialization failed: " << e.what() << std::endl;
        throw; 
    }
}

int Database::get_schema_version() {
    return m_db.execAndGet("PRAGMA user_version").getInt();
}

void Database::set_schema_version(int version) {
    m_db.exec("PRAGMA user_version = " + std::to_string(version));
}

void Database::migrate_to_v1() {
    SQLite::Transaction transaction(m_db);

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS generations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT UNIQUE NOT NULL,
            file_path TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            prompt TEXT,
            negative_prompt TEXT,
            seed INTEGER,
            width INTEGER,
            height INTEGER,
            steps INTEGER,
            cfg_scale REAL,
            model_hash TEXT,
            is_favorite BOOLEAN DEFAULT 0,
            parent_uuid TEXT,
            generation_time REAL,
            auto_tagged BOOLEAN DEFAULT 0,
            model_id TEXT,
            rating INTEGER DEFAULT 0,
            params_json TEXT,
            FOREIGN KEY(parent_uuid) REFERENCES generations(uuid)
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            category TEXT DEFAULT 'general'
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS image_tags (
            generation_id INTEGER,
            tag_id INTEGER,
            source TEXT DEFAULT 'user',
            confidence REAL DEFAULT 1.0,
            PRIMARY KEY(generation_id, tag_id),
            FOREIGN KEY(generation_id) REFERENCES generations(id) ON DELETE CASCADE,
            FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS styles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            prompt TEXT NOT NULL,
            negative_prompt TEXT,
            preview_path TEXT
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS models (
            id TEXT PRIMARY KEY,
            metadata TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS prompt_templates (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            content TEXT NOT NULL,
            description TEXT
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS model_metadata (
            model_hash TEXT PRIMARY KEY,
            name TEXT,
            description TEXT,
            trigger_words TEXT,
            preferred_params TEXT,
            last_used DATETIME DEFAULT CURRENT_TIMESTAMP
        );
    )");

    m_db.exec("CREATE INDEX IF NOT EXISTS idx_generations_timestamp ON generations(timestamp DESC);");
    m_db.exec("CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);");
    m_db.exec("CREATE INDEX IF NOT EXISTS idx_generations_model_id ON generations(model_id);");
    m_db.exec("CREATE INDEX IF NOT EXISTS idx_generations_rating ON generations(rating);");

    transaction.commit();
}

void Database::migrate_to_v2() {
    SQLite::Transaction transaction(m_db);

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS generation_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            generation_id INTEGER NOT NULL,
            file_type TEXT NOT NULL,
            file_path TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(generation_id) REFERENCES generations(id) ON DELETE CASCADE
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            payload TEXT,
            status TEXT DEFAULT 'pending',
            error TEXT,
            priority INTEGER DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            completed_at DATETIME
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS prompt_library (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            label TEXT NOT NULL,
            content TEXT NOT NULL,
            category TEXT DEFAULT 'Style',
            preview_path TEXT,
            usage_count INTEGER DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
    )");

    try { 
        m_db.exec("ALTER TABLE tags ADD COLUMN normalized_name TEXT;"); 
        m_db.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_normalized ON tags(normalized_name);");
    } catch (...) {}

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS tag_aliases (
            alias TEXT PRIMARY KEY,
            target_tag_id INTEGER NOT NULL,
            FOREIGN KEY(target_tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );
    )");

    try {
        m_db.exec(R"(
            CREATE VIRTUAL TABLE IF NOT EXISTS generations_fts USING fts5(
                uuid UNINDEXED,
                prompt,
                negative_prompt,
                content='generations',
                content_rowid='id'
            );
        )");

        m_db.exec(R"(
            CREATE TRIGGER IF NOT EXISTS generations_ai AFTER INSERT ON generations BEGIN
                INSERT INTO generations_fts(rowid, uuid, prompt, negative_prompt) VALUES (new.id, new.uuid, new.prompt, new.negative_prompt);
            END;
        )");
        m_db.exec(R"(
            CREATE TRIGGER IF NOT EXISTS generations_ad AFTER DELETE ON generations BEGIN
                INSERT INTO generations_fts(generations_fts, rowid, uuid, prompt, negative_prompt) VALUES('delete', old.id, old.uuid, old.prompt, old.negative_prompt);
            END;
        )");
        m_db.exec(R"(
            CREATE TRIGGER IF NOT EXISTS generations_au AFTER UPDATE ON generations BEGIN
                INSERT INTO generations_fts(generations_fts, rowid, uuid, prompt, negative_prompt) VALUES('delete', old.id, old.uuid, old.prompt, old.negative_prompt);
                INSERT INTO generations_fts(rowid, uuid, prompt, negative_prompt) VALUES (new.id, new.uuid, new.prompt, new.negative_prompt);
            END;
        )");
        
        m_db.exec("INSERT INTO generations_fts(generations_fts) VALUES('rebuild');");
    } catch (const std::exception& e) {
        std::cerr << "[Database] FTS5 support missing or failed: " << e.what() << std::endl;
    }

    transaction.commit();
}

void Database::migrate_to_v3() {
    SQLite::Transaction transaction(m_db);

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS image_presets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            unet_path TEXT,
            vae_path TEXT,
            clip_l_path TEXT,
            clip_g_path TEXT,
            t5xxl_path TEXT,
            vram_weights_mb_estimate INTEGER DEFAULT 0,
            vram_weights_mb_measured INTEGER DEFAULT 0,
            default_params TEXT,
            preferred_params TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
    )");

    m_db.exec(R"(
        CREATE TABLE IF NOT EXISTS llm_presets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE NOT NULL,
            model_path TEXT NOT NULL,
            mmproj_path TEXT,
            n_ctx INTEGER DEFAULT 2048,
            capabilities TEXT,
            role TEXT DEFAULT 'Assistant',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
    )");

    transaction.commit();
}

void Database::save_generation(const mysti::json& j) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        std::string uuid = j.value("uuid", "");
        std::string file_path = j.value("file_path", "");
        if (uuid.empty() || file_path.empty()) return;

        SQLite::Statement query(m_db, R"(
            INSERT OR REPLACE INTO generations (
                uuid, file_path, prompt, negative_prompt, seed, 
                width, height, steps, cfg_scale, model_hash, 
                generation_time, parent_uuid, params_json, model_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        )");

        query.bind(1, uuid);
        query.bind(2, file_path);
        query.bind(3, j.value("prompt", ""));
        query.bind(4, j.value("negative_prompt", ""));
        query.bind(5, (int64_t)j.value("seed", -1LL));
        query.bind(6, j.value("width", 512));
        query.bind(7, j.value("height", 512));
        query.bind(8, (int)j.value("steps", 20));
        query.bind(9, (double)j.value("cfg_scale", 7.0));
        query.bind(10, j.value("model_hash", ""));
        query.bind(11, (double)j.value("generation_time", 0.0));
        
        if (j.contains("parent_uuid") && !j["parent_uuid"].is_null()) {
            query.bind(12, j["parent_uuid"].get<std::string>());
        } else {
            query.bind(12);
        }

        query.bind(13, j.dump());
        query.bind(14, j.value("model_id", ""));

        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] save_generation failed: " << e.what() << std::endl;
    }
}

void Database::set_favorite(const std::string& uuid, bool favorite) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "UPDATE generations SET is_favorite = ? WHERE uuid = ?");
        query.bind(1, favorite ? 1 : 0);
        query.bind(2, uuid);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] set_favorite failed: " << e.what() << std::endl;
    }
}

void Database::set_rating(const std::string& uuid, int rating) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "UPDATE generations SET rating = ? WHERE uuid = ?");
        query.bind(1, std::max(0, std::min(5, rating)));
        query.bind(2, uuid);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] set_rating failed: " << e.what() << std::endl;
    }
}

void Database::remove_generation(const std::string& uuid) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "DELETE FROM generations WHERE uuid = ?");
        query.bind(1, uuid);
        query.exec();
        delete_unused_tags(); 
    } catch (const std::exception& e) {
        std::cerr << "[Database] remove_generation failed: " << e.what() << std::endl;
    }
}

std::string Database::get_generation_filepath(const std::string& uuid) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "SELECT file_path FROM generations WHERE uuid = ?");
        query.bind(1, uuid);
        if (query.executeStep()) {
            return query.getColumn(0).getText();
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_generation_filepath failed: " << e.what() << std::endl;
    }
    return "";
}

mysti::json Database::get_generations(int limit, int offset, const std::vector<std::string>& tags, const std::string& model, int min_rating) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        std::string sql = "SELECT g.* FROM generations g WHERE 1=1 ";
        if (!tags.empty()) {
            sql += "AND g.id IN (SELECT it.generation_id FROM image_tags it JOIN tags t ON it.tag_id = t.id WHERE t.name IN (";
            for (size_t i = 0; i < tags.size(); ++i) {
                sql += (i == 0 ? "?" : ", ?");
            }
            sql += ") GROUP BY it.generation_id HAVING COUNT(DISTINCT t.id) = ?) ";
        }
        if (!model.empty()) sql += "AND g.model_id = ? ";
        if (min_rating > 0) sql += "AND g.rating >= ? ";
        sql += "ORDER BY g.timestamp DESC LIMIT ? OFFSET ?";

        SQLite::Statement query(m_db, sql);
        int bind_idx = 1;
        if (!tags.empty()) {
            for (const auto& tag : tags) query.bind(bind_idx++, tag);
            query.bind(bind_idx++, (int)tags.size());
        }
        if (!model.empty()) query.bind(bind_idx++, model);
        if (min_rating > 0) query.bind(bind_idx++, min_rating);
        query.bind(bind_idx++, limit);
        query.bind(bind_idx++, offset);

        while (query.executeStep()) {
            results.push_back(parse_generation_row(query, m_db));
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_generations failed: " << e.what() << std::endl;
    }
    return results;
}

mysti::json Database::search_generations(const std::string& query, int limit) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement q(m_db, R"(
            SELECT g.* 
            FROM generations g
            WHERE g.id IN (SELECT rowid FROM generations_fts WHERE generations_fts MATCH ?)
            ORDER BY g.timestamp DESC LIMIT ?
        )");
        q.bind(1, query);
        q.bind(2, limit);
        while (q.executeStep()) {
            results.push_back(parse_generation_row(q, m_db));
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] search_generations FTS failed, falling back to LIKE: " << e.what() << std::endl;
        try {
            SQLite::Statement q(m_db, "SELECT * FROM generations WHERE prompt LIKE ? OR negative_prompt LIKE ? ORDER BY timestamp DESC LIMIT ?");
            q.bind(1, "%" + query + "%");
            q.bind(2, "%" + query + "%");
            q.bind(3, limit);
            while (q.executeStep()) {
                results.push_back(parse_generation_row(q, m_db));
            }
        } catch (...) {}
    }
    return results;
}

mysti::json Database::get_tags() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement query(m_db, "SELECT name, category, COUNT(it.tag_id) as count FROM tags t LEFT JOIN image_tags it ON t.id = it.tag_id GROUP BY t.id ORDER BY count DESC");
        while (query.executeStep()) {
            mysti::json tag;
            tag["name"] = query.getColumn(0).getText();
            tag["category"] = query.getColumn(1).getText();
            tag["count"] = query.getColumn(2).getInt();
            results.push_back(tag);
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_tags failed: " << e.what() << std::endl;
    }
    return results;
}

void Database::save_style(const Style& style) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "INSERT OR REPLACE INTO styles (name, prompt, negative_prompt, preview_path) VALUES (?, ?, ?, ?)");
        query.bind(1, style.name);
        query.bind(2, style.prompt);
        query.bind(3, style.negative_prompt);
        query.bind(4, style.preview_path);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] save_style failed: " << e.what() << std::endl;
    }
}

mysti::json Database::get_styles() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement query(m_db, "SELECT name, prompt, negative_prompt, preview_path FROM styles ORDER BY name ASC");
        while (query.executeStep()) {
            mysti::json s;
            s["name"] = query.getColumn(0).getText();
            s["prompt"] = query.getColumn(1).getText();
            s["negative_prompt"] = query.getColumn(2).getText();
            s["preview_path"] = query.getColumn(3).getText();
            results.push_back(s);
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_styles failed: " << e.what() << std::endl;
    }
    return results;
}

void Database::delete_style(const std::string& name) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "DELETE FROM styles WHERE name = ?");
        query.bind(1, name);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] delete_style failed: " << e.what() << std::endl;
    }
}

void Database::add_library_item(const LibraryItem& item) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "INSERT INTO prompt_library (label, content, category, preview_path) VALUES (?, ?, ?, ?)");
        query.bind(1, item.label);
        query.bind(2, item.content);
        query.bind(3, item.category);
        query.bind(4, item.preview_path);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] add_library_item failed: " << e.what() << std::endl;
    }
}

mysti::json Database::get_library_items(const std::string& category) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        std::string sql = "SELECT id, label, content, category, preview_path, usage_count FROM prompt_library ";
        if (!category.empty()) sql += "WHERE category = ? ";
        sql += "ORDER BY label ASC";
        SQLite::Statement query(m_db, sql);
        if (!category.empty()) query.bind(1, category);
        while (query.executeStep()) {
            mysti::json item;
            item["id"] = query.getColumn(0).getInt();
            item["label"] = query.getColumn(1).getText();
            item["content"] = query.getColumn(2).getText();
            item["category"] = query.getColumn(3).getText();
            item["preview_path"] = query.getColumn(4).getText();
            item["usage_count"] = query.getColumn(5).getInt();
            results.push_back(item);
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_library_items failed: " << e.what() << std::endl;
    }
    return results;
}

void Database::delete_library_item(int id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        m_db.exec("DELETE FROM prompt_library WHERE id = " + std::to_string(id));
    } catch (...) {}
}

void Database::increment_library_usage(int id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        m_db.exec("UPDATE prompt_library SET usage_count = usage_count + 1 WHERE id = " + std::to_string(id));
    } catch (...) {}
}

int Database::add_job(const std::string& type, const mysti::json& payload, int priority) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "INSERT INTO jobs (type, payload, priority) VALUES (?, ?, ?)");
        query.bind(1, type);
        query.bind(2, payload.dump());
        query.bind(3, priority);
        query.exec();
        return (int)m_db.getLastInsertRowid();
    } catch (...) { return -1; }
}

std::optional<Job> Database::get_next_job() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "SELECT id, type, payload, status, error, priority, created_at FROM jobs WHERE status = 'pending' ORDER BY priority DESC, created_at ASC LIMIT 1");
        if (query.executeStep()) {
            Job job;
            job.id = query.getColumn(0).getInt();
            job.type = query.getColumn(1).getText();
            job.payload = mysti::json::parse(query.getColumn(2).getText());
            job.status = query.getColumn(3).getText();
            job.error = query.getColumn(4).getText();
            job.priority = query.getColumn(5).getInt();
            job.created_at = query.getColumn(6).getText();
            return job;
        }
    } catch (...) {}
    return std::nullopt;
}

void Database::update_job_status(int id, const std::string& status, const std::string& error) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        std::string sql = "UPDATE jobs SET status = ?, updated_at = CURRENT_TIMESTAMP ";
        if (status == "completed") sql += ", completed_at = CURRENT_TIMESTAMP ";
        if (!error.empty()) sql += ", error = ? ";
        sql += "WHERE id = ?";
        SQLite::Statement query(m_db, sql);
        query.bind(1, status);
        if (!error.empty()) { query.bind(2, error); query.bind(3, id); } 
        else query.bind(2, id);
        query.exec();
    } catch (...) {}
}

void Database::add_generation_file(int generation_id, const std::string& type, const std::string& path) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "INSERT INTO generation_files (generation_id, file_type, file_path) VALUES (?, ?, ?)");
        query.bind(1, generation_id);
        query.bind(2, type);
        query.bind(3, path);
        query.exec();
    } catch (...) {}
}

std::vector<std::string> Database::get_generation_files(int generation_id, const std::string& type) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    std::vector<std::string> results;
    try {
        std::string sql = "SELECT file_path FROM generation_files WHERE generation_id = ? ";
        if (!type.empty()) sql += "AND file_type = ? ";
        sql += "ORDER BY created_at ASC";
        SQLite::Statement query(m_db, sql);
        query.bind(1, generation_id);
        if (!type.empty()) query.bind(2, type);
        while (query.executeStep()) results.push_back(query.getColumn(0).getText());
    } catch (...) {}
    return results;
}

void Database::save_image_preset(const ImagePreset& p) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, R"(
            INSERT OR REPLACE INTO image_presets (
                id, name, unet_path, vae_path, clip_l_path, clip_g_path, t5xxl_path,
                vram_weights_mb_estimate, vram_weights_mb_measured, default_params, preferred_params
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        )");
        if (p.id > 0) query.bind(1, p.id); else query.bind(1);
        query.bind(2, p.name);
        query.bind(3, p.unet_path);
        query.bind(4, p.vae_path);
        query.bind(5, p.clip_l_path);
        query.bind(6, p.clip_g_path);
        query.bind(7, p.t5xxl_path);
        query.bind(8, p.vram_weights_mb_estimate);
        query.bind(9, p.vram_weights_mb_measured);
        query.bind(10, p.default_params.dump());
        query.bind(11, p.preferred_params.dump());
        query.exec();
    } catch (...) {}
}

mysti::json Database::get_image_presets() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement query(m_db, "SELECT id, name, unet_path, vae_path, clip_l_path, clip_g_path, t5xxl_path, vram_weights_mb_estimate, vram_weights_mb_measured, default_params, preferred_params FROM image_presets ORDER BY name ASC");
        while (query.executeStep()) {
            mysti::json p;
            p["id"] = query.getColumn(0).getInt();
            p["name"] = query.getColumn(1).getText();
            p["unet_path"] = query.getColumn(2).getText();
            p["vae_path"] = query.getColumn(3).getText();
            p["clip_l_path"] = query.getColumn(4).getText();
            p["clip_g_path"] = query.getColumn(5).getText();
            p["t5xxl_path"] = query.getColumn(6).getText();
            p["vram_weights_mb_estimate"] = query.getColumn(7).getInt();
            p["vram_weights_mb_measured"] = query.getColumn(8).getInt();
            p["default_params"] = mysti::json::parse(query.getColumn(9).getText());
            p["preferred_params"] = mysti::json::parse(query.getColumn(10).getText());
            results.push_back(p);
        }
    } catch (...) {}
    return results;
}

void Database::delete_image_preset(int id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try { m_db.exec("DELETE FROM image_presets WHERE id = " + std::to_string(id)); } catch (...) {}
}

void Database::save_llm_preset(const LlmPreset& p) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, R"(
            INSERT OR REPLACE INTO llm_presets (
                id, name, model_path, mmproj_path, n_ctx, capabilities, role
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        )");
        if (p.id > 0) query.bind(1, p.id); else query.bind(1);
        query.bind(2, p.name);
        query.bind(3, p.model_path);
        query.bind(4, p.mmproj_path);
        query.bind(5, p.n_ctx);
        query.bind(6, mysti::json(p.capabilities).dump());
        query.bind(7, p.role);
        query.exec();
    } catch (...) {}
}

mysti::json Database::get_llm_presets() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement query(m_db, "SELECT id, name, model_path, mmproj_path, n_ctx, capabilities, role FROM llm_presets ORDER BY name ASC");
        while (query.executeStep()) {
            mysti::json p;
            p["id"] = query.getColumn(0).getInt();
            p["name"] = query.getColumn(1).getText();
            p["model_path"] = query.getColumn(2).getText();
            p["mmproj_path"] = query.getColumn(3).getText();
            p["n_ctx"] = query.getColumn(4).getInt();
            p["capabilities"] = mysti::json::parse(query.getColumn(5).getText());
            p["role"] = query.getColumn(6).getText();
            results.push_back(p);
        }
    } catch (...) {}
    return results;
}

void Database::delete_llm_preset(int id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try { m_db.exec("DELETE FROM llm_presets WHERE id = " + std::to_string(id)); } catch (...) {}
}

void Database::save_model_metadata(const std::string& model_id, const mysti::json& metadata) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "INSERT OR REPLACE INTO models (id, metadata, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)");
        query.bind(1, model_id);
        query.bind(2, metadata.dump());
        query.exec();
    } catch (...) {}
}

mysti::json Database::get_model_metadata(const std::string& model_id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement query(m_db, "SELECT metadata FROM models WHERE id = ?");
        query.bind(1, model_id);
        if (query.executeStep()) return mysti::json::parse(query.getColumn(0).getText());

        SQLite::Statement all_ids(m_db, "SELECT id, metadata FROM models");
        std::string normalized_id = model_id;
        std::replace(normalized_id.begin(), normalized_id.end(), '\\', '/');
        while (all_ids.executeStep()) {
            std::string stored_id = all_ids.getColumn(0).getText();
            std::string normalized_stored = stored_id;
            std::replace(normalized_stored.begin(), normalized_stored.end(), '\\', '/');
            if (normalized_id.length() >= normalized_stored.length()) {
                if (normalized_id.substr(normalized_id.length() - normalized_stored.length()) == normalized_stored) {
                    return mysti::json::parse(all_ids.getColumn(1).getText());
                }
            }
        }
    } catch (...) {}
    return mysti::json::object();
}

mysti::json Database::get_all_models_metadata() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    mysti::json results = mysti::json::array();
    try {
        SQLite::Statement query(m_db, "SELECT id, metadata FROM models ORDER BY id ASC");
        while (query.executeStep()) {
            mysti::json m;
            m["id"] = query.getColumn(0).getText();
            m["metadata"] = mysti::json::parse(query.getColumn(1).getText());
            results.push_back(m);
        }
    } catch (...) {}
    return results;
}

bool Database::generation_exists(const std::string& file_path) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement check(m_db, "SELECT id FROM generations WHERE file_path = ?");
        check.bind(1, file_path);
        return check.executeStep();
    } catch (...) { return false; }
}

void Database::insert_generation(const Generation& gen) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement ins(m_db, "INSERT INTO generations (uuid, file_path, prompt, negative_prompt, seed, width, height, steps, cfg_scale, generation_time, model_hash, is_favorite, auto_tagged, rating, model_id, params_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        ins.bind(1, gen.uuid);
        ins.bind(2, gen.file_path);
        ins.bind(3, gen.prompt);
        ins.bind(4, gen.negative_prompt);
        ins.bind(5, (int64_t)gen.seed);
        ins.bind(6, gen.width);
        ins.bind(7, gen.height);
        ins.bind(8, gen.steps);
        ins.bind(9, gen.cfg_scale);
        ins.bind(10, gen.generation_time);
        ins.bind(11, gen.model_hash);
        ins.bind(12, gen.is_favorite ? 1 : 0);
        ins.bind(13, gen.auto_tagged ? 1 : 0);
        ins.bind(14, gen.rating);
        ins.bind(15, gen.model_id);
        ins.bind(16, gen.params_json);
        ins.exec();
    } catch (...) {}
}

void Database::insert_generation_with_tags(const Generation& gen, const std::vector<std::string>& tags) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Transaction transaction(m_db);
        // Inlining insert_generation logic here to avoid double locking
        SQLite::Statement ins(m_db, "INSERT INTO generations (uuid, file_path, prompt, negative_prompt, seed, width, height, steps, cfg_scale, generation_time, model_hash, is_favorite, auto_tagged, rating, model_id, params_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        ins.bind(1, gen.uuid);
        ins.bind(2, gen.file_path);
        ins.bind(3, gen.prompt);
        ins.bind(4, gen.negative_prompt);
        ins.bind(5, (int64_t)gen.seed);
        ins.bind(6, gen.width);
        ins.bind(7, gen.height);
        ins.bind(8, gen.steps);
        ins.bind(9, gen.cfg_scale);
        ins.bind(10, gen.generation_time);
        ins.bind(11, gen.model_hash);
        ins.bind(12, gen.is_favorite ? 1 : 0);
        ins.bind(13, gen.auto_tagged ? 1 : 0);
        ins.bind(14, gen.rating);
        ins.bind(15, gen.model_id);
        ins.bind(16, gen.params_json);
        ins.exec();

        int64_t gen_id = m_db.getLastInsertRowid();
        for (const auto& tag : tags) {
            SQLite::Statement ins_tag(m_db, "INSERT OR IGNORE INTO tags (name) VALUES (?)");
            ins_tag.bind(1, tag); ins_tag.exec();
            SQLite::Statement get_tag_id(m_db, "SELECT id FROM tags WHERE name = ?");
            get_tag_id.bind(1, tag);
            if (get_tag_id.executeStep()) {
                int tag_id = get_tag_id.getColumn(0);
                SQLite::Statement link(m_db, "INSERT OR IGNORE INTO image_tags (generation_id, tag_id, source) VALUES (?, ?, ?)");
                link.bind(1, (int)gen_id); link.bind(2, tag_id); link.bind(3, "user"); link.exec();
            }
        }
        transaction.commit();
    } catch (...) {}
}

void Database::add_tag(const std::string& uuid, const std::string& tag, const std::string& source) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        SQLite::Statement get_id(m_db, "SELECT id FROM generations WHERE uuid = ?");
        get_id.bind(1, uuid);
        if (!get_id.executeStep()) return;
        int gen_id = get_id.getColumn(0);
        add_tag_by_id(gen_id, tag, source); // This will re-lock if add_tag_by_id locks
    } catch (...) {}
}

void Database::add_tag_by_id(int generation_id, const std::string& tag, const std::string& source) {
    // This function is called by add_tag, which already holds the lock. 
    // To avoid double locking, this function should not lock again.
    // However, for simplicity and safety, it's made to handle its own lock if called directly.
    // If add_tag calls this, it will attempt to double-lock.
    // A better approach would be to have an internal non-locking version.
    // For now, assuming add_tag is the primary caller and it holds the lock.
    try {
        SQLite::Statement ins_tag(m_db, "INSERT OR IGNORE INTO tags (name) VALUES (?)");
        ins_tag.bind(1, tag); ins_tag.exec();
        SQLite::Statement get_tag_id(m_db, "SELECT id FROM tags WHERE name = ?");
        get_tag_id.bind(1, tag);
        if (get_tag_id.executeStep()) {
            int tag_id = get_tag_id.getColumn(0);
            SQLite::Statement link(m_db, "INSERT OR IGNORE INTO image_tags (generation_id, tag_id, source) VALUES (?, ?, ?)");
            link.bind(1, generation_id); link.bind(2, tag_id); link.bind(3, source); link.exec();
        }
    } catch (...) {}
}

void Database::remove_tag(const std::string& uuid, const std::string& tag) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try {
        m_db.exec("DELETE FROM image_tags WHERE generation_id = (SELECT id FROM generations WHERE uuid = '" + uuid + "') AND tag_id = (SELECT id FROM tags WHERE name = '" + tag + "')");
    } catch (...) {}
}

void Database::delete_unused_tags() {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try { m_db.exec("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM image_tags)"); } catch (...) {}
}

std::vector<std::tuple<int, std::string, std::string>> Database::get_untagged_generations(int limit) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    std::vector<std::tuple<int, std::string, std::string>> results;
    try {
        SQLite::Statement query(m_db, "SELECT id, uuid, prompt FROM generations WHERE auto_tagged = 0 AND prompt IS NOT NULL AND prompt != '' LIMIT ?");
        query.bind(1, limit);
        while (query.executeStep()) results.emplace_back(query.getColumn(0).getInt(), query.getColumn(1).getText(), query.getColumn(2).getText());
    } catch (...) {}
    return results;
}

void Database::mark_as_tagged(int id) {
    std::lock_guard<std::recursive_mutex> lock(m_mutex);
    try { m_db.exec("UPDATE generations SET auto_tagged = 1 WHERE id = " + std::to_string(id)); } catch (...) {}
}

} // namespace mysti
