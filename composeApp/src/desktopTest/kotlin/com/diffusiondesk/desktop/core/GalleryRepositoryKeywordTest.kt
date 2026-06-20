package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GalleryRepositoryKeywordTest {
    @Test
    fun listKeywordStatsIncludesUsageCounts() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-keyword-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val firstImageId = insertImage(db, tempDir.resolve("first.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        val secondImageId = insertImage(db, tempDir.resolve("second.png").apply { writeBytes(byteArrayOf(2)) }.absolutePath)
        repository.addKeyword(firstImageId, "shared")
        repository.addKeyword(secondImageId, "shared")
        repository.addKeyword(secondImageId, "solo")

        val stats = repository.listKeywordStats()

        assertEquals("shared", stats[0].name)
        assertEquals(2, stats[0].count)
        assertEquals("General", stats[0].category)
        assertEquals("solo", stats[1].name)
        assertEquals(1, stats[1].count)
    }

    @Test
    fun listImagesCanFilterByMultipleTags() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-keyword-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val firstImageId = insertImage(db, tempDir.resolve("first.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        val secondImageId = insertImage(db, tempDir.resolve("second.png").apply { writeBytes(byteArrayOf(2)) }.absolutePath)
        val thirdImageId = insertImage(db, tempDir.resolve("third.png").apply { writeBytes(byteArrayOf(3)) }.absolutePath)
        repository.addKeyword(firstImageId, "portrait")
        repository.addKeyword(firstImageId, "studio")
        repository.addKeyword(secondImageId, "portrait")
        repository.addKeyword(thirdImageId, "studio")

        val images = repository.listImages(query = "", keywords = listOf("portrait", "studio"))

        assertEquals(listOf(firstImageId), images.map { it.id })
    }

    @Test
    fun deleteKeywordRemovesTagFromAllImages() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-keyword-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val firstImageId = insertImage(db, tempDir.resolve("first.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        val secondImageId = insertImage(db, tempDir.resolve("second.png").apply { writeBytes(byteArrayOf(2)) }.absolutePath)
        repository.addKeyword(firstImageId, "portrait")
        repository.addKeyword(secondImageId, "portrait")
        repository.addKeyword(secondImageId, "studio")

        repository.deleteKeyword("portrait")

        assertEquals(listOf("studio"), repository.listKeywords())
        val images = repository.listImages()
        assertEquals(emptyList(), images.first { it.id == firstImageId }.keywords)
        assertEquals(listOf("studio"), images.first { it.id == secondImageId }.keywords)
    }

    @Test
    fun removingLastImageKeywordDeletesUnusedTag() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-keyword-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val imageId = insertImage(db, tempDir.resolve("image.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        repository.addKeyword(imageId, "portrait")

        repository.removeKeyword(imageId, "portrait")

        assertTrue(repository.listKeywords().isEmpty())
    }

    @Test
    fun deletingImageOnlyDeletesTagsNoOtherImageUses() {
        val tempDir = Files.createTempDirectory("diffusion-desk-gallery-keyword-test").toFile()
        val db = GalleryDatabase(tempDir.resolve("gallery.db"))
        val repository = GalleryRepository(db)
        val firstImageId = insertImage(db, tempDir.resolve("first.png").apply { writeBytes(byteArrayOf(1)) }.absolutePath)
        val secondImageId = insertImage(db, tempDir.resolve("second.png").apply { writeBytes(byteArrayOf(2)) }.absolutePath)
        repository.addKeyword(firstImageId, "shared")
        repository.addKeyword(firstImageId, "only-first")
        repository.addKeyword(secondImageId, "shared")

        repository.deleteImage(repository.listImages().first { it.id == firstImageId })

        assertEquals(listOf("shared"), repository.listKeywords())
        assertEquals(listOf("shared"), repository.listImages().single().keywords)
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
