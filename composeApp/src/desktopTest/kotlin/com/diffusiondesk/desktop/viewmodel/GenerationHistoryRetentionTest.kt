package com.diffusiondesk.desktop.viewmodel

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.diffusiondesk.desktop.core.api.GeneratedImage
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerationHistoryRetentionTest {
    @Test
    fun trimsOldTerminalEntriesButPreservesQueueAndCurrentSelection() {
        val completed = (1..6).map { index ->
            GenerationHistoryItem(
                id = "item-$index",
                status = GenerationStatus.Completed,
                params = GenerationParams(
                    prompt = "prompt $index",
                    negativePrompt = "",
                    width = 64,
                    height = 64,
                    steps = 1,
                    cfgScale = 1.0,
                    seed = index,
                    sampler = "euler",
                ),
                imageUrls = listOf("/outputs/image-$index.png"),
                images = listOf(testImage(index)),
            )
        }
        val pending = completed.last().copy(id = "pending", status = GenerationStatus.Pending)
        val state = GenerationUiState(
            history = completed + pending,
            historyIndex = 0,
        )

        val retained = state.compactGenerationHistory(terminalLimit = 4, imagePayloadLimit = 2)

        assertEquals(listOf("item-1", "item-3", "item-4", "item-5", "item-6", "pending"), retained.history.map { it.id })
        assertEquals(0, retained.historyIndex)
        assertTrue(retained.history.first { it.id == "item-3" }.images.isEmpty())
        assertTrue(retained.history.first { it.id == "item-5" }.images.isNotEmpty())
        assertTrue(retained.history.last().status == GenerationStatus.Pending)
    }

    @Test
    fun resolvesSavedAndTemporaryOutputUrlsToDisk() {
        val outputDir = Files.createTempDirectory("diffusion-desk-history-output").toFile()

        assertEquals(
            outputDir.resolve("image.png").canonicalFile,
            assertNotNull(resolveGeneratedOutputFile(outputDir.absolutePath, "/outputs/image.png")),
        )
        assertEquals(
            outputDir.resolve("temp/image.png").canonicalFile,
            assertNotNull(resolveGeneratedOutputFile(outputDir.absolutePath, "http://127.0.0.1:1234/outputs/temp/image.png")),
        )
        assertEquals(null, resolveGeneratedOutputFile(outputDir.absolutePath, "/outputs/../outside.png"))
    }

    private fun testImage(index: Int): GeneratedImage {
        val bufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        return GeneratedImage(
            bitmap = bufferedImage.toComposeImageBitmap(),
            bufferedImage = bufferedImage,
            bytes = byteArrayOf(index.toByte()),
            sourceUrl = "memory://$index",
        )
    }
}
