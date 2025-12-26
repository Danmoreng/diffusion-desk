#include "database.hpp"
#include <iostream>

namespace mysti {

Database::Database(const std::string& db_path) 
    : m_db(db_path, SQLite::OPEN_READWRITE | SQLite::OPEN_CREATE) 
{
    // Enable Write-Ahead Logging (WAL) for concurrency
    try {
        m_db.exec("PRAGMA journal_mode=WAL;");
        m_db.exec("PRAGMA synchronous=NORMAL;"); // Good balance for WAL
        m_db.exec("PRAGMA foreign_keys=ON;");    // Enforce foreign keys
    } catch (const std::exception& e) {
        std::cerr << "[Database] Error setting pragmas: " << e.what() << std::endl;
    }
}

Database::~Database() {
    // SQLiteCpp closes the DB automatically
}

void Database::init_schema() {
    try {
        SQLite::Transaction transaction(m_db);

        // Table: generations
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
                FOREIGN KEY(parent_uuid) REFERENCES generations(uuid)
            );
        )");

        // Check if columns exist (migrations)
        try { m_db.exec("ALTER TABLE generations ADD COLUMN generation_time REAL;"); } catch (...) {}
        try { m_db.exec("ALTER TABLE generations ADD COLUMN auto_tagged BOOLEAN DEFAULT 0;"); } catch (...) {}

        // Table: tags
        m_db.exec(R"(
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                category TEXT DEFAULT 'general'
            );
        )");

        // Table: image_tags
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

        // Table: prompt_templates
        m_db.exec(R"(
            CREATE TABLE IF NOT EXISTS prompt_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                content TEXT NOT NULL,
                description TEXT
            );
        )");

        // Create indexes for performance
        m_db.exec("CREATE INDEX IF NOT EXISTS idx_generations_timestamp ON generations(timestamp DESC);");
        m_db.exec("CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);");

        transaction.commit();
        std::cout << "[Database] Schema initialized successfully." << std::endl;

    } catch (const std::exception& e) {
        std::cerr << "[Database] Schema initialization failed: " << e.what() << std::endl;
        throw; // Re-throw to stop startup if DB is broken
    }
}

