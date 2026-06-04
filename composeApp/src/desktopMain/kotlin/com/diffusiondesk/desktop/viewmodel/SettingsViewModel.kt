package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.BackendUiState
import com.diffusiondesk.desktop.core.DesktopSettings
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
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
    val isBusy: Boolean = false,
    val message: String = "",
    val error: String? = null,
)

class SettingsViewModel(
    private val scope: CoroutineScope,
    private val store: DesktopSettingsStore,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
) {
    private val _uiState = MutableStateFlow(store.load().toUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val backendState: StateFlow<BackendUiState> = backendManager.state

    fun updateRepoRoot(value: String) = update { copy(repoRoot = value) }
    fun updateListenPort(value: String) = update { copy(listenPort = value) }
    fun updateModelDir(value: String) = update { copy(modelDir = value) }
    fun updateOutputDir(value: String) = update { copy(outputDir = value) }
    fun updateSetupCompleted(value: Boolean) = update { copy(setupCompleted = value) }
    fun updateThemeMode(value: String) = updateAndSave { copy(themeMode = value) }
    fun updateActionBarPosition(value: String) = updateAndSave { copy(actionBarPosition = value) }
    fun updateSaveImagesAutomatically(value: Boolean) = updateAndSave { copy(saveImagesAutomatically = value) }

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
    )

    private fun update(transform: SettingsUiState.() -> SettingsUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun updateAndSave(transform: SettingsUiState.() -> SettingsUiState) {
        update(transform)
        currentSettingsOrNull()?.let(store::save)
    }
}
