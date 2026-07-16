package com.diffusiondesk.desktop.core.api

import androidx.compose.ui.graphics.ImageBitmap
import java.awt.image.BufferedImage

data class GenerationRequest(
    val modelId: String,
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Double,
    val seed: Int,
    val sampler: String,
    val saveImage: Boolean,
    val initImageBase64: String? = null,
    val strength: Double = 0.75,
)

data class GenerationResult(
    val imageUrls: List<String>,
    val usedSeed: Int,
    val generationTime: Double? = null,
)

data class GeneratedImage(
    val bitmap: ImageBitmap,
    val bufferedImage: BufferedImage,
    val bytes: ByteArray,
    val sourceUrl: String,
    val generationTime: Double? = null,
)

data class GenerationJobSubmission(
    val id: String,
    val status: String,
)

sealed class GenerationJobEvent {
    data class Queued(val jobId: String, val queuePosition: Int) : GenerationJobEvent()
    data class Started(val jobId: String) : GenerationJobEvent()
    data class Progress(
        val jobId: String,
        val step: Int,
        val steps: Int,
        val time: Double,
        val phase: String,
        val message: String,
    ) : GenerationJobEvent()

    data class Completed(val jobId: String, val result: GenerationResult) : GenerationJobEvent()
    data class Failed(val jobId: String, val message: String) : GenerationJobEvent()
    data class Cancelled(val jobId: String, val message: String) : GenerationJobEvent()
}
