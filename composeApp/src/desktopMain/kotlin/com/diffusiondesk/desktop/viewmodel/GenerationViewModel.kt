package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GalleryRepository
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.GenerationJobEvent
import com.diffusiondesk.desktop.core.GenerationRequest
import com.diffusiondesk.desktop.core.GenerationSettingsStore
import com.diffusiondesk.desktop.core.GalleryReusableParams
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.ImageTaggingService
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.LlmRoleService
import com.diffusiondesk.desktop.core.SavedGenerationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.math.roundToInt

enum class GenerationStatus {
    Pending,
    Processing,
    Completed,
    Failed,
}

private val DEFAULT_SAMPLERS = listOf(
    "euler",
    "euler_a",
    "heun",
    "dpm2",
    "dpm++2s_a",
    "dpm++2m",
    "dpm++2mv2",
    "ipndm",
    "ipndm_v",
    "lcm",
    "ddim_trailing",
    "tcd",
    "res_multistep",
    "res_2s",
    "er_sde",
    "euler_cfg_pp",
    "euler_a_cfg_pp",
    "euler_ge",
)

data class GenerationParams(
    val prompt: String,
    val negativePrompt: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfgScale: Double,
    val seed: Int,
    val batchCount: Int,
    val sampler: String,
    val saveImage: Boolean = true,
)

data class GenerationHistoryItem(
    val id: String,
    val status: GenerationStatus,
    val params: GenerationParams,
    val promptMode: ImagePromptMode = ImagePromptMode.Text,
    val imageUrls: List<String> = emptyList(),
    val usedSeed: Int? = null,
    val images: List<GeneratedImage> = emptyList(),
    val error: String? = null,
    val generationTime: Double? = null,
)

data class GenerationProgressStage(
    val key: String,
    val label: String,
    val progress: Float,
    val isActive: Boolean,
    val isComplete: Boolean,
)

private data class ProgressTiming(
    val elapsedSeconds: Double,
    val etaSeconds: Int,
)

enum class IdeogramStructureTab {
    Text,
    Json,
    Preview,
}

private fun ImagePromptMode.toIdeogramTab(): IdeogramStructureTab = when (this) {
    ImagePromptMode.Text -> IdeogramStructureTab.Text
    ImagePromptMode.Json -> IdeogramStructureTab.Json
}

data class IdeogramElementPreview(
    val type: String,
    val text: String,
    val desc: String,
    val bbox: List<Int> = emptyList(),
    val colors: List<String> = emptyList(),
)

data class IdeogramUiState(
    val jsonPrompt: String = defaultIdeogramJsonPrompt(),
    val selectedTab: IdeogramStructureTab = IdeogramStructureTab.Text,
    val isGeneratingJson: Boolean = false,
    val jsonStatus: String = "JSON ready.",
    val jsonError: String? = null,
) {
    val isJsonValid: Boolean get() = jsonError == null
}

private val ideogramJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

@OptIn(ExperimentalSerializationApi::class)
private val ideogramJsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private fun defaultIdeogramJsonPrompt(): String = """
{
  "high_level_description": "A cinematic macro photograph of a single ripe strawberry with glossy red skin and a clear handwritten-style caption that reads EAT ME.",
  "style_description": {
    "aesthetics": "warm, appetizing, premium editorial food photography",
    "lighting": "soft golden-hour light, gentle highlights, shallow shadows",
    "photo": "macro photography, shallow depth of field, crisp subject detail",
    "medium": "photograph",
    "color_palette": ["#B91C1C", "#F43F5E", "#F8FAFC", "#FBBF24", "#1F2937"]
  },
  "compositional_deconstruction": {
    "background": "A soft-focus warm kitchen or studio background with subtle bokeh and no distracting details.",
    "elements": [
      {
        "type": "obj",
        "bbox": [140, 210, 810, 800],
        "desc": "A single ripe strawberry centered in the frame, glossy red skin, fresh green leaves, appealing macro detail."
      },
      {
        "type": "text",
        "bbox": [790, 240, 930, 760],
        "text": "EAT ME",
        "desc": "Bold white handwritten-style caption along the bottom, horizontal and clearly readable."
      }
    ]
  }
}
""".trimIndent()

private fun validateIdeogramJson(value: String): Pair<String, String?> {
    val root = runCatching { ideogramJson.parseToJsonElement(value).jsonObject }
        .getOrElse { return "Invalid JSON." to (it.message ?: "Invalid JSON.") }

    val composition = root["compositional_deconstruction"]?.jsonObjectOrNull()
        ?: return "Missing composition." to "compositional_deconstruction is required."
    composition["background"]?.jsonPrimitiveOrNull()?.content?.takeIf { it.isNotBlank() }
        ?: return "Missing background." to "compositional_deconstruction.background is required."
    val elements = composition["elements"]?.jsonArrayOrNull()
        ?: return "Missing elements." to "compositional_deconstruction.elements is required."
    if (elements.isEmpty()) {
        return "No elements." to "Add at least one obj or text element."
    }

    root["style_description"]?.jsonObjectOrNull()?.let { style ->
        val hasPhoto = style.containsKey("photo")
        val hasArtStyle = style.containsKey("art_style")
        if (hasPhoto == hasArtStyle) {
            return "Style mode issue." to "style_description must contain exactly one of photo or art_style."
        }
        val required = listOf("aesthetics", "lighting", "medium")
        required.forEach { key ->
            if (style[key]?.jsonPrimitiveOrNull()?.content?.isNotBlank() != true) {
                return "Missing style field." to "style_description.$key is required."
            }
        }
        style["color_palette"]?.let { palette ->
            val error = validatePalette(palette, maxColors = 16, label = "style_description.color_palette")
            if (error != null) return "Palette issue." to error
        }
    }

    elements.forEachIndexed { index, element ->
        val obj = element.jsonObjectOrNull()
            ?: return "Element issue." to "Element ${index + 1} must be an object."
        val type = obj["type"]?.jsonPrimitiveOrNull()?.content
            ?: return "Element issue." to "Element ${index + 1} is missing type."
        if (type !in setOf("obj", "text")) {
            return "Element issue." to "Element ${index + 1} type must be obj or text."
        }
        if (type == "text" && obj["text"]?.jsonPrimitiveOrNull()?.content?.isNotBlank() != true) {
            return "Text issue." to "Text element ${index + 1} must include literal text."
        }
        if (obj["desc"]?.jsonPrimitiveOrNull()?.content?.isNotBlank() != true) {
            return "Element issue." to "Element ${index + 1} is missing desc."
        }
        obj["bbox"]?.let { bbox ->
            val arr = bbox.jsonArrayOrNull()
                ?: return "Bbox issue." to "Element ${index + 1} bbox must be an array."
            if (arr.size != 4) {
                return "Bbox issue." to "Element ${index + 1} bbox must have four values."
            }
            val values = arr.mapNotNull { it.jsonPrimitiveOrNull()?.content?.toIntOrNull() }
            if (values.size != 4 || values.any { it !in 0..1000 }) {
                return "Bbox issue." to "Element ${index + 1} bbox values must be integers from 0 to 1000."
            }
            if (values[0] >= values[2] || values[1] >= values[3]) {
                return "Bbox issue." to "Element ${index + 1} bbox must be [y_min, x_min, y_max, x_max]."
            }
        }
        obj["color_palette"]?.let { palette ->
            val error = validatePalette(palette, maxColors = 5, label = "element ${index + 1} color_palette")
            if (error != null) return "Palette issue." to error
        }
    }

    return "Valid Ideogram JSON (${elements.size} element${if (elements.size == 1) "" else "s"})." to null
}

