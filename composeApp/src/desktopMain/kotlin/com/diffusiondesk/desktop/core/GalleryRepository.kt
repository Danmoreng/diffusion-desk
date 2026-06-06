package com.diffusiondesk.desktop.core

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.DriverManager
import javax.imageio.ImageIO
import java.util.Locale

class GalleryRepository(
    private val database: GalleryDatabase,
) {
    private companion object {
        const val PREVIEW_MAX_DIMENSION = 320
        const val PREVIEW_QUALITY_HINT = "jpg"
    }

    fun indexOutputDirectory(outputDir: String): Int {
        val root = File(outputDir).absoluteFile
        if (!root.isDirectory) return 0
        val legacyPreviews = loadLegacyPreviewMappings(root)
        var indexed = 0
        database.connection().use { conn ->
            conn.autoCommit = false
            try {
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name.lowercase(Locale.US)
                        name != "temp" && name != "previews"
                    }
                    .filter(GalleryMetadataParser::isSupportedImage)
                    .forEach { file ->
                        val sourceFile = file.absoluteFile
                        val metadata = GalleryMetadataParser.parse(sourceFile)
                        val previewFile = resolvePreviewFile(root, sourceFile, legacyPreviews)
                        upsertImage(conn, sourceFile, previewFile, metadata)
                        indexed++
                    }
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            } finally {
                conn.autoCommit = true
            }
        }
        return indexed
    }

    fun listImages(query: String = "", keyword: String = ""): List<GalleryImage> {
        database.connection().use { conn ->
            val where = mutableListOf<String>()
            val args = mutableListOf<String>()
            val normalizedQuery = query.trim()
            if (normalizedQuery.isNotBlank()) {
                where += "(i.prompt LIKE ? OR i.negative_prompt LIKE ? OR i.model_id LIKE ? OR i.file_path LIKE ?)"
                repeat(4) { args += "%$normalizedQuery%" }
            }
            val normalizedKeyword = keyword.trim()
            if (normalizedKeyword.isNotBlank()) {
                where += "EXISTS (SELECT 1 FROM image_keywords ik JOIN keywords k ON k.id = ik.keyword_id WHERE ik.image_id = i.id AND k.name = ?)"
                args += normalizedKeyword
            }
            val sql = buildString {
                append(
                    """
                    SELECT i.id, i.file_path, i.preview_path, i.prompt, i.negative_prompt, i.seed, i.width, i.height,
                           i.steps, i.cfg_scale, i.sampler, i.model_id, i.preset_id, i.generation_time,
                           i.created_at, i.modified_at, i.metadata_text, i.favorite, i.rating,
                           GROUP_CONCAT(k.name, char(31)) AS keywords
                    FROM images i
                    LEFT JOIN image_keywords ik ON ik.image_id = i.id
                    LEFT JOIN keywords k ON k.id = ik.keyword_id
                    """.trimIndent(),
                )
                if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
                append(" GROUP BY i.id ORDER BY i.created_at DESC, i.id DESC LIMIT 500")
            }

            conn.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, arg -> statement.setString(index + 1, arg) }
                statement.executeQuery().use { rs ->
                    val rows = mutableListOf<GalleryImage>()
                    while (rs.next()) {
                        rows += rs.toGalleryImage()
                    }
                    return rows
                }
            }
        }
    }

    fun listKeywords(): List<String> {
        database.connection().use { conn ->
            conn.prepareStatement(
                """
                SELECT k.name
                FROM keywords k
                JOIN image_keywords ik ON ik.keyword_id = k.id
                GROUP BY k.id
                ORDER BY lower(k.name)
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    val keywords = mutableListOf<String>()
                    while (rs.next()) keywords += rs.getString(1)
                    return keywords
                }
            }
        }
    }

    fun addKeyword(imageId: Long, keyword: String) {
        addKeyword(imageId, keyword, "manual")
    }

    fun addKeyword(imageId: Long, keyword: String, source: String) {
        val normalized = keyword.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return
        val normalizedSource = source.trim().ifBlank { "manual" }
        database.connection().use { conn ->
            conn.autoCommit = false
            try {
                addKeyword(conn, imageId, normalized, normalizedSource)
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun claimNextPendingLlmTag(presetId: String): GalleryImage? {
        database.connection().use { conn ->
            conn.autoCommit = false
            try {
                enqueuePendingLlmTagRows(conn)
                val imageId = conn.prepareStatement(
                    """
                    SELECT q.image_id
                    FROM llm_tagging_queue q
                    JOIN images i ON i.id = q.image_id
                    WHERE q.status = 'pending'
                    ORDER BY i.created_at DESC, i.id DESC
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
                }

                if (imageId == null) {
                    conn.commit()
                    return null
                }

                conn.prepareStatement(
                    """
                    UPDATE llm_tagging_queue
                    SET status = 'running', preset_id = ?, error = '', updated_at = ?
                    WHERE image_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, presetId)
                    statement.setLong(2, System.currentTimeMillis())
                    statement.setLong(3, imageId)
                    statement.executeUpdate()
                }

                val image = selectImageById(conn, imageId)
                conn.commit()
                return image
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun completeLlmTagging(imageId: Long, presetId: String, tags: List<String>) {
        database.connection().use { conn ->
            conn.autoCommit = false
            try {
                tags.forEach { tag ->
                    val normalized = tag.trim().lowercase(Locale.US)
                    if (normalized.isNotBlank()) {
                        addKeyword(conn, imageId, normalized, "llm")
                    }
                }
                updateLlmTaggingStatus(conn, imageId, "completed", presetId, "")
                conn.commit()
            } catch (error: Throwable) {
                conn.rollback()
                throw error
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun failLlmTagging(imageId: Long, presetId: String, message: String) {
        database.connection().use { conn ->
            updateLlmTaggingStatus(conn, imageId, "failed", presetId, message.take(500))
        }
    }

    fun listImagesPendingLlmTags(limit: Int = 1): List<GalleryImage> {
        database.connection().use { conn ->
            conn.prepareStatement(
                """
                SELECT i.id, i.file_path, i.preview_path, i.prompt, i.negative_prompt, i.seed, i.width, i.height,
                       i.steps, i.cfg_scale, i.sampler, i.model_id, i.preset_id, i.generation_time,
                       i.created_at, i.modified_at, i.metadata_text, i.favorite, i.rating,
                       GROUP_CONCAT(k.name, char(31)) AS keywords
                FROM images i
                LEFT JOIN image_keywords ik ON ik.image_id = i.id
                LEFT JOIN keywords k ON k.id = ik.keyword_id
                WHERE NOT EXISTS (
                    SELECT 1 FROM image_keywords tik
                    WHERE tik.image_id = i.id AND tik.source = 'llm'
                )
                GROUP BY i.id
                ORDER BY i.created_at DESC, i.id DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit.coerceAtLeast(1))
                statement.executeQuery().use { rs ->
                    val rows = mutableListOf<GalleryImage>()
                    while (rs.next()) {
                        rows += rs.toGalleryImage()
                    }
                    return rows
                }
            }
        }
    }

    fun removeKeyword(imageId: Long, keyword: String) {
        database.connection().use { conn ->
            conn.prepareStatement(
                """
                DELETE FROM image_keywords
                WHERE image_id = ? AND keyword_id = (SELECT id FROM keywords WHERE name = ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, imageId)
                statement.setString(2, keyword.trim().lowercase(Locale.US))
                statement.executeUpdate()
            }
        }
    }

    fun reusableParams(image: GalleryImage): GalleryReusableParams {
        return GalleryReusableParams(
            prompt = image.prompt,
            negativePrompt = image.negativePrompt,
            width = image.width,
            height = image.height,
            steps = image.steps,
            cfgScale = image.cfgScale,
            sampler = image.sampler,
            seed = image.seed,
            modelId = image.modelId,
            presetId = image.presetId,
        )
    }

    private fun upsertImage(conn: Connection, file: File, previewFile: File, metadata: ParsedImageMetadata) {
            conn.prepareStatement(
                """
                INSERT INTO images(
                    file_path, preview_path, prompt, negative_prompt, seed, width, height, steps, cfg_scale,
                    sampler, model_id, preset_id, generation_time, created_at, modified_at, metadata_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(file_path) DO UPDATE SET
                    preview_path = excluded.preview_path,
                    prompt = excluded.prompt,
                    negative_prompt = excluded.negative_prompt,
                    seed = excluded.seed,
                    width = excluded.width,
                    height = excluded.height,
                    steps = excluded.steps,
                    cfg_scale = excluded.cfg_scale,
                    sampler = excluded.sampler,
                    model_id = excluded.model_id,
                    preset_id = excluded.preset_id,
                    generation_time = excluded.generation_time,
                    modified_at = excluded.modified_at,
                    metadata_text = excluded.metadata_text
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, file.absolutePath)
                statement.setString(2, previewFile.absolutePath)
                statement.setString(3, metadata.prompt)
                statement.setString(4, metadata.negativePrompt)
                statement.setNullableLong(5, metadata.seed)
                statement.setNullableInt(6, metadata.width)
                statement.setNullableInt(7, metadata.height)
                statement.setNullableInt(8, metadata.steps)
                statement.setNullableDouble(9, metadata.cfgScale)
                statement.setString(10, metadata.sampler)
                statement.setString(11, metadata.modelId)
                statement.setString(12, "")
                statement.setNullableDouble(13, metadata.generationTime)
                statement.setLong(14, file.lastModified())
                statement.setLong(15, file.lastModified())
                statement.setString(16, metadata.metadataText)
                statement.executeUpdate()
            }
    }

    private fun resolvePreviewFile(root: File, sourceFile: File, legacyPreviews: Map<String, File>): File {
        val canonicalSource = sourceFile.canonicalFile
        val legacyPreview = legacyPreviews[canonicalSource.absolutePath]
        if (legacyPreview != null && legacyPreview.isFile) {
            return legacyPreview
        }

        val previewDir = File(root, "previews")
        val previewFile = File(previewDir, "dd_${sha256(canonicalSource.absolutePath)}.$PREVIEW_QUALITY_HINT")
        if (previewFile.isFile && previewFile.lastModified() >= canonicalSource.lastModified()) {
            return previewFile
        }

        runCatching {
            previewDir.mkdirs()
            writePreview(canonicalSource, previewFile)
        }
        return previewFile.takeIf { it.isFile } ?: canonicalSource
    }

    private fun writePreview(sourceFile: File, targetFile: File) {
        val source = ImageIO.read(sourceFile) ?: return
        val scale = minOf(
            PREVIEW_MAX_DIMENSION.toDouble() / source.width.coerceAtLeast(1),
            PREVIEW_MAX_DIMENSION.toDouble() / source.height.coerceAtLeast(1),
            1.0,
        )
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(target, PREVIEW_QUALITY_HINT, targetFile)
    }

    private fun loadLegacyPreviewMappings(outputRoot: File): Map<String, File> {
        val dbFiles = listOfNotNull(
            File("diffusion_desk.db"),
            outputRoot.parentFile?.let { File(it, "diffusion_desk.db") },
        )
            .filter { it.isFile }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        if (dbFiles.isEmpty()) return emptyMap()
        val mappings = mutableMapOf<String, File>()
        dbFiles.forEach { dbFile ->
            runCatching {
                DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                    conn.prepareStatement(
                        """
                        SELECT g.file_path AS image_path, gf.file_path AS preview_path
                        FROM generations g
                        JOIN generation_files gf ON gf.generation_id = g.id
                        WHERE gf.file_type = 'thumbnail'
                        """.trimIndent(),
                    ).use { statement ->
                        statement.executeQuery().use { rs ->
                            while (rs.next()) {
                                val imageFile = resolveLegacyOutputPath(outputRoot, rs.getString("image_path").orEmpty())
                                val previewFile = resolveLegacyOutputPath(outputRoot, rs.getString("preview_path").orEmpty())
                                if (imageFile != null && previewFile != null && previewFile.isFile) {
                                    mappings[imageFile.canonicalPath] = previewFile.canonicalFile
                                }
                            }
                        }
                    }
                }
            }
        }
        return mappings
    }

    private fun resolveLegacyOutputPath(outputRoot: File, path: String): File? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return null
        val rawFile = File(trimmed)
        if (rawFile.isAbsolute) return rawFile.takeIf { it.exists() }?.canonicalFile

        val clean = trimmed.trimStart('/', '\\').replace('/', File.separatorChar).replace('\\', File.separatorChar)
        val relativeToParent = outputRoot.parentFile?.let { File(it, clean) }
        if (relativeToParent != null && relativeToParent.exists()) return relativeToParent.canonicalFile

        val outputPrefix = outputRoot.name + File.separator
        val relativeToOutput = if (clean.startsWith(outputPrefix, ignoreCase = true)) {
            File(outputRoot, clean.removePrefix(outputPrefix))
        } else {
            File(outputRoot, clean)
        }
        return relativeToOutput.takeIf { it.exists() }?.canonicalFile
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private fun enqueuePendingLlmTagRows(conn: Connection) {
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            """
            INSERT OR IGNORE INTO llm_tagging_queue(image_id, status, created_at, updated_at)
            SELECT i.id, 'pending', ?, ?
            FROM images i
            WHERE NOT EXISTS (
                SELECT 1 FROM image_keywords tik
                WHERE tik.image_id = i.id AND tik.source = 'llm'
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, now)
            statement.setLong(2, now)
            statement.executeUpdate()
        }
    }

    private fun updateLlmTaggingStatus(
        conn: Connection,
        imageId: Long,
        status: String,
        presetId: String,
        error: String,
    ) {
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            """
            INSERT INTO llm_tagging_queue(image_id, status, preset_id, error, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(image_id) DO UPDATE SET
                status = excluded.status,
                preset_id = excluded.preset_id,
                error = excluded.error,
                updated_at = excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, imageId)
            statement.setString(2, status)
            statement.setString(3, presetId)
            statement.setString(4, error)
            statement.setLong(5, now)
            statement.setLong(6, now)
            statement.executeUpdate()
        }
    }

    private fun selectImageById(conn: Connection, imageId: Long): GalleryImage? {
        conn.prepareStatement(
            """
            SELECT i.id, i.file_path, i.preview_path, i.prompt, i.negative_prompt, i.seed, i.width, i.height,
                   i.steps, i.cfg_scale, i.sampler, i.model_id, i.preset_id, i.generation_time,
                   i.created_at, i.modified_at, i.metadata_text, i.favorite, i.rating,
                   GROUP_CONCAT(k.name, char(31)) AS keywords
            FROM images i
            LEFT JOIN image_keywords ik ON ik.image_id = i.id
            LEFT JOIN keywords k ON k.id = ik.keyword_id
            WHERE i.id = ?
            GROUP BY i.id
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, imageId)
            statement.executeQuery().use { rs ->
                return if (rs.next()) rs.toGalleryImage() else null
            }
        }
    }

    private fun addKeyword(conn: Connection, imageId: Long, keyword: String, source: String) {
        val keywordId = findOrCreateKeyword(conn, keyword)
        conn.prepareStatement(
            "INSERT OR IGNORE INTO image_keywords(image_id, keyword_id, source) VALUES (?, ?, ?)",
        ).use { statement ->
            statement.setLong(1, imageId)
            statement.setLong(2, keywordId)
            statement.setString(3, source)
            statement.executeUpdate()
        }
    }

    private fun findOrCreateKeyword(conn: Connection, keyword: String): Long {
        conn.prepareStatement("INSERT OR IGNORE INTO keywords(name) VALUES (?)").use { statement ->
            statement.setString(1, keyword)
            statement.executeUpdate()
        }
        conn.prepareStatement("SELECT id FROM keywords WHERE name = ?").use { statement ->
            statement.setString(1, keyword)
            statement.executeQuery().use { rs ->
                if (rs.next()) return rs.getLong(1)
            }
        }
        error("Failed to create tag.")
    }

    private fun ResultSet.toGalleryImage(): GalleryImage {
        return GalleryImage(
            id = getLong("id"),
            filePath = getString("file_path").orEmpty(),
            previewPath = getString("preview_path").orEmpty(),
            prompt = getString("prompt").orEmpty(),
            negativePrompt = getString("negative_prompt").orEmpty(),
            seed = nullableLong("seed"),
            width = nullableInt("width"),
            height = nullableInt("height"),
            steps = nullableInt("steps"),
            cfgScale = nullableDouble("cfg_scale"),
            sampler = getString("sampler").orEmpty(),
            modelId = getString("model_id").orEmpty(),
            presetId = getString("preset_id").orEmpty(),
            generationTime = nullableDouble("generation_time"),
            createdAt = getLong("created_at"),
            modifiedAt = getLong("modified_at"),
            metadataText = getString("metadata_text").orEmpty(),
            favorite = getInt("favorite") != 0,
            rating = getInt("rating"),
            keywords = getString("keywords")
                ?.split(31.toChar())
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.INTEGER) else setLong(index, value)
    }

    private fun java.sql.PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value == null) setNull(index, java.sql.Types.INTEGER) else setInt(index, value)
    }

    private fun java.sql.PreparedStatement.setNullableDouble(index: Int, value: Double?) {
        if (value == null) setNull(index, java.sql.Types.REAL) else setDouble(index, value)
    }

    private fun ResultSet.nullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.nullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.nullableDouble(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }
}
