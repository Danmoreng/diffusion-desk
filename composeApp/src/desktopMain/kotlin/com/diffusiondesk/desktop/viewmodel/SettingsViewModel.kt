package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.DesktopSettings
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.ImageTaggingService
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.LlmRoleSettings
import com.diffusiondesk.desktop.core.LlmWorkerPool
import com.diffusiondesk.desktop.core.LlmWorkerState
import com.diffusiondesk.desktop.core.detectDefaultModelDir
import com.diffusiondesk.desktop.core.detectDefaultOutputDir
import com.diffusiondesk.desktop.core.detectDefaultRepoRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val repoRoot: String,
    val listenPort: String,
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
    val themeMode: String,
    val actionBarPosition: String,
    val saveImagesAutomatically: Boolean,
    val galleryPreviewWidthDp: Int,
    val llmPresets: List<LlmPreset> = emptyList(),
    val llmRoles: LlmRoleSettings = LlmRoleSettings(),
    val llmWorkers: List<LlmWorkerState> = emptyList(),
    val isBusy: Boolean = false,
    val message: String = "",
    val error: String? = null,
)

class SettingsViewModel(
    private val scope: CoroutineScope,
    private val store: DesktopSettingsStore,
    private val backendManager: BackendManager,
    private val llmPresetStore: LlmPresetStore,
    private val llmWorkerPool: LlmWorkerPool,
    private val taggingService: ImageTaggingService,
    private val client: DiffusionDeskClient,
) {
    private val _uiState = MutableStateFlow(
        store.load().toUiState().copy(
            llmPresets = llmPresetStore.load(),
            llmRoles = llmPresetStore.loadRoles(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val backendState: StateFlow<BackendUiState> = backendManager.state

    init {
        scope.launch {
            llmWorkerPool.state.collect { workers ->
                update { copy(llmWorkers = workers) }
            }
        }
    }

    fun updateRepoRoot(value: String) = update { copy(repoRoot = value) }
    fun updateListenPort(value: String) = update { copy(listenPort = value) }
    fun updateModelDir(value: String) = update { copy(modelDir = value) }
    fun updateOutputDir(value: String) = update { copy(outputDir = value) }
    fun updateSetupCompleted(value: Boolean) = update { copy(setupCompleted = value) }
    fun updateThemeMode(value: String) = updateAndSave { copy(themeMode = value) }
    fun updateActionBarPosition(value: String) = updateAndSave { copy(actionBarPosition = value) }
    fun updateSaveImagesAutomatically(value: Boolean) = updateAndSave { copy(saveImagesAutomatically = value) }
    fun updateGalleryPreviewWidth(value: Int) = updateAndSave { copy(galleryPreviewWidthDp = value.coerceIn(320, 760)) }
    fun updateTaggingPresetId(value: String) = updateRoles { copy(taggingPresetId = value) }
    fun updateAssistantPresetId(value: String) = updateRoles { copy(assistantPresetId = value) }
    fun updatePromptEnhancerPresetId(value: String) = updateRoles { copy(promptEnhancerPresetId = value) }

    fun toggleThemeMode() {
        updateThemeMode(
            when (_uiState.value.themeMode) {
                "system" -> "light"
                "light" -> "dark"
                else -> "system"
            },
        )
    }

    fun useCurrentRepo() {
        val repoRoot = detectDefaultRepoRoot()
        update {
            copy(
                repoRoot = repoRoot,
                modelDir = detectDefaultModelDir(repoRoot),
                outputDir = detectDefaultOutputDir(repoRoot),
            )
        }
    }

    fun saveLocalSettings() {
        runCatching { currentSettings() }
            .onSuccess {
                store.save(it)
                update { copy(message = "Saved desktop settings.", error = null) }
            }
            .onFailure { error ->
                update { copy(error = error.message ?: "Invalid settings.") }
            }
    }

    fun startBackend() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            store.save(settings)
            update { copy(isBusy = true, message = "Starting image worker...", error = null) }

            val startResult = backendManager.start(settings)
            if (startResult.isSuccess) {
                applySettingsToBackendInternal(settings)
                loadConfigFromBackendInternal()
                update { copy(isBusy = false, message = "Image worker started.", error = null) }
            } else {
                update {
                    copy(
                        isBusy = false,
                        error = startResult.exceptionOrNull()?.message ?: "Failed to start image worker.",
                    )
                }
            }
        }
    }

    fun stopBackend() {
        scope.launch {
            update { copy(isBusy = true, message = "Stopping image worker...", error = null) }
            backendManager.stop()
            update { copy(isBusy = false, message = "Image worker stopped.", error = null) }
        }
    }

    fun unloadImageModel() {
        scope.launch {
            if (backendState.value.status != BackendStatus.Ready) {
                update { copy(message = "Image worker is not ready yet.") }
                return@launch
            }
            update { copy(isBusy = true, message = "Unloading image model...", error = null) }
            client.unloadImageModel(backendState.value.baseUrl)
                .onSuccess { update { copy(isBusy = false, message = "Image model unloaded.", error = null) } }
                .onFailure { error -> update { copy(isBusy = false, error = error.message ?: "Failed to unload image model.") } }
        }
    }

    fun reloadLlmPresets() {
        runCatching {
            llmPresetStore.load() to llmPresetStore.loadRoles()
        }.onSuccess { (presets, roles) ->
            update {
                copy(
                    llmPresets = presets,
                    llmRoles = roles.sanitizeFor(presets),
                    message = "Loaded ${presets.size} LLM preset(s).",
                    error = null,
                )
            }
            llmPresetStore.saveRoles(_uiState.value.llmRoles)
        }.onFailure { error ->
            update { copy(error = error.message ?: "Failed to load LLM presets.") }
        }
    }

    fun loadLlmRole(role: String) {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole(role)
            if (preset == null) {
                update { copy(error = "Select an LLM preset for $role first.") }
                return@launch
            }
            update { copy(isBusy = true, message = "Loading ${preset.name} for $role...", error = null) }
            llmWorkerPool.ensureWorkerForPreset(settings, preset)
                .onSuccess { update { copy(isBusy = false, message = "Loaded ${preset.name} for $role.", error = null) } }
                .onFailure { error -> update { copy(isBusy = false, error = error.message ?: "Failed to load LLM preset.") } }
        }
    }

    fun unloadLlmPreset(presetId: String) {
        scope.launch {
            llmWorkerPool.unloadPreset(presetId)
                .onSuccess { update { copy(message = "LLM model unloaded.", error = null) } }
                .onFailure { error -> update { copy(error = error.message ?: "Failed to unload LLM model.") } }
        }
    }

    fun stopLlmWorker(workerId: String) {
        scope.launch {
            llmWorkerPool.stopWorker(workerId)
                .onSuccess { update { copy(message = "LLM worker stopped.", error = null) } }
                .onFailure { error -> update { copy(error = error.message ?: "Failed to stop LLM worker.") } }
        }
    }

    fun stopAllLlmWorkers() {
        scope.launch {
            llmWorkerPool.stopAll()
            update { copy(message = "Stopped all LLM workers.", error = null) }
        }
    }

    fun tagNextImage() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole("tagging")
            if (preset == null) {
                update { copy(error = "Select a tagging LLM preset first.") }
                return@launch
            }
            update { copy(isBusy = true, message = "Tagging next image...", error = null) }
            taggingService.tagNextImage(settings, preset)
                .onSuccess { result ->
                    update {
                        copy(
                            isBusy = false,
                            message = "Tagged ${result.imageName}: ${result.tags.joinToString(", ")}",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update { copy(isBusy = false, error = error.message ?: "Failed to tag image.") }
                }
        }
    }

    fun tagAllPendingImages() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole("tagging")
            if (preset == null) {
                update { copy(error = "Select a tagging LLM preset first.") }
                return@launch
            }
            update { copy(isBusy = true, message = "Tagging untagged gallery images...", error = null) }
            taggingService.tagPendingImages(settings, preset, maxItems = Int.MAX_VALUE, refreshOutputIndex = true)
                .onSuccess { result ->
                    update {
                        copy(
                            isBusy = false,
                            message = "Tagged ${result.completed} image(s). ${result.failed} failed.",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update { copy(isBusy = false, error = error.message ?: "Failed to tag gallery.") }
                }
        }
    }

    fun applySettingsToBackend() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            applySettingsToBackendInternal(settings)
        }
    }

    fun loadConfigFromBackend() {
        scope.launch {
            loadConfigFromBackendInternal()
        }
    }

    private suspend fun applySettingsToBackendInternal(settings: DesktopSettings) {
        if (backendState.value.status != BackendStatus.Ready) {
            update { copy(message = "Image worker is not ready yet.") }
            return
        }

        val result = client.updateConfig(backendState.value.baseUrl, settings)
        if (result.isSuccess) {
            update { copy(message = "Settings applied to image worker.", error = null) }
        } else {
            update { copy(error = result.exceptionOrNull()?.message ?: "Failed to apply image worker settings.") }
        }
    }

    private suspend fun loadConfigFromBackendInternal() {
        if (backendState.value.status != BackendStatus.Ready) {
            update { copy(message = "Image worker is not ready yet.") }
            return
        }

        val result = client.fetchConfig(backendState.value.baseUrl)
        result.onSuccess { config ->
            update {
                copy(
                    modelDir = config.modelDir.ifBlank { modelDir },
                    outputDir = config.outputDir.ifBlank { outputDir },
                    setupCompleted = config.setupCompleted,
                    message = "Loaded image worker config.",
                    error = null,
                )
            }
            currentSettingsOrNull()?.let(store::save)
        }.onFailure { error ->
            update { copy(error = error.message ?: "Failed to load image worker config.") }
        }
    }

    private fun currentSettingsOrReport(): DesktopSettings? {
        return runCatching { currentSettings() }
            .onFailure { error -> update { copy(error = error.message ?: "Invalid settings.") } }
            .getOrNull()
    }

    private fun currentSettingsOrNull(): DesktopSettings? = runCatching { currentSettings() }.getOrNull()

    private fun currentSettings(): DesktopSettings {
        val state = _uiState.value
        val port = state.listenPort.toIntOrNull() ?: error("Listen port must be a number.")
        require(state.repoRoot.isNotBlank()) { "Repo root is required." }
        require(state.modelDir.isNotBlank()) { "Model directory is required." }
        require(state.outputDir.isNotBlank()) { "Output directory is required." }
        return DesktopSettings(
            repoRoot = state.repoRoot.trim(),
            listenPort = port,
            modelDir = state.modelDir.trim(),
            outputDir = state.outputDir.trim(),
            setupCompleted = state.setupCompleted,
            themeMode = state.themeMode,
            actionBarPosition = state.actionBarPosition,
            saveImagesAutomatically = state.saveImagesAutomatically,
            galleryPreviewWidthDp = state.galleryPreviewWidthDp.coerceIn(320, 760),
        )
    }

    private fun presetForRole(role: String): LlmPreset? {
        val state = _uiState.value
        val presetId = when (role) {
            "tagging" -> state.llmRoles.taggingPresetId
            "assistant" -> state.llmRoles.assistantPresetId
            "prompt enhancement" -> state.llmRoles.promptEnhancerPresetId
            "promptEnhancer" -> state.llmRoles.promptEnhancerPresetId
            else -> ""
        }
        return state.llmPresets.firstOrNull { it.id == presetId }
    }

    private fun updateRoles(transform: LlmRoleSettings.() -> LlmRoleSettings) {
        val next = _uiState.value.llmRoles.transform().sanitizeFor(_uiState.value.llmPresets)
        llmPresetStore.saveRoles(next)
        update { copy(llmRoles = next, message = "Saved LLM role settings.", error = null) }
    }

    private fun LlmRoleSettings.sanitizeFor(presets: List<LlmPreset>): LlmRoleSettings {
        val ids = presets.map { it.id }.toSet()
        return copy(
            taggingPresetId = taggingPresetId.takeIf { it in ids }.orEmpty(),
            assistantPresetId = assistantPresetId.takeIf { it in ids }.orEmpty(),
            promptEnhancerPresetId = promptEnhancerPresetId.takeIf { it in ids }.orEmpty(),
        )
    }

    private fun DesktopSettings.toUiState() = SettingsUiState(
        repoRoot = repoRoot,
        listenPort = listenPort.toString(),
        modelDir = modelDir,
        outputDir = outputDir,
        setupCompleted = setupCompleted,
        themeMode = themeMode,
        actionBarPosition = actionBarPosition,
        saveImagesAutomatically = saveImagesAutomatically,
        galleryPreviewWidthDp = galleryPreviewWidthDp,
    )

    private fun update(transform: SettingsUiState.() -> SettingsUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun updateAndSave(transform: SettingsUiState.() -> SettingsUiState) {
        update(transform)
        currentSettingsOrNull()?.let(store::save)
    }
}
