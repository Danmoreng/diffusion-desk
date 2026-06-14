package com.diffusiondesk.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import java.awt.image.BufferedImage
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
    val sampler: String,
    val saveImage: Boolean,
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
)

data class UpscaleResult(
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val name: String,
)

data class GenerationJobSubmission(
    val id: String,
    val status: String,
)

data class LlmHealth(
    val modelLoaded: Boolean,
    val modelPath: String,
    val placement: String,
    val nGpuLayers: Int,
    val advancedArgs: List<String>,
    val vramAllocatedMb: Int,
    val vramFreeMb: Int,
)

data class LlmChatMessage(
    val role: String,
    val content: String = "",
    val imageDataUri: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmChatCompletion(
    val content: String,
    val reasoningContent: String,
    val finishReason: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val promptTokensPerSecond: Double?,
    val predictedTokensPerSecond: Double?,
    val elapsedMs: Long,
    val rawResponsePreview: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
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

class DiffusionDeskClient(
    private val internalToken: String = "",
    private val llmDebugLog: LlmDebugLog? = null,
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private fun requestBuilder(uri: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(uri)).apply {
            if (internalToken.isNotBlank()) {
                header("X-Internal-Token", internalToken)
            }
        }

    suspend fun fetchConfig(baseUrl: String): Result<ServerConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/config")
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
            val request = requestBuilder("$baseUrl/v1/models")
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

    suspend fun loadPreset(baseUrl: String, preset: ImagePreset, settings: DesktopSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("model_id", JsonPrimitive(preset.diffusionModel))
                putIfNotBlank("uncond_diffusion_model", preset.uncondDiffusionModel)
                putIfNotBlank("vae", preset.vae)
                putIfNotBlank("clip_l", preset.clipL)
                putIfNotBlank("clip_g", preset.clipG)
                putIfNotBlank("t5xxl", preset.t5xxl)
                putIfNotBlank("llm", preset.llm)
                put("clip_on_cpu", JsonPrimitive(preset.clipOnCpu))
                put("vae_on_cpu", JsonPrimitive(preset.vaeOnCpu))
                put("offload_to_cpu", JsonPrimitive(preset.offloadParamsToCpu || preset.streamLayers))
                put("flash_attn", JsonPrimitive(preset.flashAttention))
                if (preset.streamLayers) {
                    put("stream_layers", JsonPrimitive(true))
                    val maxVramGb = preset.maxVramGb.takeIf { it > 0.0 }
                        ?: if (settings.vramBudgetMode == "manual") settings.manualVramBudgetGb else -2.0
                    put("max_vram_gb", JsonPrimitive(maxVramGb))
                }
            }

            val request = requestBuilder("$baseUrl/v1/models/load")
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
            val request = requestBuilder("$baseUrl/internal/health")
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
            val request = requestBuilder("$baseUrl/v1/progress")
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

    suspend fun verifyLlmWorker(baseUrl: String): Result<LlmHealth> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/internal/health")
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "LLM worker health request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            check(root["service"]?.jsonPrimitive?.content == "llm") { "Port is not running the LLM worker." }
            LlmHealth(
                modelLoaded = root["model_loaded"]?.jsonPrimitive?.booleanOrNull ?: false,
                modelPath = root["model_path"]?.jsonPrimitive?.content.orEmpty(),
                placement = root["placement"]?.jsonPrimitive?.content.orEmpty(),
                nGpuLayers = root["n_gpu_layers"]?.jsonPrimitive?.intOrNull ?: -1,
                advancedArgs = root["advanced_args"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) },
                vramAllocatedMb = root["vram_allocated_mb"]?.jsonPrimitive?.intOrNull ?: 0,
                vramFreeMb = root["vram_free_mb"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }

    suspend fun fetchSamplerOptions(baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/options/samplers")
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Sampler options request failed with ${response.statusCode()}" }

            val root = json.parseToJsonElement(response.body()).jsonObject
            root["data"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                .distinct()
        }
    }

    suspend fun updateConfig(baseUrl: String, settings: DesktopSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("model_dir", JsonPrimitive(settings.modelDir))
                put("output_dir", JsonPrimitive(settings.outputDir))
                put("setup_completed", JsonPrimitive(settings.setupCompleted))
            }

            val request = requestBuilder("$baseUrl/v1/config")
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
        val request = requestBuilder("$baseUrl/internal/shutdown")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(3))
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }

    suspend fun shutdownLlmWorker(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            shutdownLlmWorkerBlocking(baseUrl)
        }
    }

    fun shutdownLlmWorkerBlocking(baseUrl: String) {
        val request = requestBuilder("$baseUrl/internal/shutdown")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(3))
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }

    suspend fun unloadImageModel(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/models/unload")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Image model unload failed with ${response.statusCode()}" } }
        }
    }

    suspend fun loadLlmPreset(baseUrl: String, preset: LlmPreset): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val advancedArgs = preset.effectiveAdvancedArgs().getOrThrow()
            val payload = buildJsonObject {
                put("model_id", JsonPrimitive(preset.modelPath))
                if (preset.mmprojPath.isNotBlank()) {
                    put("mmproj_id", JsonPrimitive(preset.mmprojPath))
                }
                if (preset.placement == LlmPlacement.Cpu) {
                    put("n_gpu_layers", JsonPrimitive(0))
                }
                put("advanced_args", JsonArray(advancedArgs.map { JsonPrimitive(it) }))
            }

            val request = requestBuilder("$baseUrl/v1/llm/load")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "LLM load failed with ${response.statusCode()}" } }
        }
    }

    suspend fun unloadLlmModel(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/llm/unload")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(20))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "LLM unload failed with ${response.statusCode()}" } }
        }
    }

    suspend fun chatCompletion(
        baseUrl: String,
        model: String,
        messages: List<LlmChatMessage>,
        maxTokens: Int? = null,
        temperature: Double? = null,
        jsonResponse: Boolean = false,
        reasoningFormat: String? = null,
        enableThinking: Boolean? = null,
        reasoningBudgetTokens: Int? = null,
        cachePrompt: Boolean? = null,
        slotId: Int? = null,
        tools: JsonArray? = null,
        parallelToolCalls: Boolean? = null,
        timeout: Duration = Duration.ofMinutes(3),
    ): Result<String> = withContext(Dispatchers.IO) {
        chatCompletionDetailed(
            baseUrl = baseUrl,
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            temperature = temperature,
            jsonResponse = jsonResponse,
            reasoningFormat = reasoningFormat,
            enableThinking = enableThinking,
            reasoningBudgetTokens = reasoningBudgetTokens,
            cachePrompt = cachePrompt,
            slotId = slotId,
            tools = tools,
            parallelToolCalls = parallelToolCalls,
            timeout = timeout,
        ).mapCatching { completion ->
            completion.content.ifBlank {
                error("Chat completion returned empty content. Raw response: ${completion.rawResponsePreview}")
            }
        }
    }

    suspend fun chatCompletionDetailed(
        baseUrl: String,
        model: String,
        messages: List<LlmChatMessage>,
        maxTokens: Int? = null,
        temperature: Double? = null,
        jsonResponse: Boolean = false,
        reasoningFormat: String? = null,
        enableThinking: Boolean? = null,
        reasoningBudgetTokens: Int? = null,
        cachePrompt: Boolean? = null,
        slotId: Int? = null,
        tools: JsonArray? = null,
        parallelToolCalls: Boolean? = null,
        timeout: Duration = Duration.ofMinutes(3),
    ): Result<LlmChatCompletion> = withContext(Dispatchers.IO) {
        val callId = llmDebugLog?.start(
            model = model,
            systemPrompt = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content },
            userPrompt = messages.filter { it.role != "system" }.joinToString("\n\n") { "${it.role}: ${it.content}" },
        )
        runCatching {
            val payload = buildJsonObject {
                put("model", JsonPrimitive(model))
                put(
                    "messages",
                    JsonArray(
                        messages.map { message ->
                            buildJsonObject {
                                put("role", JsonPrimitive(message.role))
                                val imageDataUri = message.imageDataUri
                                if (imageDataUri.isNullOrBlank()) {
                                    put("content", JsonPrimitive(message.content))
                                } else {
                                    put(
                                        "content",
                                        JsonArray(
                                            listOf(
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("text"))
                                                    put("text", JsonPrimitive(message.content))
                                                },
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("image_url"))
                                                    put(
                                                        "image_url",
                                                        buildJsonObject {
                                                            put("url", JsonPrimitive(imageDataUri))
                                                        },
                                                    )
                                                },
                                            ),
                                        ),
                                    )
                                }
                                message.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                                if (message.toolCalls.isNotEmpty()) {
                                    put(
                                        "tool_calls",
                                        JsonArray(
                                            message.toolCalls.map { toolCall ->
                                                buildJsonObject {
                                                    put("id", JsonPrimitive(toolCall.id))
                                                    put("type", JsonPrimitive("function"))
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", JsonPrimitive(toolCall.name))
                                                            put("arguments", JsonPrimitive(toolCall.arguments))
                                                        },
                                                    )
                                                }
                                            },
                                        ),
                                    )
                                }
                            }
                        },
                    ),
                )
                put("stream", JsonPrimitive(false))
                maxTokens?.let { put("max_tokens", JsonPrimitive(it)) }
                temperature?.let { put("temperature", JsonPrimitive(it)) }
                reasoningFormat?.let { put("reasoning_format", JsonPrimitive(it)) }
                reasoningBudgetTokens?.let { put("thinking_budget_tokens", JsonPrimitive(it)) }
                cachePrompt?.let { put("cache_prompt", JsonPrimitive(it)) }
                slotId?.let { put("id_slot", JsonPrimitive(it)) }
                tools?.let { put("tools", it) }
                parallelToolCalls?.let { put("parallel_tool_calls", JsonPrimitive(it)) }
                enableThinking?.let {
                    put(
                        "chat_template_kwargs",
                        buildJsonObject {
                            put("enable_thinking", JsonPrimitive(it))
                        },
                    )
                }
                if (jsonResponse) {
                    put(
                        "response_format",
                        buildJsonObject {
                            put("type", JsonPrimitive("json_object"))
                        },
                    )
                }
            }

            val request = requestBuilder("$baseUrl/v1/chat/completions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(timeout)
                .build()

            val startNanos = System.nanoTime()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Chat completion failed with ${response.statusCode()}" } }

            val root = json.parseToJsonElement(response.body()).jsonObject
            parseChatCompletion(root, response.body().compactPreview(), elapsedMs)
        }.also { result -> recordDetailedLlmResult(callId, result) }
    }

    suspend fun visionChatCompletion(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageDataUri: String,
        useImageTagSchema: Boolean = true,
        maxTokens: Int? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        visionChatCompletionDetailed(
            baseUrl = baseUrl,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imageDataUri = imageDataUri,
            useImageTagSchema = useImageTagSchema,
            maxTokens = maxTokens,
        ).mapCatching { it.content }
    }

    suspend fun visionChatCompletionDetailed(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageDataUri: String,
        useImageTagSchema: Boolean = true,
        maxTokens: Int? = null,
    ): Result<LlmChatCompletion> = withContext(Dispatchers.IO) {
        val callId = llmDebugLog?.start(model, systemPrompt, "$userPrompt\n\n[Image reference attached]")
        runCatching {
            val payload = buildJsonObject {
                put("model", JsonPrimitive(model))
                put(
                    "messages",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("role", JsonPrimitive("system"))
                                put("content", JsonPrimitive(systemPrompt))
                            },
                            buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put(
                                    "content",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("type", JsonPrimitive("text"))
                                                put("text", JsonPrimitive(userPrompt))
                                            },
                                            buildJsonObject {
                                                put("type", JsonPrimitive("image_url"))
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", JsonPrimitive(imageDataUri))
                                                    },
                                                )
                                            },
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                )
                put("temperature", JsonPrimitive(0.0))
                if (useImageTagSchema) {
                    put("response_format", imageTagsResponseFormat())
                }
                maxTokens?.let { put("max_tokens", JsonPrimitive(it)) }
                put("stream", JsonPrimitive(false))
            }

            val request = requestBuilder("$baseUrl/v1/chat/completions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(3))
                .build()

            val startNanos = System.nanoTime()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Vision chat completion failed with ${response.statusCode()}" } }
            parseChatCompletion(json.parseToJsonElement(response.body()).jsonObject, response.body().compactPreview(), elapsedMs)
        }.also { result -> recordDetailedLlmResult(callId, result) }
    }

    suspend fun compositionChatCompletion(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageDataUri: String? = null,
        maxTokens: Int = 1024,
        timeout: Duration = Duration.ofMinutes(5),
    ): Result<String> = withContext(Dispatchers.IO) {
        val debugUserPrompt = if (imageDataUri == null) userPrompt else "$userPrompt\n\n[Image reference attached]"
        val callId = llmDebugLog?.start(model, systemPrompt, debugUserPrompt)
        runCatching {
            val userContent: JsonElement = if (imageDataUri == null) {
                JsonPrimitive(userPrompt)
            } else {
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(userPrompt))
                        },
                        buildJsonObject {
                            put("type", JsonPrimitive("image_url"))
                            put(
                                "image_url",
                                buildJsonObject {
                                    put("url", JsonPrimitive(imageDataUri))
                                },
                            )
                        },
                    ),
                )
            }
            val payload = buildJsonObject {
                put("model", JsonPrimitive(model))
                put(
                    "messages",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("role", JsonPrimitive("system"))
                                put("content", JsonPrimitive(systemPrompt))
                            },
                            buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", userContent)
                            },
                        ),
                    ),
                )
                put("max_tokens", JsonPrimitive(maxTokens))
                put("temperature", JsonPrimitive(0.2))
                put(
                    "response_format",
                    buildJsonObject {
                        put("type", JsonPrimitive("json_object"))
                    },
                )
                put("stream", JsonPrimitive(false))
                put(
                    "chat_template_kwargs",
                    buildJsonObject {
                        put("enable_thinking", JsonPrimitive(false))
                    },
                )
            }

            val request = requestBuilder("$baseUrl/v1/chat/completions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(timeout)
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) {
                response.body().ifBlank { "Composition completion failed with ${response.statusCode()}" }
            }
            parseChatContent(json.parseToJsonElement(response.body()).jsonObject).ifBlank {
                error("Composition completion returned empty content.")
            }
        }.also { result -> recordLlmResult(callId, result) }
    }

    private fun recordLlmResult(callId: Long?, result: Result<String>) {
        if (callId == null) return
        result.onSuccess { llmDebugLog?.complete(callId, it) }
            .onFailure { llmDebugLog?.fail(callId, it) }
    }

    private fun imageTagsResponseFormat() = buildJsonObject {
        put("type", JsonPrimitive("json_schema"))
        put(
            "json_schema",
            buildJsonObject {
                put("name", JsonPrimitive("image_tags"))
                put("strict", JsonPrimitive(true))
                put(
                    "schema",
                    buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("additionalProperties", JsonPrimitive(false))
                        put("required", JsonArray(listOf(JsonPrimitive("tags"))))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "tags",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("array"))
                                        put("minItems", JsonPrimitive(8))
                                        put("maxItems", JsonPrimitive(12))
                                        put(
                                            "items",
                                            buildJsonObject {
                                                put("type", JsonPrimitive("string"))
                                                put("minLength", JsonPrimitive(2))
                                                put("maxLength", JsonPrimitive(40))
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
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
                put("save_image", JsonPrimitive(requestData.saveImage))
                put("no_base64", JsonPrimitive(true))
            }

            val request = requestBuilder("$baseUrl/v1/images/generations")
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
            val request = requestBuilder("$baseUrl/v1/generation-jobs")
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

    suspend fun loadUpscaleModel(baseUrl: String, modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("model_id", JsonPrimitive(modelId))
            }
            val request = requestBuilder("$baseUrl/v1/upscale/load")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(2))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Upscale model load failed with ${response.statusCode()}" } }
        }
    }

    suspend fun upscaleImage(
        baseUrl: String,
        imageBase64: String,
        upscaleFactor: Int,
        saveImage: Boolean = true,
    ): Result<UpscaleResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("image", JsonPrimitive(imageBase64))
                put("upscale_factor", JsonPrimitive(upscaleFactor))
                put("save_image", JsonPrimitive(saveImage))
            }
            val request = requestBuilder("$baseUrl/v1/images/upscale")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofMinutes(10))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { response.body().ifBlank { "Upscale failed with ${response.statusCode()}" } }

            val root = json.parseToJsonElement(response.body()).jsonObject
            UpscaleResult(
                imageUrl = root["url"]?.jsonPrimitive?.content ?: error("Upscale response did not include a URL."),
                width = root["width"]?.jsonPrimitive?.intOrNull ?: 0,
                height = root["height"]?.jsonPrimitive?.intOrNull ?: 0,
                name = root["name"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
    }

    suspend fun cancelGenerationJob(baseUrl: String, jobId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/generation-jobs/$jobId/cancel")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) {
                response.body().ifBlank { "Job cancellation failed with ${response.statusCode()}" }
            }
        }
    }

    suspend fun streamGenerationJobEvents(
        baseUrl: String,
        jobId: String,
        onEvent: (GenerationJobEvent) -> Unit,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = requestBuilder("$baseUrl/v1/generation-jobs/$jobId/events")
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

    suspend fun fetchGeneratedImages(baseUrl: String, imageUrls: List<String>): Result<List<GeneratedImage>> = withContext(Dispatchers.IO) {
        runCatching {
            imageUrls.map { imageUrl ->
                fetchGeneratedImage(baseUrl, imageUrl).getOrThrow()
            }
        }
    }

    suspend fun fetchGeneratedImage(baseUrl: String, imageUrl: String): Result<GeneratedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                imageUrl
            } else {
                "$baseUrl$imageUrl"
            }

            var lastError: Throwable? = null
            repeat(8) { attempt ->
                val attemptResult = runCatching {
                    val request = requestBuilder(resolvedUrl)
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build()

                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    check(response.statusCode() in 200..299) { "Failed to download generated image (${response.statusCode()})." }

                    val imageBytes = response.body()
                    val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                        ?: error("Image decode failed.")
                    GeneratedImage(
                        bitmap = bufferedImage.toComposeImageBitmap(),
                        bufferedImage = bufferedImage,
                        bytes = imageBytes,
                        sourceUrl = resolvedUrl,
                    )
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
        put("n", JsonPrimitive(1))
        put("sampling_method", JsonPrimitive(requestData.sampler))
        put("seed", JsonPrimitive(requestData.seed))
        put("width", JsonPrimitive(requestData.width))
        put("height", JsonPrimitive(requestData.height))
        put("save_image", JsonPrimitive(requestData.saveImage))
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

    private fun parseChatContent(root: kotlinx.serialization.json.JsonObject): String {
        return parseChatCompletion(root, rawPreview = "", elapsedMs = 0L).content
    }

    private fun parseChatCompletion(
        root: kotlinx.serialization.json.JsonObject,
        rawPreview: String,
        elapsedMs: Long,
    ): LlmChatCompletion {
        val choice = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
        val message = choice
            ?.get("message")
            ?.jsonObject
        val usage = root["usage"]?.jsonObject
        val timings = root["timings"]?.jsonObject
        val promptTokens = usage?.firstInt("prompt_tokens", "prompt_n", "n_prompt_tokens")
            ?: timings?.firstInt("prompt_n", "prompt_tokens")
        val completionTokens = usage?.firstInt("completion_tokens", "predicted_n", "n_predicted_tokens")
            ?: timings?.firstInt("predicted_n", "completion_tokens")

        return LlmChatCompletion(
            content = message?.get("content")?.let(::parseChatContentValue).orEmpty(),
            reasoningContent = message?.get("reasoning_content")?.let(::parseChatContentValue).orEmpty(),
            finishReason = choice?.get("finish_reason")?.jsonPrimitive?.content.orEmpty(),
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = usage?.firstInt("total_tokens") ?: listOfNotNull(promptTokens, completionTokens)
                .takeIf { it.size == 2 }
                ?.sum(),
            promptTokensPerSecond = usage?.firstDouble("prompt_tokens_per_second", "prompt_per_second")
                ?: timings?.firstDouble("prompt_per_second", "prompt_tokens_per_second")
                ?: timings?.tokensPerSecond("prompt_n", "prompt_ms"),
            predictedTokensPerSecond = usage?.firstDouble("completion_tokens_per_second", "predicted_per_second")
                ?: timings?.firstDouble("predicted_per_second", "completion_tokens_per_second")
                ?: timings?.tokensPerSecond("predicted_n", "predicted_ms"),
            elapsedMs = elapsedMs,
            rawResponsePreview = rawPreview,
            toolCalls = message?.get("tool_calls")?.jsonArray?.mapIndexedNotNull { index, element ->
                val obj = element.jsonObject
                val function = obj["function"]?.jsonObject
                val name = function?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapIndexedNotNull null
                val arguments = function?.get("arguments")?.let(::parseToolArguments)
                    ?: obj["arguments"]?.let(::parseToolArguments)
                    ?: "{}"
                LlmToolCall(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_$index",
                    name = name,
                    arguments = arguments,
                )
            }.orEmpty(),
        )
    }

    private fun parseToolArguments(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    private fun parseChatContentValue(value: JsonElement): String = when {
        value is JsonPrimitive -> value.content
        value is JsonArray -> value.joinToString("") { part ->
            val obj = part as? kotlinx.serialization.json.JsonObject ?: return@joinToString ""
            obj["text"]?.jsonPrimitive?.content
                ?: obj["content"]?.jsonPrimitive?.content
                ?: ""
        }
        else -> ""
    }

    private fun kotlinx.serialization.json.JsonObject.firstInt(vararg keys: String): Int? {
        return keys.firstNotNullOfOrNull { key -> get(key)?.jsonPrimitive?.intOrNull }
    }

    private fun kotlinx.serialization.json.JsonObject.firstDouble(vararg keys: String): Double? {
        return keys.firstNotNullOfOrNull { key -> get(key)?.jsonPrimitive?.doubleOrNull }
    }

    private fun kotlinx.serialization.json.JsonObject.tokensPerSecond(tokenKey: String, millisKey: String): Double? {
        val tokens = firstDouble(tokenKey) ?: return null
        val millis = firstDouble(millisKey)?.takeIf { it > 0.0 } ?: return null
        return tokens / (millis / 1000.0)
    }

    private fun recordDetailedLlmResult(callId: Long?, result: Result<LlmChatCompletion>) {
        if (callId == null) return
        result.onSuccess { completion ->
            llmDebugLog?.complete(callId, completion.content.ifBlank { "[empty content]" })
        }.onFailure { error ->
            llmDebugLog?.fail(callId, error)
        }
    }

    private fun String.compactPreview(maxLength: Int = 800): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
    }
}
