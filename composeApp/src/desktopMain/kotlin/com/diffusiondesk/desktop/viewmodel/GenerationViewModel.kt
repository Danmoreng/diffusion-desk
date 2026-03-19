package com.diffusiondesk.desktop.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GenerationRequest
import com.diffusiondesk.desktop.core.ModelSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class GenerationUiState(
    val modelId: String = "",
    val prompt: String = "A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night.",
    val negativePrompt: String = "deformed, blurry, low quality, watermark",
    val width: String = "1024",
    val height: String = "768",
    val steps: String = "4",
    val cfgScale: String = "1.0",
    val seed: String = "-1",
    val sampler: String = "euler_a",
    val availableModels: List<ModelSummary> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isLoadingModel: Boolean = false,
    val progressStep: Int = 0,
    val progressSteps: Int = 0,
    val progressTime: Double = 0.0,
    val progressPhase: String = "",
    val progressMessage: String = "",
    val isGenerating: Boolean = false,
    val resultUrl: String = "",
    val usedSeed: String = "",
    val image: ImageBitmap? = null,
    val message: String = "",
    val error: String? = null,
)

class GenerationViewModel(
    private val scope: CoroutineScope,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
) {
    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    val samplers = listOf("euler", "euler_a", "heun", "dpm2", "dpmpp_2s_a", "dpmpp_2m", "dpmpp_2mv2", "ipndm", "ipndm_v", "lcm", "ddim_trailing", "tcd")

    init {
        scope.launch {
            backendManager.state.collectLatest { state ->
                if (state.status == BackendStatus.Ready) {
                    refreshModels()
                } else {
                    progressJob?.cancel()
                    progressJob = null
                    update {
                        copy(
                            availableModels = emptyList(),
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

    fun updateModelId(value: String) = update { copy(modelId = value) }
    fun updatePrompt(value: String) = update { copy(prompt = value) }
    fun updateNegativePrompt(value: String) = update { copy(negativePrompt = value) }
    fun updateWidth(value: String) = update { copy(width = value) }
    fun updateHeight(value: String) = update { copy(height = value) }
    fun updateSteps(value: String) = update { copy(steps = value) }
    fun updateCfgScale(value: String) = update { copy(cfgScale = value) }
    fun updateSeed(value: String) = update { copy(seed = value) }
    fun updateSampler(value: String) = update { copy(sampler = value) }

    fun refreshModels() {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                return@launch
            }

            update { copy(isLoadingModels = true, error = null) }
            val result = client.fetchModels(backendManager.state.value.baseUrl)
            result.onSuccess { models ->
                val sdModels = models.filter { it.type == "stable-diffusion" || it.type == "root" }
                val activeModel = sdModels.firstOrNull { it.active }
                update {
                    copy(
                        availableModels = sdModels,
                        modelId = when {
                            activeModel != null -> activeModel.id
                            modelId.isBlank() -> modelId
                            else -> modelId
                        },
                        isLoadingModels = false,
                        message = if (sdModels.isEmpty()) "No SD models found under model_dir." else "Loaded ${sdModels.size} SD models.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                update {
                    copy(
                        isLoadingModels = false,
                        error = error.message ?: "Failed to fetch models.",
                    )
                }
            }
        }
    }

    fun loadSelectedModel() {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                update { copy(error = "Backend is not ready.") }
                return@launch
            }

            val modelId = _uiState.value.modelId.trim()
            if (modelId.isBlank()) {
                update { copy(error = "Select a model first.") }
                return@launch
            }

            update {
                copy(
                    isLoadingModel = true,
                    message = "Loading model $modelId...",
                    error = null,
                )
            }

            val result = client.loadModel(backendManager.state.value.baseUrl, modelId)
            result.onSuccess {
                refreshModels()
                update {
                    copy(
                        isLoadingModel = false,
                        message = "Loaded model $modelId.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                update {
                    copy(
                        isLoadingModel = false,
                        error = error.message ?: "Failed to load model.",
                    )
                }
            }
        }
    }

    fun generate() {
        scope.launch {
            if (backendManager.state.value.status != BackendStatus.Ready) {
                update { copy(error = "Backend is not ready.") }
                return@launch
            }

            val request = runCatching { buildRequest() }
                .onFailure { error -> update { copy(error = error.message ?: "Invalid generation parameters.") } }
                .getOrNull() ?: return@launch

            update {
                copy(
                    isGenerating = true,
                    progressStep = 0,
                    progressSteps = 0,
                    progressTime = 0.0,
                    progressPhase = "Starting...",
                    progressMessage = "",
                    message = "Submitting generation request using the active model...",
                    error = null,
                )
            }

            startProgressPolling()
            val generationResult = client.generateImage(backendManager.state.value.baseUrl, request)
            stopProgressPolling()

            generationResult.onSuccess { result ->
                val bitmapResult = client.fetchImageBitmap(backendManager.state.value.baseUrl, result.imageUrl)
                bitmapResult.onSuccess { image ->
                    update {
                        copy(
                            isGenerating = false,
                            resultUrl = result.imageUrl,
                            usedSeed = result.usedSeed.toString(),
                            image = image,
                            message = "Image generated successfully.",
                            error = null,
                        )
                    }
                }.onFailure { error ->
                    update {
                        copy(
                            isGenerating = false,
                            resultUrl = result.imageUrl,
                            usedSeed = result.usedSeed.toString(),
                            image = null,
                            error = error.message ?: "Failed to load generated image.",
                        )
                    }
                }
            }.onFailure { error ->
                update {
                    copy(
                        isGenerating = false,
                        error = error.message ?: "Generation failed.",
                    )
                }
            }
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val progress = client.fetchProgress(backendManager.state.value.baseUrl)
                progress.onSuccess { snapshot ->
                    update {
                        copy(
                            progressStep = snapshot.step,
                            progressSteps = snapshot.steps,
                            progressTime = snapshot.time,
                            progressPhase = snapshot.phase,
                            progressMessage = snapshot.message,
                        )
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun buildRequest(): GenerationRequest {
        val state = _uiState.value
        require(state.prompt.isNotBlank()) { "Prompt is required." }
        return GenerationRequest(
            modelId = "",
            prompt = state.prompt,
            negativePrompt = state.negativePrompt,
            width = state.width.toIntOrNull() ?: error("Width must be numeric."),
            height = state.height.toIntOrNull() ?: error("Height must be numeric."),
            steps = state.steps.toIntOrNull() ?: error("Steps must be numeric."),
            cfgScale = state.cfgScale.toDoubleOrNull() ?: error("CFG scale must be numeric."),
            seed = state.seed.toIntOrNull() ?: error("Seed must be numeric."),
            sampler = state.sampler.trim(),
        )
    }

    private fun update(transform: GenerationUiState.() -> GenerationUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