private fun normalizeIdeogramJsonPrompt(value: String): String? {
    val root = runCatching { ideogramJson.parseToJsonElement(value).jsonObject }.getOrNull() ?: return null
    val style = root["style_description"]?.jsonObjectOrNull() ?: return value
    val hasPhoto = style.containsKey("photo")
    val hasArtStyle = style.containsKey("art_style")
    if (hasPhoto != hasArtStyle) return value

    val medium = style["medium"]?.jsonPrimitiveOrNull()?.content.orEmpty()
    val aesthetics = style["aesthetics"]?.jsonPrimitiveOrNull()?.content.orEmpty()
    val styleMap = style.toMutableMap()
    val photoHint = listOf(medium, aesthetics)
        .joinToString(" ")
        .lowercase()
        .let { it.contains("photo") || it.contains("camera") || it.contains("realistic") }

    if (!hasPhoto && !hasArtStyle) {
        if (photoHint) {
            styleMap["photo"] = JsonPrimitive("photograph, realistic camera capture")
        } else {
            styleMap["art_style"] = JsonPrimitive(medium.ifBlank { "illustration" })
        }
    } else if (hasPhoto && hasArtStyle) {
        if (photoHint) {
            styleMap.remove("art_style")
        } else {
            styleMap.remove("photo")
        }
    }

    val rootMap = root.toMutableMap()
    rootMap["style_description"] = JsonObject(styleMap)
    return ideogramJsonPretty.encodeToString(JsonElement.serializer(), JsonObject(rootMap))
}

internal fun ideogramElementPreviews(value: String): List<IdeogramElementPreview> {
    val root = runCatching { ideogramJson.parseToJsonElement(value).jsonObject }.getOrNull() ?: return emptyList()
    val elements = root["compositional_deconstruction"]
        ?.jsonObjectOrNull()
        ?.get("elements")
        ?.jsonArrayOrNull()
        ?: return emptyList()
    return elements.mapNotNull { element ->
        val obj = element.jsonObjectOrNull() ?: return@mapNotNull null
        IdeogramElementPreview(
            type = obj["type"]?.jsonPrimitiveOrNull()?.content.orEmpty(),
            text = obj["text"]?.jsonPrimitiveOrNull()?.content.orEmpty(),
            desc = obj["desc"]?.jsonPrimitiveOrNull()?.content.orEmpty(),
            bbox = obj["bbox"]?.jsonArrayOrNull()?.mapNotNull { it.jsonPrimitiveOrNull()?.content?.toIntOrNull() } ?: emptyList(),
            colors = obj["color_palette"]?.jsonArrayOrNull()?.mapNotNull { it.jsonPrimitiveOrNull()?.content } ?: emptyList(),
        )
    }
}

private fun validatePalette(element: JsonElement, maxColors: Int, label: String): String? {
    val values = element.jsonArrayOrNull()
        ?: return "$label must be an array."
    if (values.size > maxColors) {
        return "$label can contain at most $maxColors colors."
    }
    val hex = Regex("^#[0-9A-F]{6}$")
    values.forEach { color ->
        val text = color.jsonPrimitiveOrNull()?.content ?: return "$label must contain only strings."
        if (!hex.matches(text)) return "$label color $text must use uppercase #RRGGBB."
    }
    return null
}

