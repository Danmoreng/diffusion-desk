package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GalleryRepositoryTaggingQueueTest {
    @Test
    fun completedQueueStateDoesNotDependOnNewLlmKeywordRows() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val imagePath = tempDir.resolve("image.png").absolutePath

        db.connection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO images(file_path, preview_path, created_at, modified_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, imagePath)
                statement.setString(2, imagePath)
                statement.setLong(3, 10)
                statement.setLong(4, 10)
                statement.executeUpdate()
            }
        }

        val firstClaim = repository.claimNextPendingLlmTag("tagger")
        assertNotNull(firstClaim)
        repository.addKeyword(firstClaim.id, "portrait", "manual")
        repository.completeLlmTagging(firstClaim.id, "tagger", listOf("portrait"))

        val secondClaim = repository.claimNextPendingLlmTag("tagger")
        assertNull(secondClaim)

        db.connection().use { conn ->
            conn.prepareStatement("SELECT status FROM llm_tagging_queue WHERE image_id = ?").use { statement ->
                statement.setLong(1, firstClaim.id)
                statement.executeQuery().use { rs ->
                    assertEquals(true, rs.next())
                    assertEquals("completed", rs.getString(1))
                }
            }
        }
    }
}
