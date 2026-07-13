package com.diffusiondesk.desktop.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.core.GalleryMetadataParser
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.GenerationJobEvent
import com.diffusiondesk.desktop.core.GenerationRequest
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.LlmWorkerPool
import com.diffusiondesk.desktop.core.ModelSummary
import com.diffusiondesk.desktop.core.ParsedImageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class UpscaleMode {
    Classical,
    Generative,
}

internal fun proportionalDimension(value: Int, sourceDimension: Int, otherSourceDimension: Int): Int {
    if (value <= 0 || sourceDimension <= 0 || otherSourceDimension <= 0) return 0
    return (((value.toDouble() * otherSourceDimension / sourceDimension) / 8.0).roundToInt() * 8).coerceAtLeast(8)
}

private fun alignedDimension(value: Double): String =
    (((value / 8.0).roundToInt() * 8).coerceAtLeast(8)).toString()

private fun String.modelFileName(): String = substringAfterLast('/').substringAfterLast('\\')

internal enum class GenerativeUpscaleProgressPhase {
    Prepare,
    Encode,
    Sample,
    Decode,
}

internal fun effectiveGenerativeUpscaleSteps(steps: Int, strength: Double): Int {
    if (steps <= 0) return 0
    if (strength >= 1.0) return steps
    // stable-diffusion.cpp retains t_enc + 1 sigma intervals for img2img.
    return ((steps * strength.coerceIn(0.0, 1.0)).toInt() + 1).coerceIn(1, steps)
}

internal fun generativeUpscaleProgressPhase(
    rawPhase: String,
    step: Int,
    steps: Int,
    expectedSamplingSteps: Int,
): GenerativeUpscaleProgressPhase {
    val normalized = rawPhase.lowercase()
    return when {
        normalized.contains("decod") || normalized.contains("sav") -> GenerativeUpscaleProgressPhase.Decode
        normalized.contains("sampl") -> GenerativeUpscaleProgressPhase.Sample
        step > 0 && steps > 0 && steps == expectedSamplingSteps -> GenerativeUpscaleProgressPhase.Sample
        normalized.contains("encod") || normalized.contains("prompt") || normalized.contains("vae") -> GenerativeUpscaleProgressPhase.Encode
        else -> GenerativeUpscaleProgressPhase.Prepare
    }
}

internal fun nextGenerativeUpscaleProgress(
    current: Float,
    phase: GenerativeUpscaleProgressPhase,
    step: Int,
    steps: Int,
): Float {
    val stepProgress = if (steps > 0) (step.toFloat() / steps).coerceIn(0f, 1f) else 0f
    val proposed = when (phase) {
        GenerativeUpscaleProgressPhase.Prepare -> 0.10f
        GenerativeUpscaleProgressPhase.Encode -> 0.22f
        GenerativeUpscaleProgressPhase.Sample -> 0.25f + stepProgress * 0.67f
        GenerativeUpscaleProgressPhase.Decode -> 0.96f
    }
    return maxOf(current, proposed).coerceIn(0f, 0.99f)
}

data class UpscaleSourceImage(
    val file: File,
    val bytes: ByteArray,
    val bufferedImage: BufferedImage,
    val bitmap: ImageBitmap,
    val width: Int,
    val height: Int,
) {
    val name: String get() = file.name
}

