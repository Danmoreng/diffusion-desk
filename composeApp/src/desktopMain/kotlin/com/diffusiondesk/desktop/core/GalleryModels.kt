package com.diffusiondesk.desktop.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class GalleryImage(
    val id: Long,
    val filePath: String,
    val previewPath: String,
    val prompt: String,
    val negativePrompt: String,
    val seed: Long?,
    val width: Int?,
    val height: Int?,
    val steps: Int?,
    val cfgScale: Double?,
    val sampler: String,
    val modelId: String,
    val presetId: String,
    val generationTime: Double?,
    val createdAt: Long,
    val modifiedAt: Long,
    val metadataText: String,
    val favorite: Boolean,
    val rating: Int,
    val keywords: List<String>,
) {
    val file: File get() = File(filePath)
    val previewFile: File get() = previewPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile } ?: file
    val displayName: String get() = file.name
    val dimensions: String get() = if (width != null && height != null) "${width}x${height}" else ""
    val aspectRatio: Float get() = if (width != null && height != null && height > 0) width.toFloat() / height.toFloat() else 1f
}

data class GalleryReusableParams(
    val prompt: String,
    val promptMode: ImagePromptMode,
    val negativePrompt: String,
    val width: Int?,
    val height: Int?,
    val steps: Int?,
    val cfgScale: Double?,
    val sampler: String,
    val seed: Long?,
    val modelId: String,
    val presetId: String,
)

fun inferGalleryPromptMode(prompt: String, modelId: String, presetId: String): ImagePromptMode {
    if (presetId.contains("ideogram", ignoreCase = true) || modelId.contains("ideogram", ignoreCase = true)) {
        return ImagePromptMode.Json
    }
    val isCompositionJson = runCatching {
        val root = Json.parseToJsonElement(prompt).jsonObject
        val composition = root["compositional_deconstruction"]?.jsonObject
        composition?.containsKey("elements") == true
    }.getOrDefault(false)
    return if (isCompositionJson) {
        ImagePromptMode.Json
    } else {
        ImagePromptMode.Text
    }
}

data class ParsedImageMetadata(
    val prompt: String = "",
    val negativePrompt: String = "",
    val seed: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val steps: Int? = null,
    val cfgScale: Double? = null,
    val sampler: String = "",
    val modelId: String = "",
    val generationTime: Double? = null,
    val metadataText: String = "",
)
