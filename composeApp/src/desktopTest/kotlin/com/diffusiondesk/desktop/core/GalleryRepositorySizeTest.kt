package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryRepositorySizeTest {
    @Test
    fun returnsMoreThanFiveHundredImages() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-size-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)

        db.connection().use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(
                "INSERT INTO images(file_path, preview_path, created_at, modified_at) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                repeat(501) { index ->
                    val path = tempDir.resolve("image-$index.png").absolutePath
                    statement.setString(1, path)
                    statement.setString(2, path)
                    statement.setLong(3, index.toLong())
                    statement.setLong(4, index.toLong())
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            conn.commit()
        }

        assertEquals(501, repository.listImages().size)
    }
}
