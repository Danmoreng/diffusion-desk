package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.GenerationJobEvent
import com.diffusiondesk.desktop.core.GenerationRequest
import com.diffusiondesk.desktop.core.GenerationSettingsStore
import com.diffusiondesk.desktop.core.GalleryReusableParams
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
import kotlin.math.roundToInt

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

data class GenerationProgressStage(
    val key: String,
    val label: String,
    val progress: Float,
    val isActive: Boolean,
    val isComplete: Boolean,
)

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
) {
    private val _uiState = MutableStateFlow(generationSettingsStore.load().toUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var hasAutoLoadedPreset = false
    private var lastProgressStep = 0
    private var lastProgressTime = 0.0
    private var lastProgressPhase = ""
    private var lastProgressStageKey = ""
    private var currentStageStartStep = 0
    private val recentStepTimes = ArrayDeque<Double>()

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
                    width = preset.defaultWidth.toString(),
                    height = preset.defaultHeight.toString(),
                    steps = preset.defaultSteps.toString(),
                    cfgScale = preset.defaultCfgScale.toString(),
                    sampler = preset.defaultSampler,
                    negativePrompt = preset.defaultNegativePrompt,
                    message = "Selected ${preset.name}.",
                    presetLoadFailed = false,
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

    fun reuseGalleryParams(params: GalleryReusableParams) {
        val matchingPreset = findPresetForReusableParams(params)
        update {
            copy(
                selectedPresetId = matchingPreset?.id ?: selectedPresetId,
                prompt = params.prompt.ifBlank { prompt },
                negativePrompt = params.negativePrompt,
                width = params.width?.toString() ?: width,
                height = params.height?.toString() ?: height,
                steps = params.steps?.toString() ?: steps,
                cfgScale = params.cfgScale?.toString() ?: cfgScale,
                sampler = params.sampler.takeIf { it in samplers } ?: sampler,
                seed = params.seed?.toString() ?: seed,
                resultUrls = emptyList(),
                images = emptyList(),
                usedSeed = "",
                message = if (matchingPreset != null) "Reused gallery image settings with ${matchingPreset.name}." else "Reused gallery image settings.",
                error = null,
            )
        }
        commitPrompt()
        matchingPreset?.let {
            presetStore.saveLastPresetId(it.id)
            if (backendManager.state.value.status == BackendStatus.Ready && _uiState.value.loadedPresetId != it.id) {
                loadSelectedPreset()
            }
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
                            loadedPresetId = loadedPresetId.takeIf { loadedPresetId -> presets.any { it.id == loadedPresetId } }.orEmpty(),
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

    fun selectAndLoadPreset(value: String) {
        updatePresetId(value)
        loadSelectedPreset()
    }

    fun loadSelectedPreset() {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                update {
                    copy(
                        presetLoadFailed = true,
                        error = "Image worker is not ready.",
                    )
                }
                return@launch
            }

            val preset = selectedPresetOrNull()
            if (preset == null) {
                update {
                    copy(
                        presetLoadFailed = true,
                        error = "Select an image preset first.",
                    )
                }
                return@launch
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
        }
    }

    fun generate(saveImagesAutomatically: Boolean) {
        scope.launch {
            commitPrompt()

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

    private fun handleProgressEvent(event: GenerationJobEvent.Progress) {
        update {
            val nextPhase = displayProgressPhase(event.phase, progressPhase)
            val nextStages = updateProgressStages(progressStages, nextPhase, event)
            val eta = estimateProgressEta(event, nextPhase)
            copy(
                progressStep = event.step,
                progressSteps = event.steps,
                progressTime = event.time,
                progressEtaSeconds = eta,
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

    private fun estimateProgressEta(event: GenerationJobEvent.Progress, phase: String): Int {
        val phaseChanged = phase != lastProgressPhase
        if (phaseChanged) {
            recentStepTimes.clear()
            lastProgressPhase = phase
        }

        if (event.step > lastProgressStep) {
            val deltaTime = event.time - lastProgressTime
            val deltaSteps = event.step - lastProgressStep
            val secondsPerStep = deltaTime / deltaSteps
            if (secondsPerStep > 0.0 && secondsPerStep.isFinite()) {
                recentStepTimes.addLast(secondsPerStep)
                while (recentStepTimes.size > RECENT_STEP_TIME_WINDOW) {
                    recentStepTimes.removeFirst()
                }
            }
            lastProgressTime = event.time
            lastProgressStep = event.step
        } else if (event.step < lastProgressStep || event.time < lastProgressTime) {
            recentStepTimes.clear()
            lastProgressTime = event.time
            lastProgressStep = event.step
        }

        if (event.steps <= 0 || event.step <= 0) {
            return 0
        }

        val secondsPerStep = if (recentStepTimes.isNotEmpty()) {
            recentStepTimes.average()
        } else {
            event.time / event.step
        }
        val remainingSteps = (event.steps - event.step).coerceAtLeast(0)
        return (secondsPerStep * remainingSteps).roundToInt().coerceAtLeast(0)
    }

    private fun resetProgressEstimator() {
        lastProgressStep = 0
        lastProgressTime = 0.0
        lastProgressPhase = ""
        lastProgressStageKey = ""
        currentStageStartStep = 0
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

    private fun findPresetForReusableParams(params: GalleryReusableParams): ImagePreset? {
        val state = _uiState.value
        if (params.presetId.isNotBlank()) {
            state.presets.firstOrNull { it.id == params.presetId }?.let { return it }
        }
        if (params.modelId.isBlank()) return null
        val modelName = params.modelId.substringAfterLast('/').substringAfterLast('\\')
        return state.presets.firstOrNull { preset ->
            preset.diffusionModel == params.modelId ||
                preset.diffusionModel.endsWith(params.modelId) ||
                preset.diffusionModel.substringAfterLast('/').substringAfterLast('\\') == modelName
        }
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
