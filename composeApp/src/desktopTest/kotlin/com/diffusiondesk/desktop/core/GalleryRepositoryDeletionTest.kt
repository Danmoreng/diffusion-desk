package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalleryRepositoryDeletionTest {
    @Test
    fun refreshRemovesDatabaseRowsForMissingImages() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-delete-test").toFile()
        val outputDir = tempDir.resolve("output").apply { mkdirs() }
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        insertImage(db, outputDir.resolve("missing.png").absolutePath)

        repository.indexOutputDirectory(outputDir.absolutePath)

        assertTrue(repository.listImages().isEmpty())
    }

    @Test
    fun deleteImageRemovesImageSidecarPreviewAndDatabaseRow() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-delete-test").toFile()
        val imageFile = tempDir.resolve("image.png").apply { writeBytes(byteArrayOf(1)) }
        val sidecarFile = tempDir.resolve("image.txt").apply { writeText("prompt") }
        val previewFile = tempDir.resolve("preview.jpg").apply { writeBytes(byteArrayOf(2)) }
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val imageId = insertImage(db, imageFile.absolutePath, previewFile.absolutePath)
        val image = repository.listImages().single()

        repository.deleteImage(image)

        assertEquals(imageId, image.id)
        assertFalse(imageFile.exists())
        assertFalse(sidecarFile.exists())
        assertFalse(previewFile.exists())
        assertTrue(repository.listImages().isEmpty())
    }

    private fun insertImage(database: GalleryDatabase, filePath: String, previewPath: String = filePath): Long {
        database.connection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO images(file_path, preview_path, created_at, modified_at) VALUES (?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, filePath)
                statement.setString(2, previewPath)
                statement.setLong(3, 10)
                statement.setLong(4, 10)
                statement.executeUpdate()
            }
            conn.prepareStatement("SELECT id FROM images WHERE file_path = ?").use { statement ->
                statement.setString(1, filePath)
                statement.executeQuery().use { result ->
                    check(result.next())
                    return result.getLong(1)
                }
            }
        }
    }
}
