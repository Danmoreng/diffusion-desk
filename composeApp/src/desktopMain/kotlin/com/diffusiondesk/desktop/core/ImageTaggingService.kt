package com.diffusiondesk.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class TaggingResult(
    val imageId: Long,
    val imageName: String,
    val tags: List<String>,
)

data class TaggingBatchResult(
    val completed: Int,
    val failed: Int,
)

class ImageTaggingService(
    private val galleryRepository: GalleryRepository,
    private val llmWorkerPool: LlmWorkerPool,
    private val client: DiffusionDeskClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun tagNextImage(
        settings: DesktopSettings,
        preset: LlmPreset,
    ): Result<TaggingResult> = withContext(Dispatchers.IO) {
        runCatching {
            tagClaimedImage(settings, preset, claimNextImage(preset))
        }
    }

    suspend fun tagImage(
        settings: DesktopSettings,
        preset: LlmPreset,
        image: GalleryImage,
    ): Result<TaggingResult> = withContext(Dispatchers.IO) {
        runCatching {
            tagClaimedImage(settings, preset, image)
        }
    }

    suspend fun tagPendingImages(
        settings: DesktopSettings,
        preset: LlmPreset,
        maxItems: Int = Int.MAX_VALUE,
        refreshOutputIndex: Boolean = true,
    ): Result<TaggingBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (refreshOutputIndex) {
                galleryRepository.indexOutputDirectory(settings.outputDir)
            }

            var completed = 0
            var failed = 0
            while (completed + failed < maxItems) {
                val image = galleryRepository.claimNextPendingLlmTag(preset.id) ?: break
                runCatching {
                    tagClaimedImage(settings, preset, image)
                }.onSuccess {
                    completed += 1
                }.onFailure {
                    failed += 1
                }
            }
            TaggingBatchResult(completed = completed, failed = failed)
        }
    }

    private fun claimNextImage(preset: LlmPreset): GalleryImage =
        galleryRepository.claimNextPendingLlmTag(preset.id)
            ?: error("No gallery image is pending LLM tags.")

    private suspend fun tagClaimedImage(
        settings: DesktopSettings,
        preset: LlmPreset,
        image: GalleryImage,
    ): TaggingResult {
        try {
            val worker = llmWorkerPool.ensureWorkerForPreset(settings, preset).getOrThrow()
            val response = if (preset.mmprojPath.isNotBlank()) {
                client.visionChatCompletion(
                    baseUrl = worker.baseUrl,
                    model = preset.modelPath,
                    systemPrompt = VISION_TAGGING_SYSTEM_PROMPT,
                    userPrompt = VISION_TAGGING_USER_PROMPT,
                    imageDataUri = image.toDataUri(),
                ).getOrThrow()
            } else {
                client.chatCompletion(
                    baseUrl = worker.baseUrl,
                    model = preset.modelPath,
                    messages = listOf(
                        LlmChatMessage(
                            role = "system",
                            content = METADATA_TAGGING_SYSTEM_PROMPT,
                        ),
                        LlmChatMessage(
                            role = "user",
                            content = buildPrompt(image),
                        ),
                    ),
                ).getOrThrow()
            }

            val tags = parseTags(response)
            require(tags.isNotEmpty()) { "The tagging LLM did not return any tags." }
            val existingTags = image.keywords.map { it.trim().lowercase(Locale.US) }.toSet()
            val newTags = tags.filterNot { it in existingTags }
            galleryRepository.completeLlmTagging(image.id, preset.id, newTags)

            return TaggingResult(
                imageId = image.id,
                imageName = image.displayName,
                tags = newTags,
            )
        } catch (error: Throwable) {
            galleryRepository.failLlmTagging(
                imageId = image.id,
                presetId = preset.id,
                message = error.message ?: "Tagging failed.",
            )
            throw error
        }
    }

    private fun buildPrompt(image: GalleryImage): String {
        return buildString {
            appendLine("Create search tags for this generated image from its metadata.")
            if (image.prompt.isNotBlank()) appendLine("Prompt: ${image.prompt}")
            if (image.negativePrompt.isNotBlank()) appendLine("Negative prompt: ${image.negativePrompt}")
            if (image.modelId.isNotBlank()) appendLine("Model: ${image.modelId}")
            image.dimensions.takeIf { it.isNotBlank() }?.let { appendLine("Size: $it") }
            appendLine("Return 5 to 12 lowercase tags separated by commas.")
        }
    }

    companion object {
        const val METADATA_TAGGING_SYSTEM_PROMPT =
            "Return only concise comma-separated image tags. Do not write a sentence."
        const val VISION_TAGGING_SYSTEM_PROMPT =
            "Extract the following fields from the image:\n\n" +
                "tags: A JSON array of 8 to 12 concise lowercase gallery tags covering the main subject, specific visible objects, medium or style, composition, lighting, background, mood, and any readable text. Prefer visually specific tags over generic category labels.\n\n" +
                "Respond with only a JSON object. Do not include any text outside the JSON."
        const val VISION_TAGGING_USER_PROMPT =
            "Analyze this image and extract useful gallery search tags."
        val TAG_FIELD_NAMES = setOf("tags", "tag", "keywords", "keyword")
    }

    private fun parseTags(value: String): List<String> {
        val jsonTags = parseJsonTags(value)

        val rawTags = jsonTags.takeIf { it.isNotEmpty() } ?: splitTagText(value)

        return rawTags
            .asSequence()
            .flatMap { splitTagText(it).asSequence() }
            .map(::normalizeTag)
            .filter { it.length in 2..40 }
            .filterNot { it in TAG_FIELD_NAMES }
            .distinct()
            .take(12)
            .toList()
    }

    private fun parseJsonTags(value: String): List<String> {
        return runCatching {
            val root = json.parseToJsonElement(extractJson(value))
            tagsFromJson(root)
        }.getOrDefault(emptyList())
    }

    private fun tagsFromJson(root: JsonElement): List<String> {
        return when (root) {
            is JsonArray -> root.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonObject -> {
                root["tags"]?.let(::tagsFromJson)
                    ?.takeIf { it.isNotEmpty() }
                    ?: root.values
                        .flatMap { value -> tagsFromJson(value) }
                        .filterNot { it in TAG_FIELD_NAMES }
            }
            is JsonPrimitive -> {
                val content = root.contentOrNull.orEmpty()
                if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                    parseJsonTags(content).takeIf { it.isNotEmpty() } ?: splitTagText(content)
                } else {
                    splitTagText(content)
                }
            }
        }
    }

    private fun splitTagText(value: String): List<String> {
        return value
            .replace(Regex("[{}\\[\\]\"]"), " ")
            .lineSequence()
            .flatMap { it.split(',', ';', '|').asSequence() }
            .map { token -> token.replace(Regex("""^\s*(tags|keywords)\s*[:=]\s*""", RegexOption.IGNORE_CASE), "") }
            .toList()
    }

    private fun normalizeTag(token: String): String {
        return token
            .trim()
            .trim('-', '*', '.', ':')
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
    }

    private fun GalleryImage.toDataUri(): String {
        val bytes = file.readBytes()
        val mime = when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        return "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun extractJson(value: String): String {
        val trimmed = value.trim()
        val objectStart = trimmed.indexOf('{')
        val objectEnd = trimmed.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1)
        }
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }
        return trimmed
    }

}
