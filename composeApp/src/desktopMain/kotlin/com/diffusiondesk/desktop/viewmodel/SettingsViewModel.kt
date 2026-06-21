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
import com.diffusiondesk.desktop.core.NotificationCenter
import com.diffusiondesk.desktop.core.detectDefaultModelDir
import com.diffusiondesk.desktop.core.detectDefaultOutputDir
import com.diffusiondesk.desktop.core.detectDefaultRepoRoot
import com.diffusiondesk.desktop.core.normalizeConfiguredPath
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
    val showCompositionOverlay: Boolean,
    val showLlmDebugConsole: Boolean,
    val vramBudgetMode: String,
    val manualVramBudgetGb: String,
    val autostartLlmWorkers: Boolean,
    val galleryPreviewWidthDp: Int,
    val llmPresets: List<LlmPreset> = emptyList(),
    val llmRoles: LlmRoleSettings = LlmRoleSettings(),
    val llmWorkers: List<LlmWorkerState> = emptyList(),
    val isBusy: Boolean = false,
    val isTaggingGallery: Boolean = false,
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
    private val notifications: NotificationCenter,
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
    fun updateShowCompositionOverlay(value: Boolean) = updateAndSave { copy(showCompositionOverlay = value) }
    fun updateShowLlmDebugConsole(value: Boolean) = updateAndSave { copy(showLlmDebugConsole = value) }
    fun updateVramBudgetMode(value: String) = updateAndSave { copy(vramBudgetMode = value) }
    fun updateManualVramBudgetGb(value: String) = update { copy(manualVramBudgetGb = value) }
    fun updateAutostartLlmWorkers(value: Boolean) = updateAndSave { copy(autostartLlmWorkers = value) }
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

    fun reloadLocalSettings() {
        val settings = store.load()
        update {
            settings.toUiState().copy(
                llmPresets = llmPresetStore.load(),
                llmRoles = llmPresetStore.loadRoles(),
                llmWorkers = llmWorkers,
            )
        }
    }

    fun saveLocalSettings() {
        runCatching { currentSettings() }
            .onSuccess {
                store.save(it)
                notifications.success("Saved desktop settings.")
                update {
                    copy(
                        repoRoot = it.repoRoot,
                        listenPort = it.listenPort.toString(),
                        modelDir = it.modelDir,
                        outputDir = it.outputDir,
                        manualVramBudgetGb = it.manualVramBudgetGb.toString(),
                        galleryPreviewWidthDp = it.galleryPreviewWidthDp,
                        message = "",
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                val message = error.message ?: "Invalid settings."
                notifications.error(message)
                update { copy(error = message) }
            }
    }

    fun startBackend() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            store.save(settings)
            notifications.info("Starting image worker...")
            update { copy(isBusy = true, message = "", error = null) }

            val startResult = backendManager.start(settings)
            if (startResult.isSuccess) {
                applySettingsToBackendInternal(settings, notify = false)
                loadConfigFromBackendInternal(notify = false)
                notifications.success("Image worker started.")
                update { copy(isBusy = false, message = "", error = null) }
            } else {
                val message = startResult.exceptionOrNull()?.message ?: "Failed to start image worker."
                notifications.error(message)
                update {
                    copy(
                        isBusy = false,
                        error = message,
                    )
                }
            }
        }
    }

    fun stopBackend() {
        scope.launch {
            notifications.info("Stopping image worker...")
            update { copy(isBusy = true, message = "", error = null) }
            backendManager.stop()
            notifications.success("Image worker stopped.")
            update { copy(isBusy = false, message = "", error = null) }
        }
    }

    fun autostartLlmWorkersIfEnabled() {
        scope.launch {
            val settings = currentSettingsOrNull() ?: return@launch
            if (!settings.autostartLlmWorkers) {
                return@launch
            }

            val presets = llmPresetStore.load()
            val roles = llmPresetStore.loadRoles().sanitizeFor(presets)
            val presetIds = listOf(
                roles.taggingPresetId,
                roles.assistantPresetId,
                roles.promptEnhancerPresetId,
            ).filter(String::isNotBlank).distinct()

            if (presetIds.isEmpty()) {
                notifications.warning("LLM autostart is enabled, but no LLM roles are configured.")
                return@launch
            }

            update { copy(isBusy = true, message = "", error = null) }
            var started = 0
            var failureMessage: String? = null
            presetIds.forEach { presetId ->
                val preset = presets.firstOrNull { it.id == presetId } ?: return@forEach
                llmWorkerPool.ensureWorkerForPreset(settings, preset)
                    .onSuccess { started += 1 }
                    .onFailure { error ->
                        failureMessage = error.message ?: "Failed to autostart ${preset.name}."
                    }
            }

            val message = failureMessage
            when {
                message != null -> {
                    notifications.error(message)
                    update { copy(isBusy = false, error = message) }
                }
                started > 0 -> {
                    notifications.success("Started $started LLM worker(s).")
                    update { copy(isBusy = false, message = "", error = null) }
                }
                else -> update { copy(isBusy = false, message = "", error = null) }
            }
        }
    }

    fun unloadImageModel() {
        scope.launch {
            if (backendState.value.status != BackendStatus.Ready) {
                notifications.warning("Image worker is not ready yet.")
                return@launch
            }
            notifications.info("Unloading image model...")
            update { copy(isBusy = true, message = "", error = null) }
            client.unloadImageModel(backendState.value.baseUrl)
                .onSuccess {
                    notifications.success("Image model unloaded.")
                    update { copy(isBusy = false, message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to unload image model."
                    notifications.error(message)
                    update { copy(isBusy = false, error = message) }
                }
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
                    message = "",
                    error = null,
                )
            }
            llmPresetStore.saveRoles(_uiState.value.llmRoles)
            notifications.success("Loaded ${presets.size} LLM preset(s).")
        }.onFailure { error ->
            val message = error.message ?: "Failed to load LLM presets."
            notifications.error(message)
            update { copy(error = message) }
        }
    }

    fun loadLlmRole(role: String) {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole(role)
            if (preset == null) {
                val message = "Select an LLM preset for $role first."
                notifications.warning(message)
                update { copy(error = message) }
                return@launch
            }
            notifications.info("Loading ${preset.name} for $role...")
            update { copy(isBusy = true, message = "", error = null) }
            llmWorkerPool.ensureWorkerForPreset(settings, preset)
                .onSuccess {
                    notifications.success("Loaded ${preset.name} for $role.")
                    update { copy(isBusy = false, message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to load LLM preset."
                    notifications.error(message)
                    update { copy(isBusy = false, error = message) }
                }
        }
    }

    fun unloadLlmPreset(presetId: String) {
        scope.launch {
            llmWorkerPool.unloadPreset(presetId)
                .onSuccess {
                    notifications.success("LLM model unloaded.")
                    update { copy(message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to unload LLM model."
                    notifications.error(message)
                    update { copy(error = message) }
                }
        }
    }

    fun stopLlmWorker(workerId: String) {
        scope.launch {
            llmWorkerPool.stopWorker(workerId)
                .onSuccess {
                    notifications.success("LLM worker stopped.")
                    update { copy(message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to stop LLM worker."
                    notifications.error(message)
                    update { copy(error = message) }
                }
        }
    }

    fun stopAllLlmWorkers() {
        scope.launch {
            llmWorkerPool.stopAll()
            notifications.success("Stopped all LLM workers.")
            update { copy(message = "", error = null) }
        }
    }

    fun tagNextImage() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole("tagging")
            if (preset == null) {
                val message = "Select a tagging LLM preset first."
                notifications.warning(message)
                update { copy(error = message) }
                return@launch
            }
            notifications.info("Tagging next image...")
            update { copy(isBusy = true, message = "", error = null) }
            taggingService.tagNextImage(settings, preset)
                .onSuccess { result ->
                    notifications.success("Tagged ${result.imageName}: ${result.tags.joinToString(", ")}")
                    update { copy(isBusy = false, message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to tag image."
                    notifications.error(message)
                    update { copy(isBusy = false, error = message) }
                }
        }
    }

    fun tagAllPendingImages() {
        scope.launch {
            val settings = currentSettingsOrReport() ?: return@launch
            val preset = presetForRole("tagging")
            if (preset == null) {
                val message = "Select a tagging LLM preset first."
                notifications.warning(message)
                update { copy(error = message) }
                return@launch
            }
            notifications.info("Generating gallery tags...")
            update { copy(isBusy = true, isTaggingGallery = true, message = "", error = null) }
            taggingService.tagPendingImages(settings, preset, maxItems = Int.MAX_VALUE, refreshOutputIndex = true)
                .onSuccess { result ->
                    notifications.success("Tagged ${result.completed} image(s). ${result.failed} failed.")
                    update { copy(isBusy = false, isTaggingGallery = false, message = "", error = null) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Failed to tag gallery."
                    notifications.error(message)
                    update { copy(isBusy = false, isTaggingGallery = false, error = message) }
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

    private suspend fun applySettingsToBackendInternal(settings: DesktopSettings, notify: Boolean = true) {
        if (backendState.value.status != BackendStatus.Ready) {
            if (notify) notifications.warning("Image worker is not ready yet.")
            return
        }

        val result = client.updateConfig(backendState.value.baseUrl, settings)
        if (result.isSuccess) {
            if (notify) notifications.success("Settings applied to image worker.")
            update { copy(message = "", error = null) }
        } else {
            val message = result.exceptionOrNull()?.message ?: "Failed to apply image worker settings."
            if (notify) notifications.error(message)
            update { copy(error = message) }
        }
    }

    private suspend fun loadConfigFromBackendInternal(notify: Boolean = true) {
        if (backendState.value.status != BackendStatus.Ready) {
            if (notify) notifications.warning("Image worker is not ready yet.")
            return
        }

        val result = client.fetchConfig(backendState.value.baseUrl)
        result.onSuccess { config ->
            update {
                copy(
                    modelDir = config.modelDir.ifBlank { modelDir },
                    outputDir = config.outputDir.ifBlank { outputDir },
                    setupCompleted = config.setupCompleted,
                    message = "",
                    error = null,
                )
            }
            currentSettingsOrNull()?.let(store::save)
            if (notify) notifications.success("Loaded image worker config.")
        }.onFailure { error ->
            val message = error.message ?: "Failed to load image worker config."
            if (notify) notifications.error(message)
            update { copy(error = message) }
        }
    }

    private fun currentSettingsOrReport(): DesktopSettings? {
        return runCatching { currentSettings() }
            .onFailure { error ->
                val message = error.message ?: "Invalid settings."
                notifications.error(message)
                update { copy(error = message) }
            }
            .getOrNull()
    }

    private fun currentSettingsOrNull(): DesktopSettings? = runCatching { currentSettings() }.getOrNull()

    private fun currentSettings(): DesktopSettings {
        val state = _uiState.value
        val port = state.listenPort.toIntOrNull() ?: error("Listen port must be a number.")
        require(state.repoRoot.isNotBlank()) { "Repo root is required." }
        require(state.modelDir.isNotBlank()) { "Model directory is required." }
        require(state.outputDir.isNotBlank()) { "Output directory is required." }
        require(state.vramBudgetMode in setOf("auto", "manual")) { "VRAM budget mode is invalid." }
        val parsedManualVramBudgetGb = state.manualVramBudgetGb.toDoubleOrNull()
        if (state.vramBudgetMode == "manual") {
            require(parsedManualVramBudgetGb != null && parsedManualVramBudgetGb in 1.0..128.0) {
                "Manual VRAM budget must be between 1 and 128 GiB."
            }
        }
        val manualVramBudgetGb = parsedManualVramBudgetGb?.coerceIn(1.0, 128.0) ?: 12.0
        return DesktopSettings(
            repoRoot = state.repoRoot.trim(),
            listenPort = port,
            modelDir = normalizeConfiguredPath(state.repoRoot.trim(), state.modelDir),
            outputDir = normalizeConfiguredPath(state.repoRoot.trim(), state.outputDir),
            setupCompleted = state.setupCompleted,
            themeMode = state.themeMode,
            actionBarPosition = state.actionBarPosition,
            saveImagesAutomatically = state.saveImagesAutomatically,
            showCompositionOverlay = state.showCompositionOverlay,
            showLlmDebugConsole = state.showLlmDebugConsole,
            vramBudgetMode = state.vramBudgetMode,
            manualVramBudgetGb = manualVramBudgetGb,
            autostartLlmWorkers = state.autostartLlmWorkers,
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
        update { copy(llmRoles = next, message = "", error = null) }
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
        showCompositionOverlay = showCompositionOverlay,
        showLlmDebugConsole = showLlmDebugConsole,
        vramBudgetMode = vramBudgetMode,
        manualVramBudgetGb = manualVramBudgetGb.toString(),
        autostartLlmWorkers = autostartLlmWorkers,
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