private fun extractJsonObject(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```").find(trimmed)?.groupValues?.getOrNull(1)
    if (fenced != null) return fenced
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

data class GenerationUiState(
    val selectedPresetId: String = "",
    val loadedPresetId: String = "",
    val prompt: String = "A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night.",
    val promptHistory: List<String> = listOf(prompt),
    val promptHistoryIndex: Int = 0,
    val negativePrompt: String = "deformed, blurry, low quality, watermark",
    val width: String = "1024",
    val height: String = "768",
    val steps: String = "4",
    val cfgScale: String = "1.0",
    val seed: String = "-1",
    val batchCount: String = "1",
    val sampler: String = "euler_a",
    val samplerOptions: List<String> = DEFAULT_SAMPLERS,
    val leftPanelWidthDp: Int = 560,
    val presets: List<ImagePreset> = emptyList(),
    val isLoadingPresets: Boolean = false,
    val isLoadingPreset: Boolean = false,
    val presetLoadFailed: Boolean = false,
    val progressStep: Int = 0,
    val progressSteps: Int = 0,
    val progressTime: Double = 0.0,
    val progressEtaSeconds: Int = 0,
    val progressPhase: String = "",
    val progressMessage: String = "",
    val progressStages: List<GenerationProgressStage> = emptyList(),
    val isGenerating: Boolean = false,
    val resultUrls: List<String> = emptyList(),
    val usedSeed: String = "",
    val images: List<GeneratedImage> = emptyList(),
    val history: List<GenerationHistoryItem> = emptyList(),
    val historyIndex: Int = -1,
    val isEndless: Boolean = false,
    val isEnhancingPrompt: Boolean = false,
    val ideogram: IdeogramUiState = IdeogramUiState(),
    val message: String = "",
    val error: String? = null,
) {
    val queueCount: Int get() = history.count { it.status == GenerationStatus.Pending }
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex >= 0 && historyIndex < history.lastIndex
    val currentHistoryItem: GenerationHistoryItem? get() = history.getOrNull(historyIndex)
    val canUndoPrompt: Boolean get() = promptHistoryIndex > 0
    val canRedoPrompt: Boolean get() = promptHistoryIndex < promptHistory.lastIndex
}

class GenerationViewModel(
    private val scope: CoroutineScope,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
    private val presetStore: ImagePresetStore,
    private val generationSettingsStore: GenerationSettingsStore,
    private val settingsStore: DesktopSettingsStore,
    private val llmPresetStore: LlmPresetStore,
    private val galleryRepository: GalleryRepository,
    private val imageTaggingService: ImageTaggingService,
    private val llmRoleService: LlmRoleService,
) {
    private val _uiState = MutableStateFlow(generationSettingsStore.load().toUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var hasAutoLoadedPreset = false
    private var lastProgressStep = 0
    private var lastProgressTime = 0.0
    private var lastProgressPhase = ""
    private var lastProgressStageKey = ""
    private var currentStageStartStep = 0
    private var currentEtaPhaseStartStep = 0
    private var autoTaggingJob: Job? = null
    private var pendingAutoTagImageCount = 0
    private val recentStepTimes = ArrayDeque<Double>()

    init {
        reloadPresets()
        scope.launch {
            backendManager.state.collectLatest { state ->
                if (state.status == BackendStatus.Ready) {
                    update { copy(message = "Image worker ready.") }
                    loadSamplerOptions(state.baseUrl)
                    if (!hasAutoLoadedPreset) {
                        hasAutoLoadedPreset = true
                        loadSelectedPreset()
                    }
                } else {
                    hasAutoLoadedPreset = false
                    update {
                        copy(
                            loadedPresetId = "",
                            presetLoadFailed = false,
                            progressStep = 0,
                            progressSteps = 0,
                            progressTime = 0.0,
                            progressEtaSeconds = 0,
                            progressPhase = "",
                            progressMessage = "",
                            progressStages = emptyList(),
                        )
                    }
                }
            }
        }
    }

    fun updatePresetId(value: String) {
        val preset = _uiState.value.presets.firstOrNull { it.id == value }
        update {
            if (preset == null) {
                copy(selectedPresetId = value)
            } else {
                copy(
                    selectedPresetId = value,
                    message = "Selected ${preset.name}.",
                    presetLoadFailed = false,
                    ideogram = ideogram.copy(selectedTab = preset.promptMode.toIdeogramTab()),
                    error = null,
                )
            }
        }
        if (preset != null) {
            presetStore.saveLastPresetId(preset.id)
        }
    }

    fun updatePrompt(value: String) = update { copy(prompt = value) }
    fun commitPrompt() = update {
        if (promptHistory.getOrNull(promptHistoryIndex) == prompt) {
            this
        } else {
            val retainedHistory = promptHistory.take(promptHistoryIndex + 1)
            val nextHistory = retainedHistory + prompt
            copy(
                promptHistory = nextHistory,
                promptHistoryIndex = nextHistory.lastIndex,
            )
        }
    }

    fun undoPrompt() = update {
        if (!canUndoPrompt) {
            this
        } else {
            val nextIndex = promptHistoryIndex - 1
            copy(
                prompt = promptHistory[nextIndex],
                promptHistoryIndex = nextIndex,
            )
        }
    }

    fun redoPrompt() = update {
        if (!canRedoPrompt) {
            this
        } else {
            val nextIndex = promptHistoryIndex + 1
            copy(
                prompt = promptHistory[nextIndex],
                promptHistoryIndex = nextIndex,
            )
        }
    }

    fun updateNegativePrompt(value: String) = update { copy(negativePrompt = value) }
    fun updateWidth(value: String) = update { copy(width = value) }
    fun updateHeight(value: String) = update { copy(height = value) }
    fun updateSteps(value: String) = update { copy(steps = value) }
    fun updateCfgScale(value: String) = update { copy(cfgScale = value) }
    fun updateSeed(value: String) = update { copy(seed = value) }
    fun updateBatchCount(value: String) = update { copy(batchCount = value) }
    fun updateSampler(value: String) = update { copy(sampler = value) }
    fun updateLeftPanelWidth(value: Int) = update { copy(leftPanelWidthDp = value.coerceIn(MIN_LEFT_PANEL_WIDTH_DP, MAX_LEFT_PANEL_WIDTH_DP)) }
    fun toggleEndless() = update { copy(isEndless = !isEndless) }

    fun updateIdeogramJsonPrompt(value: String) = update {
        val validation = validateIdeogramJson(value)
        copy(
            ideogram = ideogram.copy(
                jsonPrompt = value,
                jsonStatus = validation.first,
                jsonError = validation.second,
            ),
        )
    }

    fun selectIdeogramTab(tab: IdeogramStructureTab) = update {
        copy(ideogram = ideogram.copy(selectedTab = tab))
    }

    fun formatIdeogramJsonPrompt() = update {
        val formatted = runCatching {
            val normalized = normalizeIdeogramJsonPrompt(ideogram.jsonPrompt) ?: ideogram.jsonPrompt
            ideogramJsonPretty.encodeToString(JsonElement.serializer(), ideogramJson.parseToJsonElement(normalized))
        }.getOrNull()
        if (formatted == null) {
            copy(ideogram = ideogram.copy(jsonError = "JSON is invalid and cannot be formatted."))
        } else {
            val validation = validateIdeogramJson(formatted)
            copy(
                ideogram = ideogram.copy(
                    jsonPrompt = formatted,
                    jsonStatus = validation.first,
                    jsonError = validation.second,
                ),
            )
        }
    }

    fun generateIdeogramJsonPrompt() {
        scope.launch {
            val current = _uiState.value
            val prompt = current.prompt.trim()
            if (prompt.isBlank()) {
                update { copy(ideogram = ideogram.copy(jsonError = "Prompt is required before JSON generation.")) }
                return@launch
            }
            val negativePrompt = current.negativePrompt.trim()
            val jsonSourcePrompt = buildString {
                append(prompt)
                if (negativePrompt.isNotBlank()) {
                    append("\n\nAvoid: ")
                    append(negativePrompt)
                }
            }

            val width = current.width.toIntOrNull() ?: 1024
            val height = current.height.toIntOrNull() ?: 1024
            update {
                copy(
                    ideogram = ideogram.copy(
                        isGeneratingJson = true,
                        jsonStatus = "Generating JSON prompt...",
                        jsonError = null,
                    ),
                )
            }

            val settings = settingsStore.load()
            val presets = llmPresetStore.load()
            val roles = llmPresetStore.loadRoles()
            llmRoleService.generateIdeogramJsonPrompt(settings, presets, roles, jsonSourcePrompt, width, height)
                .onSuccess { response ->
                    val jsonText = extractJsonObject(response)
                    val formatted = jsonText?.let {
                        runCatching {
                            val normalized = normalizeIdeogramJsonPrompt(it) ?: it
                            ideogramJsonPretty.encodeToString(JsonElement.serializer(), ideogramJson.parseToJsonElement(normalized))
                        }.getOrNull()
                    }
                    if (formatted == null) {
                        update {
                            copy(
                                ideogram = ideogram.copy(
                                    isGeneratingJson = false,
                                    jsonError = "LLM response did not contain a valid JSON object.",
                                ),
                            )
                        }
                    } else {
                        val validation = validateIdeogramJson(formatted)
                        update {
                            copy(
                                ideogram = ideogram.copy(
                                    jsonPrompt = formatted,
                                    isGeneratingJson = false,
                                    jsonStatus = validation.first,
                                    jsonError = validation.second,
                                    selectedTab = IdeogramStructureTab.Json,
                                ),
                            )
                        }
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            ideogram = ideogram.copy(
                                isGeneratingJson = false,
                                jsonError = error.message ?: "Ideogram JSON generation failed.",
                            ),
                        )
                    }
                }
        }
    }

    fun randomizeSeed() = update { copy(seed = "-1") }

    fun reuseLastSeed() {
        val usedSeed = _uiState.value.history.lastOrNull { it.usedSeed != null }?.usedSeed ?: return
        update { copy(seed = usedSeed.toString()) }
    }

    fun swapDimensions() = update { copy(width = height, height = width) }

    fun applyAspectRatio(widthRatio: Int, heightRatio: Int) {
        val multiplier = kotlin.math.ceil(maxOf(4.0 / widthRatio, 4.0 / heightRatio)).toInt()
        update {
            copy(
                width = (widthRatio * multiplier * RESOLUTION_STEP).toString(),
                height = (heightRatio * multiplier * RESOLUTION_STEP).toString(),
            )
        }
    }

    fun scaleResolution(multiplier: Int) {
        val widthValue = _uiState.value.width.toIntOrNull() ?: return
        val heightValue = _uiState.value.height.toIntOrNull() ?: return
        val baseWidthUnits = (widthValue / RESOLUTION_STEP).coerceAtLeast(1)
        val baseHeightUnits = (heightValue / RESOLUTION_STEP).coerceAtLeast(1)
        val divisor = gcd(baseWidthUnits, baseHeightUnits).coerceAtLeast(1)
        val ratioWidthUnits = baseWidthUnits / divisor
        val ratioHeightUnits = baseHeightUnits / divisor
        update {
            copy(
                width = (ratioWidthUnits * multiplier * RESOLUTION_STEP).coerceIn(MIN_RESOLUTION, MAX_RESOLUTION).toString(),
                height = (ratioHeightUnits * multiplier * RESOLUTION_STEP).coerceIn(MIN_RESOLUTION, MAX_RESOLUTION).toString(),
            )
        }
    }

    fun resetToPresetDefaults() {
        val preset = selectedPresetOrNull() ?: return
        update {
            copy(
                width = preset.defaultWidth.toString(),
                height = preset.defaultHeight.toString(),
                steps = preset.defaultSteps.toString(),
                cfgScale = preset.defaultCfgScale.toString(),
                sampler = resolveSamplerOption(preset.defaultSampler, samplerOptions) ?: sampler,
                negativePrompt = preset.defaultNegativePrompt,
                message = "Reset generation settings to ${preset.name}.",
                error = null,
            )
        }
    }

    fun enhancePrompt() {
        scope.launch {
            val currentPrompt = _uiState.value.prompt.trim()
            if (currentPrompt.isBlank()) {
                update { copy(error = "Prompt is required before enhancement.") }
                return@launch
            }

            update {
                copy(
                    isEnhancingPrompt = true,
                    message = "Enhancing prompt...",
                    error = null,
                )
            }

            val settings = settingsStore.load()
            val presets = llmPresetStore.load()
            val roles = llmPresetStore.loadRoles()
            llmRoleService.enhancePrompt(settings, presets, roles, currentPrompt)
                .onSuccess { enhanced ->
                    val normalized = enhanced.trim().trim('"')
                    if (normalized.isBlank()) {
                        update {
                            copy(
                                isEnhancingPrompt = false,
                                error = "Prompt enhancer returned an empty prompt.",
                            )
                        }
                    } else {
                        update {
                            copy(
                                prompt = normalized,
                                isEnhancingPrompt = false,
                                message = "Prompt enhanced.",
                                error = null,
                            )
                        }
                        commitPrompt()
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            isEnhancingPrompt = false,
                            error = error.message ?: "Prompt enhancement failed.",
                        )
                    }
                }
        }
    }

    fun reuseGalleryParams(params: GalleryReusableParams) {
        update {
            copy(
                prompt = params.prompt.ifBlank { prompt },
                negativePrompt = params.negativePrompt,
                width = params.width?.toString() ?: width,
                height = params.height?.toString() ?: height,
                steps = params.steps?.toString() ?: steps,
                cfgScale = params.cfgScale?.toString() ?: cfgScale,
                sampler = resolveSamplerOption(params.sampler, samplerOptions) ?: sampler,
                seed = params.seed?.toString() ?: seed,
                resultUrls = emptyList(),
                images = emptyList(),
                usedSeed = "",
                message = "Reused gallery image settings.",
                error = null,
            )
        }
        commitPrompt()
    }

    fun reloadPresets() {
        scope.launch {
            update { copy(isLoadingPresets = true, error = null) }
            runCatching { presetStore.load() }
                .onSuccess { presets ->
                    val currentPresetId = _uiState.value.selectedPresetId
                    val lastPresetId = presetStore.loadLastPresetId()
                    val selected = presets.firstOrNull { it.id == currentPresetId }
                        ?: presets.firstOrNull { it.id == lastPresetId }
                        ?: presets.firstOrNull()
                    update {
                        copy(
                            presets = presets,
                            selectedPresetId = selected?.id.orEmpty(),
                            loadedPresetId = loadedPresetId.takeIf { loadedPresetId -> presets.any { it.id == loadedPresetId } }.orEmpty(),
                            isLoadingPresets = false,
                            message = "Loaded ${presets.size} image presets from ${presetStore.presetDir.absolutePath}.",
                            error = null,
                        )
                    }
                    selected?.let {
                        updatePresetId(it.id)
                        if (backendManager.state.value.status == BackendStatus.Ready && _uiState.value.loadedPresetId != it.id) {
                            loadSelectedPreset()
                        }
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            isLoadingPresets = false,
                            error = error.message ?: "Failed to load image presets.",
                        )
                    }
                }
        }
    }

    private suspend fun loadSamplerOptions(baseUrl: String) {
        client.fetchSamplerOptions(baseUrl)
            .onSuccess { options ->
                if (options.isNotEmpty()) {
                    update {
                        copy(
                            samplerOptions = options,
                            sampler = resolveSamplerOption(sampler, options) ?: options.first(),
                        )
                    }
                }
            }
    }

    private fun resolveSamplerOption(value: String, options: List<String>): String? {
        val normalized = value.trim()
        if (normalized in options) return normalized
        val legacyDpmpp = when (normalized) {
            "dpmpp_2s_a" -> "dpm++2s_a"
            "dpmpp_2m" -> "dpm++2m"
            "dpmpp_2mv2" -> "dpm++2mv2"
            else -> ""
        }
        return legacyDpmpp.takeIf { it in options }
    }

    fun selectAndLoadPreset(value: String) {
        updatePresetId(value)
        loadSelectedPreset()
    }

    fun loadSelectedPreset() {
        scope.launch {
            ensureSelectedPresetLoaded()
        }
    }

    fun generate(saveImagesAutomatically: Boolean) {
        scope.launch {
            val promptMode = selectedPresetOrNull()?.promptMode ?: ImagePromptMode.Text
            if (promptMode == ImagePromptMode.Text) {
                commitPrompt()
            }

            if (backendManager.state.value.status != BackendStatus.Ready) {
                update { copy(error = "Image worker is not ready.") }
                return@launch
            }
            if (!ensureSelectedPresetLoaded()) {
                return@launch
            }

            val params = if (promptMode == ImagePromptMode.Text) {
                runCatching { buildParams() }
                    .onFailure { error -> update { copy(error = error.message ?: "Invalid generation parameters.") } }
                    .getOrNull()
            } else {
                buildStructuredParamsOrNull()
            }
            params ?: return@launch

            enqueueGeneration(params.copy(saveImage = saveImagesAutomatically), promptMode)
        }
    }

    private fun buildStructuredParamsOrNull(): GenerationParams? {
        val jsonPrompt = _uiState.value.ideogram.jsonPrompt.trim()
        val validation = validateIdeogramJson(jsonPrompt)
        if (validation.second != null) {
            update {
                copy(
                    ideogram = ideogram.copy(
                        selectedTab = IdeogramStructureTab.Json,
                        jsonStatus = validation.first,
                        jsonError = validation.second,
                    ),
                    error = validation.second,
                )
            }
            return null
        }

        val prompt = runCatching {
            ideogramJson.encodeToString(JsonElement.serializer(), ideogramJson.parseToJsonElement(jsonPrompt))
        }.getOrElse {
            update {
                copy(
                    ideogram = ideogram.copy(selectedTab = IdeogramStructureTab.Json, jsonError = "JSON is invalid."),
                    error = "JSON is invalid.",
                )
            }
            return null
        }

        return runCatching { buildParams(promptOverride = prompt, negativePromptOverride = "") }
                .onFailure { error -> update { copy(error = error.message ?: "Invalid generation parameters.") } }
                .getOrNull()
    }

    private suspend fun ensureSelectedPresetLoaded(): Boolean {
        if (backendManager.state.value.status != BackendStatus.Ready) {
            update {
                copy(
                    presetLoadFailed = true,
                    error = "Image worker is not ready.",
                )
            }
            return false
        }

        val preset = selectedPresetOrNull()
        if (preset == null) {
            update {
                copy(
                    presetLoadFailed = true,
                    error = "Select an image preset first.",
                )
            }
            return false
        }

        if (_uiState.value.loadedPresetId == preset.id && !_uiState.value.presetLoadFailed) {
            return true
        }

        update {
            copy(
                isLoadingPreset = true,
                presetLoadFailed = false,
                message = "Loading ${preset.name}...",
                error = null,
            )
        }

        val result = client.loadPreset(backendManager.state.value.baseUrl, preset)
        result.onSuccess {
            update {
                copy(
                    loadedPresetId = preset.id,
                    isLoadingPreset = false,
                    presetLoadFailed = false,
                    message = "Loaded ${preset.name}.",
                    error = null,
                )
            }
        }.onFailure { error ->
            update {
                copy(
                    isLoadingPreset = false,
                    presetLoadFailed = true,
                    error = error.message ?: "Failed to load preset.",
                )
            }
        }
        return result.isSuccess
    }

    private fun enqueueGeneration(params: GenerationParams, promptMode: ImagePromptMode) {
        val item = GenerationHistoryItem(
            id = UUID.randomUUID().toString(),
            status = GenerationStatus.Pending,
            params = params,
            promptMode = promptMode,
        )

        var shouldSelectNewItem = false
        update {
            shouldSelectNewItem = historyIndex == -1 || historyIndex == history.lastIndex
            val nextHistory = history + item
            copy(
                history = nextHistory,
                historyIndex = if (shouldSelectNewItem) nextHistory.lastIndex else historyIndex,
                message = "Queued generation.",
                error = null,
            )
        }
        if (shouldSelectNewItem) {
            seekHistory(_uiState.value.history.lastIndex)
        }

        processQueue()
    }

    fun goBack() {
        if (_uiState.value.canGoBack) {
            seekHistory(_uiState.value.historyIndex - 1)
        }
    }

    fun goForward() {
        if (_uiState.value.canGoForward) {
            seekHistory(_uiState.value.historyIndex + 1)
        }
    }

    fun seekHistory(index: Int) {
        val item = _uiState.value.history.getOrNull(index) ?: return
        val validation = if (item.promptMode == ImagePromptMode.Text) null else validateIdeogramJson(item.params.prompt)
        update {
            val nextIdeogram = if (item.promptMode == ImagePromptMode.Text) {
                ideogram.copy(selectedTab = IdeogramStructureTab.Text)
            } else {
                ideogram.copy(
                    jsonPrompt = item.params.prompt,
                    selectedTab = IdeogramStructureTab.Json,
                    jsonStatus = validation?.first ?: ideogram.jsonStatus,
                    jsonError = validation?.second,
                )
            }
            copy(
                historyIndex = index,
                prompt = if (item.promptMode == ImagePromptMode.Text) item.params.prompt else prompt,
                negativePrompt = if (item.promptMode == ImagePromptMode.Text) item.params.negativePrompt else negativePrompt,
                ideogram = nextIdeogram,
                width = item.params.width.toString(),
                height = item.params.height.toString(),
                steps = item.params.steps.toString(),
                cfgScale = item.params.cfgScale.toString(),
                seed = item.params.seed.toString(),
                batchCount = item.params.batchCount.toString(),
                sampler = item.params.sampler,
                resultUrls = item.imageUrls,
                usedSeed = item.usedSeed?.toString().orEmpty(),
                images = item.images,
                error = item.error,
                message = when (item.status) {
                    GenerationStatus.Pending -> "Queued."
                    GenerationStatus.Processing -> "Generating..."
                    GenerationStatus.Completed -> "Image generated successfully."
                    GenerationStatus.Failed -> "Generation failed."
                },
            )
        }
    }

    private fun processQueue() {
        if (_uiState.value.isGenerating) {
            return
        }

        val nextIndex = _uiState.value.history.indexOfFirst { it.status == GenerationStatus.Pending }
        if (nextIndex < 0) {
            return
        }

        scope.launch {
            val item = _uiState.value.history.getOrNull(nextIndex) ?: return@launch
            val startedAt = System.currentTimeMillis()
            resetProgressEstimator()

            updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Processing, error = null) }
            update {
                copy(
                    isGenerating = true,
                    progressStep = 0,
                    progressSteps = 0,
                    progressTime = 0.0,
                    progressEtaSeconds = 0,
                    progressPhase = "Starting...",
                    progressMessage = "",
                    progressStages = initialProgressStages(),
                    message = "Generating...",
                    error = null,
                )
            }

            if (_uiState.value.historyIndex == -1 || _uiState.value.historyIndex >= nextIndex - 1) {
                seekHistory(nextIndex)
            }

            val baseUrl = backendManager.state.value.baseUrl
            val jobSubmission = client.submitGenerationJob(baseUrl, item.params.toRequest())

            val generationResult = jobSubmission.mapCatching { submission ->
                update { copy(message = "Queued worker job ${submission.id}.") }
                client.streamGenerationJobEvents(baseUrl, submission.id) { event ->
                    when (event) {
                        is GenerationJobEvent.Queued -> update {
                            copy(message = if (event.queuePosition > 0) "Queued worker job (${event.queuePosition})." else "Queued worker job.")
                        }
                        is GenerationJobEvent.Started -> update {
                            copy(message = "Generating...")
                        }
                        is GenerationJobEvent.Progress -> handleProgressEvent(event)
                        is GenerationJobEvent.Completed -> Unit
                        is GenerationJobEvent.Failed -> error(event.message)
                        is GenerationJobEvent.Cancelled -> error(event.message)
                    }
                }.getOrThrow()
            }

            generationResult.onSuccess { result ->
                update {
                    copy(
                        progressEtaSeconds = 0,
                        progressStages = progressStages.markAllComplete(),
                    )
                }
                val bitmapResult = client.fetchGeneratedImages(backendManager.state.value.baseUrl, result.imageUrls)
                bitmapResult.onSuccess { images ->
                    val generationTime = result.generationTime ?: ((System.currentTimeMillis() - startedAt) / 1000.0)
                    updateHistoryItem(nextIndex) {
                        it.copy(
                            status = GenerationStatus.Completed,
                            imageUrls = result.imageUrls,
                            usedSeed = result.usedSeed,
                            images = images,
                            generationTime = generationTime,
                        )
                    }
                    if (_uiState.value.historyIndex == nextIndex) {
                        update {
                            copy(
                                resultUrls = result.imageUrls,
                                usedSeed = result.usedSeed.toString(),
                                images = images,
                                message = "Image generated successfully.",
                                error = null,
                            )
                        }
                    }
                    if (item.params.saveImage && result.imageUrls.isNotEmpty()) {
                        scheduleAutoTaggingAfterGeneration(result.imageUrls.size)
                    }
                }.onFailure { error ->
                    resetProgressEstimator()
                    val message = error.message ?: "Failed to load generated image."
                    updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Failed, error = message) }
                    if (_uiState.value.historyIndex == nextIndex) {
                        update { copy(images = emptyList(), progressEtaSeconds = 0, error = message) }
                    }
                }
            }.onFailure { error ->
                resetProgressEstimator()
                val message = error.message ?: "Generation failed."
                updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Failed, error = message) }
                if (_uiState.value.historyIndex == nextIndex) {
                    update { copy(images = emptyList(), progressEtaSeconds = 0, error = message) }
                }
            }

            update { copy(isGenerating = false) }

            if (_uiState.value.isEndless) {
                generate(item.params.saveImage)
            }
            processQueue()
        }
    }

    private fun buildParams(promptOverride: String? = null, negativePromptOverride: String? = null): GenerationParams {
        val state = _uiState.value
        val promptValue = promptOverride ?: state.prompt
        require(promptValue.isNotBlank()) { "Prompt is required." }
        return GenerationParams(
            prompt = promptValue,
            negativePrompt = negativePromptOverride ?: state.negativePrompt,
            width = state.width.toIntOrNull() ?: error("Width must be numeric."),
            height = state.height.toIntOrNull() ?: error("Height must be numeric."),
            steps = state.steps.toIntOrNull() ?: error("Steps must be numeric."),
            cfgScale = state.cfgScale.toDoubleOrNull() ?: error("CFG scale must be numeric."),
            seed = state.seed.toIntOrNull() ?: error("Seed must be numeric."),
            batchCount = state.batchCount.toIntOrNull()?.coerceIn(MIN_BATCH_COUNT, MAX_BATCH_COUNT) ?: error("Batch must be numeric."),
            sampler = state.sampler.trim(),
        )
    }

    private fun GenerationParams.toRequest() = GenerationRequest(
        modelId = "",
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        steps = steps,
        cfgScale = cfgScale,
        seed = seed,
        batchCount = batchCount,
        sampler = sampler,
        saveImage = saveImage,
    )

    private fun scheduleAutoTaggingAfterGeneration(generatedImageCount: Int) {
        pendingAutoTagImageCount += generatedImageCount.coerceAtLeast(1)
        if (autoTaggingJob?.isActive == true) {
            return
        }

        autoTaggingJob = scope.launch {
            while (pendingAutoTagImageCount > 0) {
                val batchSize = pendingAutoTagImageCount
                pendingAutoTagImageCount = 0
                runAutoTaggingAfterGeneration(batchSize)
            }
            autoTaggingJob = null
        }
    }

    private suspend fun runAutoTaggingAfterGeneration(generatedImageCount: Int) {
        val settings = settingsStore.load()
        val presets = llmPresetStore.load()
        val roles = llmPresetStore.loadRoles()
        val preset = presets.firstOrNull { it.id == roles.taggingPresetId } ?: return
        if (!_uiState.value.isGenerating) {
            update { copy(message = "Image generated successfully. Tagging new image...") }
        }
        withContext(Dispatchers.IO) {
            galleryRepository.indexOutputDirectory(settings.outputDir)
        }
        val result = imageTaggingService.tagPendingImages(
            settings = settings,
            preset = preset,
            maxItems = generatedImageCount.coerceAtLeast(1),
            refreshOutputIndex = false,
        )
        result.onSuccess { batch ->
            val suffix = when {
                batch.completed > 0 && batch.failed > 0 -> " Tagged ${batch.completed}, ${batch.failed} failed."
                batch.completed > 0 -> " Tagged ${batch.completed}."
                batch.failed > 0 -> " Tagging failed for ${batch.failed}."
                else -> ""
            }
            if (!_uiState.value.isGenerating) {
                update { copy(message = "Image generated successfully.$suffix") }
            }
        }.onFailure { error ->
            if (!_uiState.value.isGenerating) {
                update { copy(message = "Image generated successfully.", error = error.message ?: "Auto-tagging failed.") }
            }
        }
    }

    private fun handleProgressEvent(event: GenerationJobEvent.Progress) {
        update {
            val nextPhase = displayProgressPhase(event.phase, progressPhase)
            val nextStages = updateProgressStages(progressStages, nextPhase, event)
            val timing = estimateProgressTiming(event, nextPhase)
            copy(
                progressStep = event.step,
                progressSteps = event.steps,
                progressTime = timing.elapsedSeconds,
                progressEtaSeconds = timing.etaSeconds,
                progressPhase = nextPhase,
                progressMessage = displayProgressMessage(event.message),
                progressStages = nextStages,
            )
        }
    }

    private fun displayProgressMessage(rawMessage: String): String {
        val normalized = rawMessage.trim()
        return when {
            normalized.equals("High-res: VAE tiling enabled", ignoreCase = true) -> ""
            else -> normalized
        }
    }

    private fun displayProgressPhase(rawPhase: String, currentPhase: String): String {
        val normalized = rawPhase.trim()
        return when {
            normalized.isBlank() || normalized.equals("idle", ignoreCase = true) -> {
                currentPhase.takeUnless { it.isBlank() || it.equals("idle", ignoreCase = true) } ?: "Starting..."
            }
            else -> normalized
        }
    }

    private fun updateProgressStages(
        stages: List<GenerationProgressStage>,
        phase: String,
        event: GenerationJobEvent.Progress,
    ): List<GenerationProgressStage> {
        val stageKey = progressStageKey(phase)
        if (stageKey != lastProgressStageKey) {
            lastProgressStageKey = stageKey
            currentStageStartStep = event.step
        }

        val nextStages = ensureProgressStage(stages.ifEmpty { initialProgressStages() }, stageKey, phase)
        val activeIndex = nextStages.indexOfFirst { it.key == stageKey }.coerceAtLeast(0)

        return nextStages.mapIndexed { index, stage ->
            when {
                index < activeIndex -> stage.copy(progress = 1f, isActive = false, isComplete = true)
                index == activeIndex -> stage.copy(
                    progress = activeStageProgress(stage.key, event),
                    isActive = true,
                    isComplete = false,
                )
                else -> stage.copy(
                    progress = if (stage.isComplete) 1f else stage.progress.coerceIn(0f, 1f),
                    isActive = false,
                    isComplete = stage.isComplete,
                )
            }
        }
    }

    private fun ensureProgressStage(
        stages: List<GenerationProgressStage>,
        stageKey: String,
        phase: String,
    ): List<GenerationProgressStage> {
        if (stages.any { it.key == stageKey }) {
            return stages
        }

        val stage = GenerationProgressStage(
            key = stageKey,
            label = progressStageLabel(stageKey, phase),
            progress = 0f,
            isActive = false,
            isComplete = false,
        )
        val decodeIndex = stages.indexOfFirst { it.key == PROGRESS_STAGE_DECODE }
        return if (decodeIndex >= 0) {
            stages.take(decodeIndex) + stage + stages.drop(decodeIndex)
        } else {
            stages + stage
        }
    }

    private fun activeStageProgress(stageKey: String, event: GenerationJobEvent.Progress): Float {
        return when (stageKey) {
            PROGRESS_STAGE_SAMPLING,
            PROGRESS_STAGE_HIGHRES -> {
                val denominator = (event.steps - currentStageStartStep).coerceAtLeast(1)
                val numerator = (event.step - currentStageStartStep).coerceAtLeast(0)
                (numerator.toFloat() / denominator.toFloat()).coerceIn(0f, 0.98f)
            }
            PROGRESS_STAGE_PREPARE,
            PROGRESS_STAGE_DECODE -> 0.35f
            else -> 0.25f
        }
    }

    private fun progressStageKey(phase: String): String {
        val normalized = phase.lowercase()
        return when {
            normalized.contains("vae") || normalized.contains("decod") || normalized.contains("sav") -> PROGRESS_STAGE_DECODE
            normalized.contains("highres") || normalized.contains("high-res") || normalized.contains("hires") -> PROGRESS_STAGE_HIGHRES
            normalized.contains("sampl") -> PROGRESS_STAGE_SAMPLING
            else -> PROGRESS_STAGE_PREPARE
        }
    }

    private fun progressStageLabel(stageKey: String, phase: String): String {
        return when (stageKey) {
            PROGRESS_STAGE_PREPARE -> "Prepare"
            PROGRESS_STAGE_SAMPLING -> "Sampling"
            PROGRESS_STAGE_HIGHRES -> "Highres-fix"
            PROGRESS_STAGE_DECODE -> "Decode and save"
            else -> phase.ifBlank { "Processing" }.removeSuffix("...")
        }
    }

    private fun initialProgressStages(): List<GenerationProgressStage> = listOf(
        GenerationProgressStage(PROGRESS_STAGE_PREPARE, "Prepare", 0.25f, isActive = true, isComplete = false),
        GenerationProgressStage(PROGRESS_STAGE_SAMPLING, "Sampling", 0f, isActive = false, isComplete = false),
        GenerationProgressStage(PROGRESS_STAGE_DECODE, "Decode and save", 0f, isActive = false, isComplete = false),
    )

    private fun List<GenerationProgressStage>.markAllComplete(): List<GenerationProgressStage> {
        return map { stage ->
            stage.copy(progress = 1f, isActive = false, isComplete = true)
        }
    }

    private fun estimateProgressTiming(event: GenerationJobEvent.Progress, phase: String): ProgressTiming {
        val phaseChanged = phase != lastProgressPhase
        if (phaseChanged) {
            recentStepTimes.clear()
            lastProgressPhase = phase
            currentEtaPhaseStartStep = (event.step - 1).coerceAtLeast(0)
            lastProgressStep = currentEtaPhaseStartStep
            lastProgressTime = 0.0
        }

        val phaseStep = (event.step - currentEtaPhaseStartStep).coerceAtLeast(0)
        val cumulativePhaseTime = if (event.time > 0.0 && phaseStep > 0) {
            event.time * phaseStep
        } else {
            lastProgressTime
        }

        if (event.step > lastProgressStep) {
            val deltaTime = cumulativePhaseTime - lastProgressTime
            val deltaSteps = event.step - lastProgressStep
            val secondsPerStep = deltaTime / deltaSteps
            if (secondsPerStep > 0.0 && secondsPerStep.isFinite()) {
                recentStepTimes.addLast(secondsPerStep)
                while (recentStepTimes.size > RECENT_STEP_TIME_WINDOW) {
                    recentStepTimes.removeFirst()
                }
            }
            lastProgressTime = cumulativePhaseTime
            lastProgressStep = event.step
        } else if (event.step < lastProgressStep || cumulativePhaseTime < lastProgressTime) {
            recentStepTimes.clear()
            currentEtaPhaseStartStep = (event.step - 1).coerceAtLeast(0)
            lastProgressTime = 0.0
            lastProgressStep = event.step
        }

        if (event.steps <= 0 || event.step <= 0) {
            return ProgressTiming(elapsedSeconds = cumulativePhaseTime.coerceAtLeast(0.0), etaSeconds = 0)
        }

        val secondsPerStep = if (recentStepTimes.isNotEmpty()) {
            recentStepTimes.average()
        } else if (event.time > 0.0) {
            event.time
        } else {
            cumulativePhaseTime / phaseStep.coerceAtLeast(1)
        }
        val remainingSteps = (event.steps - event.step).coerceAtLeast(0)
        val etaSeconds = (secondsPerStep * remainingSteps).roundToInt().coerceAtLeast(0)
        return ProgressTiming(
            elapsedSeconds = cumulativePhaseTime.coerceAtLeast(0.0),
            etaSeconds = etaSeconds,
        )
    }

    private fun resetProgressEstimator() {
        lastProgressStep = 0
        lastProgressTime = 0.0
        lastProgressPhase = ""
        lastProgressStageKey = ""
        currentStageStartStep = 0
        currentEtaPhaseStartStep = 0
        recentStepTimes.clear()
    }

    private fun updateHistoryItem(index: Int, transform: (GenerationHistoryItem) -> GenerationHistoryItem) {
        update {
            val nextHistory = history.mapIndexed { itemIndex, item ->
                if (itemIndex == index) transform(item) else item
            }
            copy(history = nextHistory)
        }
    }

    private fun selectedPresetOrNull(): ImagePreset? {
        val state = _uiState.value
        return state.presets.firstOrNull { it.id == state.selectedPresetId }
    }

    private fun update(transform: GenerationUiState.() -> GenerationUiState) {
        _uiState.value = _uiState.value.transform()
        generationSettingsStore.save(_uiState.value.toSavedSettings())
    }

    private fun SavedGenerationSettings.toUiState() = GenerationUiState(
        prompt = prompt,
        promptHistory = listOf(prompt),
        promptHistoryIndex = 0,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        steps = steps,
        cfgScale = cfgScale,
        seed = seed,
        batchCount = batchCount,
        sampler = sampler,
        leftPanelWidthDp = leftPanelWidthDp,
    )

    private fun GenerationUiState.toSavedSettings() = SavedGenerationSettings(
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        steps = steps,
        cfgScale = cfgScale,
        seed = seed,
        batchCount = batchCount,
        sampler = sampler,
        leftPanelWidthDp = leftPanelWidthDp,
    )

    private fun gcd(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

    companion object {
        const val MIN_RESOLUTION = 64
        const val MAX_RESOLUTION = 4096
        const val RESOLUTION_STEP = 16
        const val MIN_BATCH_COUNT = 1
        const val MAX_BATCH_COUNT = 8
        const val MIN_LEFT_PANEL_WIDTH_DP = 480
        const val MAX_LEFT_PANEL_WIDTH_DP = 900
        private const val RECENT_STEP_TIME_WINDOW = 5
        private const val PROGRESS_STAGE_PREPARE = "prepare"
        private const val PROGRESS_STAGE_SAMPLING = "sampling"
        private const val PROGRESS_STAGE_HIGHRES = "highres"
        private const val PROGRESS_STAGE_DECODE = "decode"
    }
}
