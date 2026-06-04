package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.GenerationJobEvent
import com.diffusiondesk.desktop.core.GenerationRequest
import com.diffusiondesk.desktop.core.GenerationSettingsStore
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.SavedGenerationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

enum class GenerationStatus {
    Pending,
    Processing,
    Completed,
    Failed,
}

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
    val imageUrls: List<String> = emptyList(),
    val usedSeed: Int? = null,
    val images: List<GeneratedImage> = emptyList(),
    val error: String? = null,
    val generationTime: Double? = null,
)

data class GenerationUiState(
    val selectedPresetId: String = "",
    val prompt: String = "A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night.",
    val negativePrompt: String = "deformed, blurry, low quality, watermark",
    val width: String = "1024",
    val height: String = "768",
    val steps: String = "4",
    val cfgScale: String = "1.0",
    val seed: String = "-1",
    val batchCount: String = "1",
    val sampler: String = "euler_a",
    val leftPanelWidthDp: Int = 560,
    val presets: List<ImagePreset> = emptyList(),
    val isLoadingPresets: Boolean = false,
    val isLoadingPreset: Boolean = false,
    val progressStep: Int = 0,
    val progressSteps: Int = 0,
    val progressTime: Double = 0.0,
    val progressPhase: String = "",
    val progressMessage: String = "",
    val isGenerating: Boolean = false,
    val resultUrls: List<String> = emptyList(),
    val usedSeed: String = "",
    val images: List<GeneratedImage> = emptyList(),
    val history: List<GenerationHistoryItem> = emptyList(),
    val historyIndex: Int = -1,
    val isEndless: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val queueCount: Int get() = history.count { it.status == GenerationStatus.Pending }
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex >= 0 && historyIndex < history.lastIndex
    val currentHistoryItem: GenerationHistoryItem? get() = history.getOrNull(historyIndex)
}

