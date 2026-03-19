package com.diffusiondesk.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO

data class ServerConfig(
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
)

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
)

data class GenerationResult(
    val imageUrl: String,
    val usedSeed: Int,
)

class DiffusionDeskClient {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun fetchConfig(baseUrl: String): Result<ServerConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/config"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Config request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            ServerConfig(
                modelDir = root["model_dir"]?.jsonPrimitive?.content.orEmpty(),
                outputDir = root["output_dir"]?.jsonPrimitive?.content.orEmpty(),
                setupCompleted = root["setup_completed"]?.jsonPrimitive?.booleanOrNull ?: true,
            )
        }
    }

    suspend fun updateConfig(baseUrl: String, settings: DesktopSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("model_dir", JsonPrimitive(settings.modelDir))
                put("output_dir", JsonPrimitive(settings.outputDir))
                put("setup_completed", JsonPrimitive(settings.setupCompleted))
            }

            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/config"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(10))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Config update failed with ${response.statusCode()}" }
        }
    }

    suspend fun generateImage(baseUrl: String, requestData: GenerationRequest): Result<GenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                if (requestData.modelId.isNotBlank()) {
                    put("model_id", JsonPrimitive(requestData.modelId))
                }
                put("prompt", JsonPrimitive(requestData.prompt))
                put("negative_prompt", JsonPrimitive(requestData.negativePrompt))
                put("sample_steps", JsonPrimitive(requestData.steps))
                put("cfg_scale", JsonPrimitive(requestData.cfgScale))
                put("strength", JsonPrimitive(0.75))
                put("n", JsonPrimitive(1))
                put("sampling_method", JsonPrimitive(requestData.sampler))
                put("seed", JsonPrimitive(requestData.seed))
                put("width", JsonPrimitive(requestData.width))
                put("height", JsonPrimitive(requestData.height))
                put("save_image", JsonPrimitive(true))
                put("no_base64", JsonPrimitive(true))
            }

            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/images/generations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Generation failed with ${response.statusCode()}" } }

            val root = json.parseToJsonElement(response.body()).jsonObject
            val first = root["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("Generation response did not include image data.")

            GenerationResult(
                imageUrl = first["url"]?.jsonPrimitive?.content ?: error("Missing image url"),
                usedSeed = first["seed"]?.jsonPrimitive?.intOrNull ?: requestData.seed,
            )
        }
    }

    suspend fun fetchImageBitmap(baseUrl: String, imageUrl: String): Result<ImageBitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                imageUrl
            } else {
                "$baseUrl$imageUrl"
            }

            val request = HttpRequest.newBuilder(URI.create(resolvedUrl))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() in 200..299) { "Failed to download generated image." }

            val bufferedImage = ImageIO.read(ByteArrayInputStream(response.body()))
                ?: error("Image decode failed.")
            bufferedImage.toComposeImageBitmap()
        }
    }
}