data class UpscaleUiState(
    val models: List<ModelSummary> = emptyList(),
    val imagePresets: List<ImagePreset> = emptyList(),
    val selectedModelId: String = "",
    val selectedPresetId: String = "",
    val mode: UpscaleMode = UpscaleMode.Classical,
    val source: UpscaleSourceImage? = null,
    val result: GeneratedImage? = null,
    val factor: Double = 2.0,
    val targetWidthText: String = "",
    val targetHeightText: String = "",
    val prompt: String = "",
    val negativePrompt: String = "",
    val highResStepsText: String = "20",
    val cfgScaleText: String = "5.0",
    val seed: Int = -1,
    val sampler: String = "euler",
    val denoisingStrength: Double = 0.25,
    val upscaleStage: String = "",
    val upscaleDetail: String = "",
    val upscaleProgress: Float = 0f,
    val upscaleStep: Int = 0,
    val upscaleSteps: Int = 0,
    val upscaleElapsedSeconds: Double = 0.0,
    val upscaleEtaSeconds: Int = 0,
    val isLoadingModels: Boolean = false,
    val isUpscaling: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val upscaleModels: List<ModelSummary> get() = models.filter { it.type == "esrgan" }
    val generativePresets: List<ImagePreset> get() = imagePresets.filter { it.diffusionModel.isNotBlank() }
    val selectedModel: ModelSummary? get() = upscaleModels.firstOrNull { it.id == selectedModelId }
    val selectedPreset: ImagePreset? get() = generativePresets.firstOrNull { it.id == selectedPresetId }
    val targetWidth: Int? get() = targetWidthText.toIntOrNull()?.takeIf { it > 0 }
    val targetHeight: Int? get() = targetHeightText.toIntOrNull()?.takeIf { it > 0 }
    val highResSteps: Int? get() = highResStepsText.toIntOrNull()?.takeIf { it in 1..100 }
    val cfgScale: Double? get() = cfgScaleText.toDoubleOrNull()?.takeIf { it in 0.0..30.0 }
    val canUpscale: Boolean
        get() = source != null && !isUpscaling && when (mode) {
            UpscaleMode.Classical -> selectedModelId.isNotBlank()
            UpscaleMode.Generative -> selectedPresetId.isNotBlank() && prompt.isNotBlank() && highResSteps != null && cfgScale != null
        }
}

