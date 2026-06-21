package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.LlmPlacement
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.LlmRoleSettings
import com.diffusiondesk.desktop.core.NotificationCenter
import com.diffusiondesk.desktop.core.normalizeConfiguredPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SetupModelOption(
    val id: String,
    val name: String,
    val type: String,
)

data class SetupUiState(
    val step: Int = 1,
    val modelDir: String,
    val outputDir: String,
    val imagePresetName: String = "Starter Image",
    val selectedImageModel: String = "",
    val enableLlmPreset: Boolean = false,
    val llmPresetName: String = "Local Assistant",
    val selectedLlmModel: String = "",
    val selectedMmproj: String = "",
    val imageModels: List<SetupModelOption> = emptyList(),
    val llmModels: List<SetupModelOption> = emptyList(),
    val mmprojModels: List<SetupModelOption> = emptyList(),
    val isScanning: Boolean = false,
    val isFinishing: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val canContinueFromFolders: Boolean get() = modelDir.isNotBlank() && outputDir.isNotBlank() && !isScanning
    val canFinish: Boolean get() = selectedImageModel.isNotBlank() && !isFinishing
}

class SetupViewModel(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val imagePresetStore: ImagePresetStore,
    private val llmPresetStore: LlmPresetStore,
    private val notifications: NotificationCenter,
) {
    private val initialSettings = settingsStore.load()
    private val _uiState = MutableStateFlow(
        SetupUiState(
            modelDir = initialSettings.modelDir,
            outputDir = initialSettings.outputDir,
        ),
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateModelDir(value: String) = update { copy(modelDir = value, error = null) }
    fun updateOutputDir(value: String) = update { copy(outputDir = value, error = null) }
    fun updateImagePresetName(value: String) = update { copy(imagePresetName = value, error = null) }
    fun updateSelectedImageModel(value: String) = update { copy(selectedImageModel = value, error = null) }
    fun updateEnableLlmPreset(value: Boolean) = update { copy(enableLlmPreset = value, error = null) }
    fun updateLlmPresetName(value: String) = update { copy(llmPresetName = value, error = null) }
    fun updateSelectedLlmModel(value: String) = update { copy(selectedLlmModel = value, error = null) }
    fun updateSelectedMmproj(value: String) = update { copy(selectedMmproj = value, error = null) }
    fun goBack() = update { copy(step = (step - 1).coerceAtLeast(1), error = null) }

    fun scanAndContinue() {
        scope.launch {
            val current = _uiState.value
            val settings = initialSettings.copy(
                modelDir = normalizeConfiguredPath(initialSettings.repoRoot, current.modelDir),
                outputDir = normalizeConfiguredPath(initialSettings.repoRoot, current.outputDir),
                setupCompleted = false,
            )
            update {
                copy(
                    modelDir = settings.modelDir,
                    outputDir = settings.outputDir,
                    isScanning = true,
                    message = "Scanning model folders...",
                    error = null,
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    File(settings.modelDir).mkdirs()
                    File(settings.outputDir).mkdirs()
                    settingsStore.save(settings)
                    scanModels(settings.modelDir)
                }
            }.onSuccess { result ->
                update {
                    copy(
                        step = 2,
                        imageModels = result.imageModels,
                        llmModels = result.llmModels,
                        mmprojModels = result.mmprojModels,
                        selectedImageModel = selectedImageModel.takeIf { it in result.imageModels.map(SetupModelOption::id) }
                            ?: result.imageModels.firstOrNull()?.id.orEmpty(),
                        selectedLlmModel = selectedLlmModel.takeIf { it in result.llmModels.map(SetupModelOption::id) }
                            ?: result.llmModels.firstOrNull()?.id.orEmpty(),
                        selectedMmproj = selectedMmproj.takeIf { it in result.mmprojModels.map(SetupModelOption::id) }
                            ?: result.mmprojModels.firstOrNull()?.id.orEmpty(),
                        isScanning = false,
                        message = "Found ${result.imageModels.size} image model(s) and ${result.llmModels.size} LLM model(s).",
                        error = null,
                    )
                }
            }.onFailure { error ->
                val message = error.message ?: "Failed to scan model folders."
                notifications.error(message)
                update { copy(isScanning = false, error = message) }
            }
        }
    }

    fun finish(onCompleted: () -> Unit) {
        scope.launch {
            val state = _uiState.value
            if (state.selectedImageModel.isBlank()) {
                update { copy(error = "Select an image model before finishing setup.") }
                return@launch
            }

            val settings = initialSettings.copy(
                modelDir = normalizeConfiguredPath(initialSettings.repoRoot, state.modelDir),
                outputDir = normalizeConfiguredPath(initialSettings.repoRoot, state.outputDir),
                setupCompleted = true,
            )

            update { copy(isFinishing = true, error = null, message = "Saving setup...") }
            runCatching {
                withContext(Dispatchers.IO) {
                    File(settings.modelDir).mkdirs()
                    File(settings.outputDir).mkdirs()
                    settingsStore.save(settings)

                    val imagePreset = ImagePreset(
                        id = uniqueImagePresetId(slugify(state.imagePresetName.ifBlank { "Starter Image" })),
                        name = state.imagePresetName.trim().ifBlank { "Starter Image" },
                        diffusionModel = state.selectedImageModel,
                    )
                    imagePresetStore.save(imagePreset)
                    imagePresetStore.saveLastPresetId(imagePreset.id)

                    if (state.enableLlmPreset && state.selectedLlmModel.isNotBlank()) {
                        val llmPreset = LlmPreset(
                            id = uniqueLlmPresetId(slugify(state.llmPresetName.ifBlank { "Local Assistant" })),
                            name = state.llmPresetName.trim().ifBlank { "Local Assistant" },
                            modelPath = state.selectedLlmModel,
                            mmprojPath = state.selectedMmproj,
                            placement = LlmPlacement.Auto,
                        )
                        llmPresetStore.save(llmPreset)
                        llmPresetStore.saveRoles(
                            LlmRoleSettings(
                                assistantPresetId = llmPreset.id,
                                promptEnhancerPresetId = llmPreset.id,
                                taggingPresetId = llmPreset.id.takeIf { state.selectedMmproj.isNotBlank() }.orEmpty(),
                            ),
                        )
                    }
                }
            }.onSuccess {
                notifications.success("Setup complete.")
                update { copy(isFinishing = false, message = "", error = null) }
                onCompleted()
            }.onFailure { error ->
                val message = error.message ?: "Failed to finish setup."
                notifications.error(message)
                update { copy(isFinishing = false, error = message) }
            }
        }
    }

    private fun uniqueImagePresetId(baseId: String): String {
        val existing = imagePresetStore.load().map { it.id }.toSet()
        return uniqueId(baseId, existing)
    }

    private fun uniqueLlmPresetId(baseId: String): String {
        val existing = llmPresetStore.load().map { it.id }.toSet()
        return uniqueId(baseId, existing)
    }

    private fun uniqueId(baseId: String, existing: Set<String>): String {
        if (baseId !in existing) return baseId
        var index = 2
        while ("$baseId-$index" in existing) index += 1
        return "$baseId-$index"
    }

    private fun scanModels(modelDir: String): SetupScanResult {
        val root = File(modelDir).canonicalFile
        val imageModels = mutableListOf<SetupModelOption>()
        val llmModels = mutableListOf<SetupModelOption>()
        val mmprojModels = mutableListOf<SetupModelOption>()

        scanFolder(root, "stable-diffusion", "stable-diffusion", imageModels)
        scanFolder(root, "diffusion_models", "diffusion_models", imageModels)
        scanFolder(root, "unet", "unet", imageModels)
        scanRootImageModels(root, imageModels)
        scanFolder(root, "llm", "llm", llmModels)
        scanFolder(root, "text-encoder", "text-encoder", llmModels)
        scanFolder(root, "mmproj", "mmproj", mmprojModels)
        scanMmprojCandidates(root.resolveChildIgnoreCase("llm"), mmprojModels)

        return SetupScanResult(
            imageModels = imageModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            llmModels = llmModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            mmprojModels = mmprojModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
        )
    }

    private fun scanFolder(root: File, folderName: String, type: String, target: MutableList<SetupModelOption>) {
        val folder = root.resolveChildIgnoreCase(folderName)
        if (!folder.isDirectory) return
        folder.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in modelExtensions }
            .take(maxScanResults)
            .forEach { file -> target += file.toOption(root, type) }
    }

    private fun scanRootImageModels(root: File, target: MutableList<SetupModelOption>) {
        root.listFiles { file -> file.isFile && file.extension.lowercase() in modelExtensions }
            .orEmpty()
            .take(maxScanResults)
            .forEach { file -> target += file.toOption(root, "root") }
    }

    private fun scanMmprojCandidates(folder: File, target: MutableList<SetupModelOption>) {
        if (!folder.isDirectory) return
        folder.walkTopDown()
            .filter { it.isFile && it.name.contains("mmproj", ignoreCase = true) && it.extension.lowercase() in modelExtensions }
            .take(maxScanResults)
            .forEach { file -> target += file.toOption(folder.parentFile ?: folder, "mmproj") }
    }

    private fun File.toOption(root: File, type: String): SetupModelOption {
        val id = root.toPath().relativize(canonicalFile.toPath()).joinToString("/")
        return SetupModelOption(
            id = id,
            name = nameWithoutExtension,
            type = type,
        )
    }

    private fun File.resolveChildIgnoreCase(name: String): File {
        val exact = resolve(name)
        if (exact.exists()) return exact
        return listFiles()
            .orEmpty()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: exact
    }

    private fun slugify(value: String): String {
        val slug = value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.ifBlank { "preset" }
    }

    private fun update(transform: SetupUiState.() -> SetupUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private data class SetupScanResult(
        val imageModels: List<SetupModelOption>,
        val llmModels: List<SetupModelOption>,
        val mmprojModels: List<SetupModelOption>,
    )

    private companion object {
        val modelExtensions = setOf("gguf", "safetensors", "ckpt", "pth", "pt", "bin")
        const val maxScanResults = 500
    }
}
