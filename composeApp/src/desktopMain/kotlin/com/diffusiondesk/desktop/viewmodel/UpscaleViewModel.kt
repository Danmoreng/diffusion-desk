package com.diffusiondesk.desktop.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.ModelSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val selectedModelId: String = "",
    val source: UpscaleSourceImage? = null,
    val result: GeneratedImage? = null,
    val factor: Double = 2.0,
    val upscaleStage: String = "",
    val upscaleDetail: String = "",
    val upscaleProgress: Float = 0f,
    val isLoadingModels: Boolean = false,
    val isUpscaling: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val upscaleModels: List<ModelSummary> get() = models.filter { it.type == "esrgan" }
    val selectedModel: ModelSummary? get() = upscaleModels.firstOrNull { it.id == selectedModelId }
    val targetWidth: Int? get() = source?.let { (it.width * factor).roundToInt().coerceAtLeast(1) }
    val targetHeight: Int? get() = source?.let { (it.height * factor).roundToInt().coerceAtLeast(1) }
    val canUpscale: Boolean get() = source != null && selectedModelId.isNotBlank() && !isUpscaling
}

class UpscaleViewModel(
    private val scope: CoroutineScope,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
    private val settingsStore: DesktopSettingsStore,
) {
    private val _uiState = MutableStateFlow(UpscaleUiState())
    val uiState: StateFlow<UpscaleUiState> = _uiState.asStateFlow()

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
    }

    fun reloadModels() {
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

    fun selectModel(modelId: String) = update { copy(selectedModelId = modelId, error = null) }

    fun updateFactor(value: Double) = update {
        copy(factor = snapFactor(value), error = null)
    }

    fun loadFile(file: File) {
        scope.launch {
            val source = withContext(Dispatchers.IO) { file.toUpscaleSourceImage() }
            if (source == null) {
                update { copy(error = "Could not load the selected image.") }
            } else {
                update {
                    copy(
                        source = source,
                        result = null,
                        message = "Loaded ${source.name}.",
                        error = null,
                    )
                }
            }
        }
    }

    fun useGalleryImage(image: GalleryImage) = loadFile(image.file)

    fun upscale() {
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