class UpscaleViewModel(
    private val scope: CoroutineScope,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
    private val settingsStore: DesktopSettingsStore,
    private val presetStore: ImagePresetStore,
    private val llmWorkerPool: LlmWorkerPool,
) {
    private val _uiState = MutableStateFlow(UpscaleUiState())
    val uiState: StateFlow<UpscaleUiState> = _uiState.asStateFlow()
    private var upscaleTimerJob: Job? = null
    private var upscaleStartedAtMs: Long = 0L
    private var lastSamplingEventAtMs: Long = 0L
    private var lastSamplingStep: Int = 0
    private val recentSamplingStepSeconds = ArrayDeque<Double>()

    init {
        scope.launch {
            var loadedReadyBaseUrl: String? = null
            backendManager.state.collect { state ->
                if (state.status == BackendStatus.Ready) {
                    if (state.baseUrl != loadedReadyBaseUrl) {
                        loadedReadyBaseUrl = state.baseUrl
                        reloadModels()
                    }
                } else {
                    loadedReadyBaseUrl = null
                }
            }
        }
        reloadPresets()
    }

    fun reloadModels() {
        reloadPresets()
        val baseUrl = backendManager.state.value.baseUrl
        if (backendManager.state.value.status != BackendStatus.Ready) {
            update { copy(error = "Image worker is not ready.") }
            return
        }
        if (_uiState.value.isLoadingModels) return
        scope.launch {
            update { copy(isLoadingModels = true, error = null) }
            client.fetchModels(baseUrl)
                .onSuccess { models ->
                    val esrgan = models.filter { it.type == "esrgan" }
                    val currentSelected = _uiState.value.selectedModelId
                    val selected = currentSelected.takeIf { id -> esrgan.any { it.id == id } }
                        ?: esrgan.firstOrNull { it.loaded || it.active }?.id
                        ?: esrgan.firstOrNull()?.id
                        ?: ""
                    update {
                        copy(
                            models = models,
                            selectedModelId = selected,
                            isLoadingModels = false,
                            message = if (esrgan.isEmpty()) "No ESRGAN models found." else "Loaded ${esrgan.size} ESRGAN model(s).",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            isLoadingModels = false,
                            error = error.message ?: "Failed to load upscale models.",
                        )
                    }
                }
        }
    }

    private fun reloadPresets() {
        scope.launch {
            val (presets, lastPresetId) = withContext(Dispatchers.IO) {
                presetStore.load() to presetStore.loadLastPresetId()
            }
            update {
                val availablePresets = presets.filter { it.diffusionModel.isNotBlank() }
                val selected = selectedPresetId.takeIf { id -> availablePresets.any { it.id == id } }
                    ?: lastPresetId.takeIf { id -> availablePresets.any { it.id == id } }
                    ?: availablePresets.firstOrNull()?.id
                    ?: ""
                val preset = availablePresets.firstOrNull { it.id == selected }
                copy(
                    imagePresets = presets,
                    selectedPresetId = selected,
                    highResStepsText = highResStepsText.ifBlank { preset?.defaultSteps?.toString().orEmpty() },
                    cfgScaleText = if (source == null) preset?.defaultCfgScale?.toString() ?: cfgScaleText else cfgScaleText,
                    sampler = if (source == null) preset?.defaultSampler?.ifBlank { sampler } ?: sampler else sampler,
                )
            }
        }
    }

    fun selectModel(modelId: String) = update { copy(selectedModelId = modelId, error = null) }

    fun selectMode(mode: UpscaleMode) = update { copy(mode = mode, result = null, error = null) }

    fun selectPreset(presetId: String) = update {
        val preset = imagePresets.firstOrNull { it.id == presetId }
        copy(
            selectedPresetId = presetId,
            highResStepsText = preset?.defaultSteps?.toString() ?: highResStepsText,
            cfgScaleText = preset?.defaultCfgScale?.toString() ?: cfgScaleText,
            sampler = preset?.defaultSampler?.ifBlank { sampler } ?: sampler,
            negativePrompt = preset?.defaultNegativePrompt ?: negativePrompt,
            error = null,
        )
    }

    fun updatePrompt(value: String) = update { copy(prompt = value, error = null) }

    fun updateNegativePrompt(value: String) = update { copy(negativePrompt = value, error = null) }

    fun updateCfgScale(value: String) = update {
        copy(
            cfgScaleText = value.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(5),
            error = null,
        )
    }

    fun updateHighResSteps(value: String) = update {
        copy(highResStepsText = value.filter(Char::isDigit).take(3), error = null)
    }

    fun updateDenoisingStrength(value: Double) = update {
        copy(denoisingStrength = ((value.coerceIn(0.05, 0.95) * 100).roundToInt() / 100.0), error = null)
    }

    fun updateFactor(value: Double) = update {
        val nextFactor = snapFactor(value)
        val nextWidth = source?.let { alignedDimension(it.width * nextFactor) }.orEmpty()
        val nextHeight = source?.let { alignedDimension(it.height * nextFactor) }.orEmpty()
        copy(
            factor = nextFactor,
            targetWidthText = nextWidth,
            targetHeightText = nextHeight,
            error = null,
        )
    }

    fun updateTargetWidth(value: String) = update {
        val filtered = value.filter(Char::isDigit).take(5)
        val width = filtered.toIntOrNull()
        val height = source?.let { image -> width?.let { proportionalDimension(it, image.width, image.height) } }
        copy(
            factor = source?.let { image -> width?.toDouble()?.div(image.width) }?.coerceIn(1.0, 4.0) ?: factor,
            targetWidthText = filtered,
            targetHeightText = height?.toString() ?: targetHeightText,
            error = null,
        )
    }

    fun updateTargetHeight(value: String) = update {
        val filtered = value.filter(Char::isDigit).take(5)
        val height = filtered.toIntOrNull()
        val width = source?.let { image -> height?.let { proportionalDimension(it, image.height, image.width) } }
        copy(
            factor = source?.let { image -> height?.toDouble()?.div(image.height) }?.coerceIn(1.0, 4.0) ?: factor,
            targetWidthText = width?.toString() ?: targetWidthText,
            targetHeightText = filtered,
            error = null,
        )
    }

    fun loadFile(file: File) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                file.toUpscaleSourceImage() to runCatching { GalleryMetadataParser.parse(file) }.getOrDefault(ParsedImageMetadata())
            }
            val source = loaded.first
            if (source == null) {
                update { copy(error = "Could not load the selected image.") }
            } else {
                applySource(source, loaded.second)
            }
        }
    }

    fun useGalleryImage(image: GalleryImage) {
        scope.launch {
            val source = withContext(Dispatchers.IO) { image.file.toUpscaleSourceImage() }
            if (source == null) {
                update { copy(error = "Could not load the selected image.") }
                return@launch
            }
            applySource(
                source = source,
                metadata = ParsedImageMetadata(
                    prompt = image.prompt,
                    negativePrompt = image.negativePrompt,
                    seed = image.seed,
                    width = image.width,
                    height = image.height,
                    steps = image.steps,
                    cfgScale = image.cfgScale,
                    sampler = image.sampler,
                    modelId = image.modelId,
                    metadataText = image.metadataText,
                    loras = image.loras,
                ),
                preferredPresetId = image.presetId,
            )
        }
    }

    fun upscale() {
        when (_uiState.value.mode) {
            UpscaleMode.Classical -> upscaleClassical()
            UpscaleMode.Generative -> upscaleGeneratively()
        }
    }

    private fun upscaleClassical() {
        val state = _uiState.value
        val source = state.source ?: return update { copy(error = "Choose an image first.") }
        val modelId = state.selectedModelId.takeIf(String::isNotBlank)
            ?: return update { copy(error = "Choose an ESRGAN model first.") }
        val factor = snapFactor(state.factor)
        val targetWidth = state.targetWidth ?: (source.width * factor).roundToInt().coerceAtLeast(1)
        val targetHeight = state.targetHeight ?: (source.height * factor).roundToInt().coerceAtLeast(1)
        val backendFactor = ceil(factor).roundToInt().coerceIn(1, 4)
        val baseUrl = backendManager.state.value.baseUrl
        if (backendManager.state.value.status != BackendStatus.Ready) {
            update { copy(error = "Image worker is not ready.") }
            return
        }

        scope.launch {
            update {
                copy(
                    isUpscaling = true,
                    result = null,
                    upscaleStage = "Loading ESRGAN model",
                    upscaleDetail = selectedModel?.name ?: modelId,
                    upscaleProgress = 0.12f,
                    message = "Loading upscale model...",
                    error = null,
                )
            }
            client.loadUpscaleModel(baseUrl, modelId)
                .mapCatching {
                    update {
                        copy(
                            upscaleStage = "Running neural upscale",
                            upscaleDetail = "${source.width} x ${source.height} source, ${"%.2f".format(java.util.Locale.US, factor)}x target",
                            upscaleProgress = 0.38f,
                            message = "Upscaling ${source.name} ${"%.2f".format(java.util.Locale.US, factor)}x...",
                        )
                    }
                    val imageBase64 = Base64.getEncoder().encodeToString(source.bytes)
                    client.upscaleImage(baseUrl, imageBase64, backendFactor, saveImage = false).getOrThrow()
                }
                .mapCatching { result ->
                    update {
                        copy(
                            upscaleStage = "Preparing final image",
                            upscaleDetail = "Fetching intermediate output from the image worker.",
                            upscaleProgress = 0.74f,
                        )
                    }
                    val image = client.fetchGeneratedImage(baseUrl, result.imageUrl).getOrThrow()
                    withContext(Dispatchers.IO) {
                        update {
                            copy(
                                upscaleStage = "Resizing and saving",
                                upscaleDetail = "Writing final ${targetWidth} x ${targetHeight} image.",
                                upscaleProgress = 0.88f,
                            )
                        }
                        val outputDir = settingsStore.load().outputDir
                        val finalImage = image.resizeAndSave(targetWidth, targetHeight, outputDir)
                        deleteTempUpscaleResult(outputDir, result.name)
                        finalImage
                    }
                }
                .onSuccess { image ->
                    update {
                        copy(
                            result = image,
                            isUpscaling = false,
                            upscaleStage = "",
                            upscaleDetail = "",
                            upscaleProgress = 1f,
                            message = "Upscale complete: ${image.bufferedImage.width} x ${image.bufferedImage.height}.",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            isUpscaling = false,
                            upscaleStage = "",
                            upscaleDetail = "",
                            upscaleProgress = 0f,
                            error = error.message ?: "Upscale failed.",
                        )
                    }
                }
        }
    }

    private fun upscaleGeneratively() {
        val state = _uiState.value
        val source = state.source ?: return update { copy(error = "Choose an image first.") }
        val preset = state.selectedPreset ?: return update { copy(error = "Choose an Ideogram4 preset first.") }
        val prompt = state.prompt.trim().takeIf(String::isNotBlank)
            ?: return update { copy(error = "A prompt is required for the high-res pass.") }
        val targetWidth = state.targetWidth ?: return update { copy(error = "Enter a target width.") }
        val targetHeight = state.targetHeight ?: return update { copy(error = "Enter a target height.") }
        val steps = state.highResSteps ?: return update { copy(error = "High-res steps must be between 1 and 100.") }
        val cfgScale = state.cfgScale ?: return update { copy(error = "CFG scale must be between 0 and 30.") }
        if (targetWidth % 8 != 0 || targetHeight % 8 != 0) {
            return update { copy(error = "Target width and height must be divisible by 8.") }
        }
        if (targetWidth < source.width || targetHeight < source.height) {
            return update { copy(error = "The generative high-res target must not be smaller than the source image.") }
        }
        if (backendManager.state.value.status != BackendStatus.Ready) {
            update { copy(error = "Image worker is not ready.") }
            return
        }

        val baseUrl = backendManager.state.value.baseUrl
        scope.launch {
            update {
                copy(
                    isUpscaling = true,
                    result = null,
                    upscaleStage = "Preparing Ideogram4",
                        upscaleDetail = "Releasing LLM VRAM and loading ${preset.name}.",
                        upscaleProgress = 0.05f,
                        upscaleStep = 0,
                        upscaleSteps = 0,
                        upscaleElapsedSeconds = 0.0,
                        upscaleEtaSeconds = 0,
                        message = "Preparing generative high-res pass...",
                        error = null,
                    )
                }
            startUpscaleTimer()

            runCatching {
                llmWorkerPool.unloadGpuModelsForImageGeneration().getOrThrow()
                update {
                    copy(
                        upscaleStage = "Loading Ideogram4 preset",
                        upscaleDetail = preset.name,
                        upscaleProgress = 0.12f,
                    )
                }
                client.loadPreset(baseUrl, preset, settingsStore.load()).getOrThrow()
                val imageBase64 = Base64.getEncoder().encodeToString(source.bytes)
                update {
                    copy(
                        upscaleStage = "Submitting high-res pass",
                        upscaleDetail = "${source.width} x ${source.height} to $targetWidth x $targetHeight",
                        upscaleProgress = 0.2f,
                    )
                }
                val submission = client.submitGenerationJob(
                    baseUrl,
                    GenerationRequest(
                        modelId = "",
                        prompt = prompt,
                        negativePrompt = state.negativePrompt,
                        width = targetWidth,
                        height = targetHeight,
                        steps = steps,
                        cfgScale = cfgScale,
                        seed = state.seed,
                        sampler = state.sampler,
                        saveImage = true,
                        initImageBase64 = imageBase64,
                        strength = state.denoisingStrength,
                    ),
                ).getOrThrow()
                val expectedSamplingSteps = effectiveGenerativeUpscaleSteps(steps, state.denoisingStrength)
                val generation = client.streamGenerationJobEvents(baseUrl, submission.id) { event ->
                    when (event) {
                        is GenerationJobEvent.Progress -> handleGenerativeProgress(
                            event = event,
                            expectedSamplingSteps = expectedSamplingSteps,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                        )
                        is GenerationJobEvent.Queued -> update { copy(upscaleStage = "Queued high-res pass") }
                        is GenerationJobEvent.Started -> update { copy(upscaleStage = "Running Ideogram4 high-res pass") }
                        is GenerationJobEvent.Failed -> error(event.message)
                        is GenerationJobEvent.Cancelled -> error(event.message)
                        is GenerationJobEvent.Completed -> Unit
                    }
                }.getOrThrow()
                update {
                    copy(
                        upscaleStage = "Loading final image",
                        upscaleDetail = "Fetching the refined output.",
                        upscaleProgress = 0.95f,
                    )
                }
                val image = client.fetchGeneratedImages(baseUrl, generation.imageUrls).getOrThrow().firstOrNull()
                    ?: error("The high-res pass did not return an image.")
                image.copy(generationTime = generation.generationTime)
            }.onSuccess { image ->
                stopUpscaleTimer()
                update {
                    copy(
                        result = image,
                        isUpscaling = false,
                        upscaleStage = "",
                        upscaleDetail = "",
                        upscaleProgress = 1f,
                        upscaleEtaSeconds = 0,
                        message = "Generative high-res pass complete: ${image.bufferedImage.width} x ${image.bufferedImage.height}.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                stopUpscaleTimer()
                update {
                    copy(
                        isUpscaling = false,
                        upscaleStage = "",
                        upscaleDetail = "",
                        upscaleProgress = 0f,
                        upscaleEtaSeconds = 0,
                        error = error.message ?: "Generative high-res pass failed.",
                    )
                }
            }
        }
    }

    private fun handleGenerativeProgress(
        event: GenerationJobEvent.Progress,
        expectedSamplingSteps: Int,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        val phase = generativeUpscaleProgressPhase(
            rawPhase = event.phase,
            step = event.step,
            steps = event.steps,
            expectedSamplingSteps = expectedSamplingSteps,
        )
        val etaSeconds = updateSamplingEta(event, phase)
        update {
            val stage = when (phase) {
                GenerativeUpscaleProgressPhase.Prepare -> "Preparing generative upscale"
                GenerativeUpscaleProgressPhase.Encode -> "Encoding source image and prompt"
                GenerativeUpscaleProgressPhase.Sample -> "Sampling high-res details"
                GenerativeUpscaleProgressPhase.Decode -> "VAE decoding and saving"
            }
            val detail = when (phase) {
                GenerativeUpscaleProgressPhase.Prepare -> event.message.ifBlank { "Preparing the image worker." }
                GenerativeUpscaleProgressPhase.Encode -> when {
                    event.message.contains("tiling", ignoreCase = true) -> "Tiled VAE is active for the high-resolution image."
                    else -> "Creating source-image latents and prompt conditioning."
                }
                GenerativeUpscaleProgressPhase.Sample -> "Step ${event.step.coerceAtLeast(0)} / ${event.steps.coerceAtLeast(expectedSamplingSteps)}  •  $targetWidth x $targetHeight"
                GenerativeUpscaleProgressPhase.Decode -> "Decoding and writing the refined image."
            }
            copy(
                upscaleStage = stage,
                upscaleDetail = detail,
                upscaleProgress = nextGenerativeUpscaleProgress(
                    current = upscaleProgress,
                    phase = phase,
                    step = event.step,
                    steps = event.steps,
                ),
                upscaleStep = if (phase == GenerativeUpscaleProgressPhase.Sample) event.step else 0,
                upscaleSteps = if (phase == GenerativeUpscaleProgressPhase.Sample) event.steps else 0,
                upscaleEtaSeconds = etaSeconds,
            )
        }
    }

    private fun updateSamplingEta(
        event: GenerationJobEvent.Progress,
        phase: GenerativeUpscaleProgressPhase,
    ): Int {
        if (phase != GenerativeUpscaleProgressPhase.Sample || event.steps <= 0 || event.step <= 0) {
            if (phase == GenerativeUpscaleProgressPhase.Decode) return 0
            return _uiState.value.upscaleEtaSeconds
        }

        val now = System.currentTimeMillis()
        if (event.step > lastSamplingStep) {
            val secondsPerStep = when {
                lastSamplingEventAtMs > 0L && lastSamplingStep > 0 -> {
                    (now - lastSamplingEventAtMs).coerceAtLeast(0L) / 1000.0 / (event.step - lastSamplingStep)
                }
                event.time > 0.0 -> event.time
                else -> 0.0
            }
            if (secondsPerStep > 0.0 && secondsPerStep.isFinite()) {
                recentSamplingStepSeconds.addLast(secondsPerStep)
                while (recentSamplingStepSeconds.size > 6) recentSamplingStepSeconds.removeFirst()
            }
            lastSamplingEventAtMs = now
            lastSamplingStep = event.step
        }

        val averageSeconds = recentSamplingStepSeconds.takeIf { it.isNotEmpty() }?.average() ?: return 0
        return (averageSeconds * (event.steps - event.step).coerceAtLeast(0)).roundToInt().coerceAtLeast(0)
    }

    private fun startUpscaleTimer() {
        upscaleTimerJob?.cancel()
        upscaleStartedAtMs = System.currentTimeMillis()
        lastSamplingEventAtMs = 0L
        lastSamplingStep = 0
        recentSamplingStepSeconds.clear()
        upscaleTimerJob = scope.launch {
            while (_uiState.value.isUpscaling) {
                val elapsed = (System.currentTimeMillis() - upscaleStartedAtMs).coerceAtLeast(0L) / 1000.0
                update { copy(upscaleElapsedSeconds = elapsed) }
                delay(1_000)
            }
        }
    }

    private fun stopUpscaleTimer() {
        upscaleTimerJob?.cancel()
        upscaleTimerJob = null
        if (upscaleStartedAtMs > 0L) {
            val elapsed = (System.currentTimeMillis() - upscaleStartedAtMs).coerceAtLeast(0L) / 1000.0
            update { copy(upscaleElapsedSeconds = elapsed) }
        }
    }

    private fun applySource(
        source: UpscaleSourceImage,
        metadata: ParsedImageMetadata,
        preferredPresetId: String = "",
    ) {
        update {
            val matchedPreset = generativePresets.firstOrNull { it.id == preferredPresetId }
                ?: generativePresets.firstOrNull {
                    it.diffusionModel.modelFileName().equals(metadata.modelId.modelFileName(), ignoreCase = true)
                }
                ?: selectedPreset
                ?: generativePresets.firstOrNull()
            copy(
                source = source,
                result = null,
                mode = if (metadata.prompt.isNotBlank()) UpscaleMode.Generative else mode,
                selectedPresetId = matchedPreset?.id.orEmpty(),
                targetWidthText = alignedDimension(source.width * factor),
                targetHeightText = alignedDimension(source.height * factor),
                prompt = metadata.prompt,
                negativePrompt = metadata.negativePrompt,
                highResStepsText = (metadata.steps ?: matchedPreset?.defaultSteps ?: 20).toString(),
                cfgScaleText = (metadata.cfgScale ?: matchedPreset?.defaultCfgScale ?: 5.0).toString(),
                seed = metadata.seed?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt() ?: -1,
                sampler = metadata.sampler.ifBlank { matchedPreset?.defaultSampler ?: "euler" },
                message = if (metadata.prompt.isNotBlank()) {
                    "Loaded ${source.name} with its generation prompt."
                } else {
                    "Loaded ${source.name}. Add a prompt to use Generative High-res."
                },
                error = null,
            )
        }
    }

    private fun File.toUpscaleSourceImage(): UpscaleSourceImage? {
        if (!isFile) return null
        val bytes = readBytes()
        val image = ImageIO.read(this) ?: return null
        return UpscaleSourceImage(
            file = absoluteFile,
            bytes = bytes,
            bufferedImage = image,
            bitmap = image.toComposeImageBitmap(),
            width = image.width,
            height = image.height,
        )
    }

    private fun update(transform: UpscaleUiState.() -> UpscaleUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun snapFactor(value: Double): Double =
        (value.coerceIn(1.0, 4.0) / 0.05).roundToInt() * 0.05

    private fun GeneratedImage.resizeAndSave(targetWidth: Int, targetHeight: Int, outputDir: String): GeneratedImage {
        val resized = bufferedImage.resizeTo(targetWidth, targetHeight)
        val bytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(resized, "png", output)
            output.toByteArray()
        }
        val outputRoot = File(outputDir.ifBlank { System.getProperty("user.home") }).absoluteFile
        outputRoot.mkdirs()
        val file = File(outputRoot, "upscale-${System.currentTimeMillis()}-${targetWidth}x$targetHeight.png")
        file.writeBytes(bytes)
        return GeneratedImage(
            bitmap = resized.toComposeImageBitmap(),
            bufferedImage = resized,
            bytes = bytes,
            sourceUrl = file.toURI().toString(),
        )
    }

    private fun deleteTempUpscaleResult(outputDir: String, fileName: String) {
        if (fileName.isBlank()) return
        runCatching {
            val outputRoot = File(outputDir.ifBlank { System.getProperty("user.home") }).canonicalFile
            val tempFile = File(File(outputRoot, "temp"), fileName).canonicalFile
            if (tempFile.parentFile == File(outputRoot, "temp").canonicalFile && tempFile.isFile) {
                tempFile.delete()
            }
        }
    }

    private fun BufferedImage.resizeTo(targetWidth: Int, targetHeight: Int): BufferedImage {
        val resized = BufferedImage(targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(this, 0, 0, resized.width, resized.height, null)
        } finally {
            graphics.dispose()
        }
        return resized
    }
}
