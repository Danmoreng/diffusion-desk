package com.diffusiondesk.desktop.core

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class GalleryDatabase(
    dbFile: File = File(AppPaths.appDir, "gallery.db"),
) {
    private val jdbcUrl: String

    init {
        dbFile.parentFile?.mkdirs()
        jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        connection().use { conn ->
            conn.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("PRAGMA foreign_keys=ON")
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS images (
                        id INTEGER PRIMARY KEY,
                        file_path TEXT NOT NULL UNIQUE,
                        preview_path TEXT NOT NULL DEFAULT '',
                        prompt TEXT NOT NULL DEFAULT '',
                        negative_prompt TEXT NOT NULL DEFAULT '',
                        seed INTEGER,
                        width INTEGER,
                        height INTEGER,
                        steps INTEGER,
                        cfg_scale REAL,
                        sampler TEXT NOT NULL DEFAULT '',
                        model_id TEXT NOT NULL DEFAULT '',
                        preset_id TEXT NOT NULL DEFAULT '',
                        generation_time REAL,
                        created_at INTEGER NOT NULL,
                        modified_at INTEGER NOT NULL,
                        metadata_text TEXT NOT NULL DEFAULT '',
                        favorite INTEGER NOT NULL DEFAULT 0,
                        rating INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                ensureColumn(
                    conn = conn,
                    tableName = "images",
                    columnName = "preview_path",
                    definition = "TEXT NOT NULL DEFAULT ''",
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS keywords (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS image_keywords (
                        image_id INTEGER NOT NULL,
                        keyword_id INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'manual',
                        PRIMARY KEY(image_id, keyword_id),
                        FOREIGN KEY(image_id) REFERENCES images(id) ON DELETE CASCADE,
                        FOREIGN KEY(keyword_id) REFERENCES keywords(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE INDEX IF NOT EXISTS idx_images_created_at ON images(created_at DESC)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_images_prompt ON images(prompt)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_keywords_name ON keywords(name)")
            }
        }
    }

    fun connection(): Connection = DriverManager.getConnection(jdbcUrl).apply {
        createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
    }

    private fun ensureColumn(
        conn: Connection,
        tableName: String,
        columnName: String,
        definition: String,
    ) {
        val exists = conn.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                var found = false
                while (rs.next()) {
                    if (rs.getString("name") == columnName) {
                        found = true
                        break
                    }
                }
                found
            }
        }
        if (!exists) {
            conn.createStatement().use { statement ->
                statement.execute("ALTER TABLE $tableName ADD COLUMN $columnName $definition")
            }
        }
    }
}