mysti::json Database::get_generations(int limit, int offset, const std::string& tag) {
    mysti::json results = mysti::json::array();
    try {
        std::string sql = "SELECT g.* FROM generations g ";
        if (!tag.empty()) {
            sql += "JOIN image_tags it ON g.id = it.generation_id ";
            sql += "JOIN tags t ON it.tag_id = t.id ";
            sql += "WHERE t.name = ? ";
        }
        sql += "ORDER BY g.timestamp DESC LIMIT ? OFFSET ?";

        SQLite::Statement query(m_db, sql);
        int bind_idx = 1;
        if (!tag.empty()) {
            query.bind(bind_idx++, tag);
        }
        query.bind(bind_idx++, limit);
        query.bind(bind_idx++, offset);

        while (query.executeStep()) {
            mysti::json gen;
            std::string file_path = query.getColumn("file_path").getText();
            
            // Extract filename for the WebUI 'name' property
            std::string name = file_path;
            size_t last_slash = file_path.find_last_of("/\\");
            if (last_slash != std::string::npos) {
                name = file_path.substr(last_slash + 1);
            }

            gen["id"] = query.getColumn("uuid").getText();
            gen["name"] = name; 
            gen["file_path"] = file_path;
            gen["timestamp"] = query.getColumn("timestamp").getText();
            
            mysti::json params;
            params["prompt"] = query.getColumn("prompt").getText();
            params["negative_prompt"] = query.getColumn("negative_prompt").getText();
            params["seed"] = (long long)query.getColumn("seed").getInt64();
            params["width"] = query.getColumn("width").getInt();
            params["height"] = query.getColumn("height").getInt();
            params["steps"] = query.getColumn("steps").getInt();
            params["sample_steps"] = query.getColumn("steps").getInt(); // Alias for compat
            params["cfg_scale"] = query.getColumn("cfg_scale").getDouble();
            params["model"] = query.getColumn("model_hash").getText(); // Alias for compat
            params["model_hash"] = query.getColumn("model_hash").getText();
            
            double gen_time = query.getColumn("generation_time").getDouble();
            if (gen_time > 0) {
                params["Time"] = std::to_string(gen_time).substr(0, std::to_string(gen_time).find(".") + 3) + "s";
            }
            
            gen["params"] = params;
            gen["is_favorite"] = query.getColumn("is_favorite").getInt() != 0;
            
            // Fetch tags for this generation
            mysti::json tags = mysti::json::array();
            SQLite::Statement tag_query(m_db, "SELECT t.name, it.source FROM tags t JOIN image_tags it ON t.id = it.tag_id WHERE it.generation_id = ?");
            tag_query.bind(1, query.getColumn("id").getInt());
            while (tag_query.executeStep()) {
                tags.push_back(tag_query.getColumn(0).getText()); // Simplify to string array for now
            }
            gen["tags"] = tags;

            results.push_back(gen);
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_generations failed: " << e.what() << std::endl;
    }
    return results;
}

mysti::json Database::get_tags() {
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

void Database::add_tag(const std::string& uuid, const std::string& tag, const std::string& source) {
    try {
        SQLite::Transaction transaction(m_db);
        
        // Get generation ID
        SQLite::Statement get_id(m_db, "SELECT id FROM generations WHERE uuid = ?");
        get_id.bind(1, uuid);
        if (!get_id.executeStep()) return;
        int gen_id = get_id.getColumn(0);

        // Insert Tag
        SQLite::Statement ins_tag(m_db, "INSERT OR IGNORE INTO tags (name) VALUES (?)");
        ins_tag.bind(1, tag);
        ins_tag.exec();

        // Get Tag ID
        SQLite::Statement get_tag_id(m_db, "SELECT id FROM tags WHERE name = ?");
        get_tag_id.bind(1, tag);
        if (get_tag_id.executeStep()) {
            int tag_id = get_tag_id.getColumn(0);
            SQLite::Statement link(m_db, "INSERT OR IGNORE INTO image_tags (generation_id, tag_id, source) VALUES (?, ?, ?)");
            link.bind(1, gen_id);
            link.bind(2, tag_id);
            link.bind(3, source);
            link.exec();
        }
        transaction.commit();
    } catch (const std::exception& e) {
        std::cerr << "[Database] add_tag failed: " << e.what() << std::endl;
    }
}

void Database::remove_tag(const std::string& uuid, const std::string& tag) {
    try {
        SQLite::Statement query(m_db, "DELETE FROM image_tags WHERE generation_id = (SELECT id FROM generations WHERE uuid = ?) AND tag_id = (SELECT id FROM tags WHERE name = ?)");
        query.bind(1, uuid);
        query.bind(2, tag);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] remove_tag failed: " << e.what() << std::endl;
    }
}

std::vector<std::pair<int, std::string>> Database::get_untagged_generations(int limit) {
    std::vector<std::pair<int, std::string>> results;
    try {
        // Find generations that haven't been auto-tagged yet AND have a prompt
        SQLite::Statement query(m_db, "SELECT id, prompt FROM generations WHERE auto_tagged = 0 AND prompt IS NOT NULL AND prompt != '' LIMIT ?");
        query.bind(1, limit);
        while (query.executeStep()) {
            results.emplace_back(query.getColumn(0).getInt(), query.getColumn(1).getText());
        }
    } catch (const std::exception& e) {
        std::cerr << "[Database] get_untagged_generations failed: " << e.what() << std::endl;
    }
    return results;
}

void Database::mark_as_tagged(int id) {
    try {
        SQLite::Statement query(m_db, "UPDATE generations SET auto_tagged = 1 WHERE id = ?");
        query.bind(1, id);
        query.exec();
    } catch (const std::exception& e) {
        std::cerr << "[Database] mark_as_tagged failed: " << e.what() << std::endl;
    }
}

} // namespace mysti