class GenerationViewModel(
    private val scope: CoroutineScope,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
    private val presetStore: ImagePresetStore,
    private val generationSettingsStore: GenerationSettingsStore,
) {
    private val _uiState = MutableStateFlow(generationSettingsStore.load().toUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var hasAutoLoadedPreset = false

    val samplers = listOf("euler", "euler_a", "heun", "dpm2", "dpmpp_2s_a", "dpmpp_2m", "dpmpp_2mv2", "ipndm", "ipndm_v", "lcm", "ddim_trailing", "tcd")

    init {
        reloadPresets()
        scope.launch {
            backendManager.state.collectLatest { state ->
                if (state.status == BackendStatus.Ready) {
                    update { copy(message = "Image worker ready.") }
                    if (!hasAutoLoadedPreset) {
                        hasAutoLoadedPreset = true
                        loadSelectedPreset()
                    }
                } else {
                    update {
                        copy(
                            progressStep = 0,
                            progressSteps = 0,
                            progressTime = 0.0,
                            progressPhase = "",
                            progressMessage = "",
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
                    width = preset.defaultWidth.toString(),
                    height = preset.defaultHeight.toString(),
                    steps = preset.defaultSteps.toString(),
                    cfgScale = preset.defaultCfgScale.toString(),
                    sampler = preset.defaultSampler,
                    negativePrompt = preset.defaultNegativePrompt,
                    message = "Selected ${preset.name}.",
                    error = null,
                )
            }
        }
        if (preset != null) {
            presetStore.saveLastPresetId(preset.id)
        }
    }

    fun updatePrompt(value: String) = update { copy(prompt = value) }
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
                sampler = preset.defaultSampler,
                negativePrompt = preset.defaultNegativePrompt,
                message = "Reset generation settings to ${preset.name}.",
                error = null,
            )
        }
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
                            isLoadingPresets = false,
                            message = "Loaded ${presets.size} image presets from ${presetStore.presetDir.absolutePath}.",
                            error = null,
                        )
                    }
                    selected?.let { updatePresetId(it.id) }
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

    fun loadSelectedPreset() {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                update { copy(error = "Image worker is not ready.") }
                return@launch
            }

            val preset = selectedPresetOrNull()
            if (preset == null) {
                update { copy(error = "Select an image preset first.") }
                return@launch
            }

            update {
                copy(
                    isLoadingPreset = true,
                    message = "Loading ${preset.name}...",
                    error = null,
                )
            }

            val result = client.loadPreset(backendManager.state.value.baseUrl, preset)
            result.onSuccess {
                update {
                    copy(
                        isLoadingPreset = false,
                        message = "Loaded ${preset.name}.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                update {
                    copy(
                        isLoadingPreset = false,
                        error = error.message ?: "Failed to load preset.",
                    )
                }
            }
        }
    }

    fun generate(saveImagesAutomatically: Boolean) {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                update { copy(error = "Image worker is not ready.") }
                return@launch
            }

            val params = runCatching { buildParams() }
                .onFailure { error -> update { copy(error = error.message ?: "Invalid generation parameters.") } }
                .getOrNull() ?: return@launch

            val item = GenerationHistoryItem(
                id = UUID.randomUUID().toString(),
                status = GenerationStatus.Pending,
                params = params.copy(saveImage = saveImagesAutomatically),
            )

            var shouldSelectNewItem = false
            update {
                shouldSelectNewItem = historyIndex == -1 || historyIndex == history.lastIndex
                val nextHistory = history + item
                copy(
                    history = nextHistory,
                    historyIndex = if (shouldSelectNewItem) nextHistory.lastIndex else historyIndex,
                    message = if (isGenerating) "Queued generation." else "Queued generation.",
                    error = null,
                )
            }
            if (shouldSelectNewItem) {
                seekHistory(_uiState.value.history.lastIndex)
            }

            processQueue()
        }
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
        update {
            copy(
                historyIndex = index,
                prompt = item.params.prompt,
                negativePrompt = item.params.negativePrompt,
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

            updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Processing, error = null) }
            update {
                copy(
                    isGenerating = true,
                    progressStep = 0,
                    progressSteps = 0,
                    progressTime = 0.0,
                    progressPhase = "Starting...",
                    progressMessage = "",
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
                        is GenerationJobEvent.Progress -> update {
                            copy(
                                progressStep = event.step,
                                progressSteps = event.steps,
                                progressTime = event.time,
                                progressPhase = event.phase,
                                progressMessage = event.message,
                            )
                        }
                        is GenerationJobEvent.Completed -> Unit
                        is GenerationJobEvent.Failed -> error(event.message)
                        is GenerationJobEvent.Cancelled -> error(event.message)
                    }
                }.getOrThrow()
            }

            generationResult.onSuccess { result ->
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
                }.onFailure { error ->
                    val message = error.message ?: "Failed to load generated image."
                    updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Failed, error = message) }
                    if (_uiState.value.historyIndex == nextIndex) {
                        update { copy(images = emptyList(), error = message) }
                    }
                }
            }.onFailure { error ->
                val message = error.message ?: "Generation failed."
                updateHistoryItem(nextIndex) { it.copy(status = GenerationStatus.Failed, error = message) }
                if (_uiState.value.historyIndex == nextIndex) {
                    update { copy(images = emptyList(), error = message) }
                }
            }

            update { copy(isGenerating = false) }

            if (_uiState.value.isEndless) {
                generate(item.params.saveImage)
            }
            processQueue()
        }
    }

    private fun buildParams(): GenerationParams {
        val state = _uiState.value
        require(state.prompt.isNotBlank()) { "Prompt is required." }
        return GenerationParams(
            prompt = state.prompt,
            negativePrompt = state.negativePrompt,
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
        const val MIN_LEFT_PANEL_WIDTH_DP = 380
        const val MAX_LEFT_PANEL_WIDTH_DP = 900
    }
}
