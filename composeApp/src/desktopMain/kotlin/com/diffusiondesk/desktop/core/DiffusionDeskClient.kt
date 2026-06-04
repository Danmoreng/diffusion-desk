package com.diffusiondesk.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.imageio.ImageIO

data class ServerConfig(
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
)

data class ModelSummary(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val loaded: Boolean,
)

data class ProgressSnapshot(
    val step: Int,
    val steps: Int,
    val time: Double,
    val phase: String,
    val message: String,
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
    val batchCount: Int,
    val sampler: String,
)

data class GenerationResult(
    val imageUrls: List<String>,
    val usedSeed: Int,
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

    suspend fun fetchModels(baseUrl: String): Result<List<ModelSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/models"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Model request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            root["data"]?.jsonArray.orEmpty().map { item ->
                val model = item.jsonObject
                ModelSummary(
                    id = model["id"]?.jsonPrimitive?.content.orEmpty(),
                    name = model["name"]?.jsonPrimitive?.content.orEmpty(),
                    type = model["type"]?.jsonPrimitive?.content.orEmpty(),
                    active = model["active"]?.jsonPrimitive?.booleanOrNull ?: false,
                    loaded = model["loaded"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }
        }
    }

    suspend fun loadPreset(baseUrl: String, preset: ImagePreset): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("model_id", JsonPrimitive(preset.diffusionModel))
                putIfNotBlank("vae", preset.vae)
                putIfNotBlank("clip_l", preset.clipL)
                putIfNotBlank("clip_g", preset.clipG)
                putIfNotBlank("t5xxl", preset.t5xxl)
                putIfNotBlank("llm", preset.llm)
                put("clip_on_cpu", JsonPrimitive(preset.clipOnCpu))
                put("vae_on_cpu", JsonPrimitive(preset.vaeOnCpu))
                put("offload_to_cpu", JsonPrimitive(preset.offloadParamsToCpu))
                put("flash_attn", JsonPrimitive(preset.flashAttention))
            }

            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/models/load"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(2))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Preset load failed with ${response.statusCode()}" } }
        }
    }

    suspend fun verifyImageWorker(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/internal/health"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Worker health request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            check(root["service"]?.jsonPrimitive?.content == "sd") { "Port is not running the SD image worker." }
        }
    }

    suspend fun fetchProgress(baseUrl: String): Result<ProgressSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/progress"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Progress request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            ProgressSnapshot(
                step = root["step"]?.jsonPrimitive?.intOrNull ?: 0,
                steps = root["steps"]?.jsonPrimitive?.intOrNull ?: 0,
                time = root["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                phase = root["phase"]?.jsonPrimitive?.content.orEmpty(),
                message = root["message"]?.jsonPrimitive?.content.orEmpty(),
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

    suspend fun shutdownImageWorker(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            shutdownImageWorkerBlocking(baseUrl)
        }
    }

    fun shutdownImageWorkerBlocking(baseUrl: String) {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/internal/shutdown"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(3))
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
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
                put("n", JsonPrimitive(requestData.batchCount))
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
                imageUrls = root["data"]?.jsonArray.orEmpty().map { item ->
                    item.jsonObject["url"]?.jsonPrimitive?.content ?: error("Missing image url")
                },
                usedSeed = first["seed"]?.jsonPrimitive?.intOrNull ?: requestData.seed,
                generationTime = root["generation_time"]?.jsonPrimitive?.doubleOrNull,
            )
        }
    }

    suspend fun submitGenerationJob(baseUrl: String, requestData: GenerationRequest): Result<GenerationJobSubmission> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = generationPayload(requestData)
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/generation-jobs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Job submit failed with ${response.statusCode()}" } }

            val root = json.parseToJsonElement(response.body()).jsonObject
            GenerationJobSubmission(
                id = root["id"]?.jsonPrimitive?.content ?: error("Generation job response did not include an id."),
                status = root["status"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    suspend fun streamGenerationJobEvents(
        baseUrl: String,
        jobId: String,
        onEvent: (GenerationJobEvent) -> Unit,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/v1/generation-jobs/$jobId/events"))
                .header("Accept", "text/event-stream")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            check(response.statusCode() in 200..299) { "Generation event stream failed with ${response.statusCode()}" }

            var finalResult: GenerationResult? = null
            BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8)).use { reader ->
                var eventName = "message"
                val dataLines = mutableListOf<String>()

                fun dispatchEvent() {
                    if (dataLines.isEmpty()) {
                        eventName = "message"
                        return
                    }

                    val data = dataLines.joinToString("\n")
                    val event = parseGenerationJobEvent(eventName, data)
                    if (event is GenerationJobEvent.Completed) {
                        finalResult = event.result
                    }
                    onEvent(event)

                    eventName = "message"
                    dataLines.clear()
                }

                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isEmpty() -> dispatchEvent()
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                    }
                }
                dispatchEvent()
            }

            finalResult ?: error("Generation event stream closed before completion.")
        }
    }

    suspend fun fetchImageBitmaps(baseUrl: String, imageUrls: List<String>): Result<List<ImageBitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            imageUrls.map { imageUrl ->
                fetchImageBitmap(baseUrl, imageUrl).getOrThrow()
            }
        }
    }

    suspend fun fetchImageBitmap(baseUrl: String, imageUrl: String): Result<ImageBitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                imageUrl
            } else {
                "$baseUrl$imageUrl"
            }

            var lastError: Throwable? = null
            repeat(8) { attempt ->
                val attemptResult = runCatching {
                    val request = HttpRequest.newBuilder(URI.create(resolvedUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build()

                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    check(response.statusCode() in 200..299) { "Failed to download generated image (${response.statusCode()})." }

                    val bufferedImage = ImageIO.read(ByteArrayInputStream(response.body()))
                        ?: error("Image decode failed.")
                    bufferedImage.toComposeImageBitmap()
                }

                if (attemptResult.isSuccess) {
                    return@runCatching attemptResult.getOrThrow()
                }

                lastError = attemptResult.exceptionOrNull()
                if (attempt < 7) {
                    delay(200)
                }
            }

            throw IllegalStateException(lastError?.message ?: "Failed to download generated image.")
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotBlank(key: String, value: String) {
        if (value.isNotBlank()) {
            put(key, JsonPrimitive(value))
        }
    }

    private fun generationPayload(requestData: GenerationRequest) = buildJsonObject {
        if (requestData.modelId.isNotBlank()) {
            put("model_id", JsonPrimitive(requestData.modelId))
        }
        put("prompt", JsonPrimitive(requestData.prompt))
        put("negative_prompt", JsonPrimitive(requestData.negativePrompt))
        put("sample_steps", JsonPrimitive(requestData.steps))
        put("cfg_scale", JsonPrimitive(requestData.cfgScale))
        put("strength", JsonPrimitive(0.75))
        put("n", JsonPrimitive(requestData.batchCount))
        put("sampling_method", JsonPrimitive(requestData.sampler))
        put("seed", JsonPrimitive(requestData.seed))
        put("width", JsonPrimitive(requestData.width))
        put("height", JsonPrimitive(requestData.height))
        put("save_image", JsonPrimitive(true))
        put("no_base64", JsonPrimitive(true))
    }

    private fun parseGenerationJobEvent(eventName: String, data: String): GenerationJobEvent {
        val root = json.parseToJsonElement(data).jsonObject
        val jobId = root["job_id"]?.jsonPrimitive?.content.orEmpty()
        return when (eventName) {
            "queued" -> GenerationJobEvent.Queued(
                jobId = jobId,
                queuePosition = root["queue_position"]?.jsonPrimitive?.intOrNull ?: 0,
            )
            "started" -> GenerationJobEvent.Started(jobId)
            "progress" -> GenerationJobEvent.Progress(
                jobId = jobId,
                step = root["step"]?.jsonPrimitive?.intOrNull ?: 0,
                steps = root["steps"]?.jsonPrimitive?.intOrNull ?: 0,
                time = root["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                phase = root["phase"]?.jsonPrimitive?.content.orEmpty(),
                message = root["message"]?.jsonPrimitive?.content.orEmpty(),
            )
            "completed" -> GenerationJobEvent.Completed(
                jobId = jobId,
                result = parseGenerationResult(root["result"] ?: error("Completed event did not include a result.")),
            )
            "cancelled" -> GenerationJobEvent.Cancelled(
                jobId = jobId,
                message = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Generation job cancelled.",
            )
            "failed" -> GenerationJobEvent.Failed(
                jobId = jobId,
                message = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Generation job failed.",
            )
            else -> GenerationJobEvent.Progress(
                jobId = jobId,
                step = root["step"]?.jsonPrimitive?.intOrNull ?: 0,
                steps = root["steps"]?.jsonPrimitive?.intOrNull ?: 0,
                time = root["time"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                phase = root["phase"]?.jsonPrimitive?.content.orEmpty(),
                message = root["message"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    private fun parseGenerationResult(element: JsonElement): GenerationResult {
        val root = element.jsonObject
        val first = root["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Generation result did not include image data.")
        return GenerationResult(
            imageUrls = root["data"]?.jsonArray.orEmpty().map { item ->
                item.jsonObject["url"]?.jsonPrimitive?.content ?: error("Missing image url")
            },
            usedSeed = first["seed"]?.jsonPrimitive?.intOrNull ?: -1,
            generationTime = root["generation_time"]?.jsonPrimitive?.doubleOrNull,
        )
    }
}
