package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.BackendStatus
import com.diffusiondesk.desktop.core.CommandLineArgs
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.core.LlmPlacement
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.ModelSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class LibraryMode {
    List,
    Editor,
    LlmList,
    LlmEditor,
}

data class ImagePresetForm(
    val name: String = "",
    val diffusionModel: String = "",
    val uncondDiffusionModel: String = "",
    val vae: String = "",
    val clipL: String = "",
    val clipG: String = "",
    val t5xxl: String = "",
    val llm: String = "",
    val clipOnCpu: Boolean = false,
    val vaeOnCpu: Boolean = false,
    val offloadParamsToCpu: Boolean = false,
    val flashAttention: Boolean = false,
    val streamLayers: Boolean = false,
    val promptMode: ImagePromptMode = ImagePromptMode.Text,
    val defaultWidth: String = "1024",
    val defaultHeight: String = "1024",
    val defaultSteps: String = "4",
    val defaultCfgScale: String = "1.0",
    val defaultSampler: String = "euler_a",
    val defaultNegativePrompt: String = "deformed, blurry, low quality, watermark",
)

data class LlmPresetForm(
    val name: String = "",
    val modelPath: String = "",
    val mmprojPath: String = "",
    val placement: LlmPlacement = LlmPlacement.Cpu,
    val advancedArgs: String = "",
)

data class LibraryUiState(
    val mode: LibraryMode = LibraryMode.List,
    val presets: List<ImagePreset> = emptyList(),
    val llmPresets: List<LlmPreset> = emptyList(),
    val modelSuggestions: List<ModelSummary> = emptyList(),
    val editingPresetId: String? = null,
    val editingLlmPresetId: String? = null,
    val form: ImagePresetForm = ImagePresetForm(),
    val llmForm: LlmPresetForm = LlmPresetForm(),
    val message: String = "",
    val error: String? = null,
) {
    val isEditing: Boolean get() = editingPresetId != null
    val isEditingLlm: Boolean get() = editingLlmPresetId != null
}

class LibraryViewModel(
    private val scope: CoroutineScope,
    private val presetStore: ImagePresetStore,
    private val llmPresetStore: LlmPresetStore,
    private val backendManager: BackendManager,
    private val client: DiffusionDeskClient,
) {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        reloadPresets()
        scope.launch {
            backendManager.state.collectLatest { backend ->
                if (backend.status == BackendStatus.Ready) {
                    reloadModelSuggestions(backend.baseUrl)
                } else {
                    update { copy(modelSuggestions = emptyList()) }
                }
            }
        }
    }

    fun reloadPresets() {
        runCatching { presetStore.load() to llmPresetStore.load() }
            .onSuccess { (presets, llmPresets) ->
                update {
                    copy(
                        presets = presets,
                        llmPresets = llmPresets,
                        message = "Loaded ${presets.size} image preset(s) and ${llmPresets.size} LLM preset(s).",
                        error = null,
                    )
                }
            }
            .onFailure { error -> update { copy(error = error.message ?: "Failed to load presets.") } }
    }

    fun showImagePresets() {
        update { copy(mode = LibraryMode.List, editingLlmPresetId = null, error = null) }
    }

    fun showLlmPresets() {
        update { copy(mode = LibraryMode.LlmList, editingPresetId = null, error = null) }
    }

    fun createPreset() {
        update {
            copy(
                mode = LibraryMode.Editor,
                editingPresetId = null,
                form = ImagePresetForm(),
                message = "",
                error = null,
            )
        }
    }

    fun createLlmPreset() {
        update {
            copy(
                mode = LibraryMode.LlmEditor,
                editingLlmPresetId = null,
                llmForm = LlmPresetForm(),
                message = "",
                error = null,
            )
        }
    }

    fun editPreset(id: String) {
        val preset = _uiState.value.presets.firstOrNull { it.id == id }
        if (preset == null) {
            update { copy(error = "Preset not found: $id") }
            return
        }
        update {
            copy(
                mode = LibraryMode.Editor,
                editingPresetId = preset.id,
                form = preset.toForm(),
                message = "",
                error = null,
            )
        }
    }

    fun editLlmPreset(id: String) {
        val preset = _uiState.value.llmPresets.firstOrNull { it.id == id }
        if (preset == null) {
            update { copy(error = "LLM preset not found: $id") }
            return
        }
        update {
            copy(
                mode = LibraryMode.LlmEditor,
                editingLlmPresetId = preset.id,
                llmForm = preset.toForm(),
                message = "",
                error = null,
            )
        }
    }

    fun cancelEditor() {
        update {
            copy(
                mode = if (mode == LibraryMode.LlmEditor) LibraryMode.LlmList else LibraryMode.List,
                editingPresetId = null,
                editingLlmPresetId = null,
                form = ImagePresetForm(),
                llmForm = LlmPresetForm(),
                error = null,
            )
        }
    }

    fun updateForm(form: ImagePresetForm) {
        update { copy(form = form, error = null) }
    }

    fun updateLlmForm(form: LlmPresetForm) {
        update { copy(llmForm = form, error = null) }
    }

    private suspend fun reloadModelSuggestions(baseUrl: String) {
        client.fetchModels(baseUrl)
            .onSuccess { models -> update { copy(modelSuggestions = models) } }
    }

    fun saveEditor(): Boolean {
        val state = _uiState.value
        val preset = state.form.toPreset(state.editingPresetId, state.presets)
            .getOrElse { error ->
                update { copy(error = error.message ?: "Invalid image preset.") }
                return false
            }

        runCatching {
            if (state.editingPresetId != null && state.editingPresetId != preset.id) {
                presetStore.delete(state.editingPresetId)
            }
            presetStore.save(preset)
            presetStore.load()
        }.onSuccess { presets ->
            update {
                copy(
                    mode = LibraryMode.List,
                    presets = presets,
                    editingPresetId = null,
                    form = ImagePresetForm(),
                    message = "Saved ${preset.name}.",
                    error = null,
                )
            }
            return true
        }.onFailure { error -> update { copy(error = error.message ?: "Failed to save image preset.") } }

        return false
    }

    fun saveLlmEditor(): Boolean {
        val state = _uiState.value
        val preset = state.llmForm.toPreset(state.editingLlmPresetId, state.llmPresets)
            .getOrElse { error ->
                update { copy(error = error.message ?: "Invalid LLM preset.") }
                return false
            }

        runCatching {
            if (state.editingLlmPresetId != null && state.editingLlmPresetId != preset.id) {
                llmPresetStore.delete(state.editingLlmPresetId)
            }
            llmPresetStore.save(preset)
            llmPresetStore.load()
        }.onSuccess { presets ->
            update {
                copy(
                    mode = LibraryMode.LlmList,
                    llmPresets = presets,
                    editingLlmPresetId = null,
                    llmForm = LlmPresetForm(),
                    message = "Saved ${preset.name}.",
                    error = null,
                )
            }
            return true
        }.onFailure { error -> update { copy(error = error.message ?: "Failed to save LLM preset.") } }

        return false
    }

    fun deletePreset(id: String): Boolean {
        val preset = _uiState.value.presets.firstOrNull { it.id == id }
        runCatching {
            presetStore.delete(id)
            presetStore.load()
        }.onSuccess { presets ->
            update {
                copy(
                    presets = presets,
                    message = if (preset == null) "Deleted preset." else "Deleted ${preset.name}.",
                    error = null,
                )
            }
            return true
        }.onFailure { error -> update { copy(error = error.message ?: "Failed to delete image preset.") } }

        return false
    }

    fun deleteLlmPreset(id: String): Boolean {
        val preset = _uiState.value.llmPresets.firstOrNull { it.id == id }
        runCatching {
            llmPresetStore.delete(id)
            llmPresetStore.load()
        }.onSuccess { presets ->
            update {
                copy(
                    llmPresets = presets,
                    message = if (preset == null) "Deleted LLM preset." else "Deleted ${preset.name}.",
                    error = null,
                )
            }
            return true
        }.onFailure { error -> update { copy(error = error.message ?: "Failed to delete LLM preset.") } }

        return false
    }

    private fun ImagePreset.toForm() = ImagePresetForm(
        name = name,
        diffusionModel = diffusionModel,
        uncondDiffusionModel = uncondDiffusionModel,
        vae = vae,
        clipL = clipL,
        clipG = clipG,
        t5xxl = t5xxl,
        llm = llm,
        clipOnCpu = clipOnCpu,
        vaeOnCpu = vaeOnCpu,
        offloadParamsToCpu = offloadParamsToCpu,
        flashAttention = flashAttention,
        streamLayers = streamLayers,
        promptMode = promptMode,
        defaultWidth = defaultWidth.toString(),
        defaultHeight = defaultHeight.toString(),
        defaultSteps = defaultSteps.toString(),
        defaultCfgScale = defaultCfgScale.toString(),
        defaultSampler = defaultSampler,
        defaultNegativePrompt = defaultNegativePrompt,
    )

    private fun LlmPreset.toForm() = LlmPresetForm(
        name = name,
        modelPath = modelPath,
        mmprojPath = mmprojPath,
        placement = placement,
        advancedArgs = advancedArgs,
    )

    private fun ImagePresetForm.toPreset(editingPresetId: String?, existingPresets: List<ImagePreset>): Result<ImagePreset> = runCatching {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Preset name is required." }
        require(diffusionModel.trim().isNotBlank()) { "UNet / main model path is required." }

        val baseId = editingPresetId ?: slugify(trimmedName)
        val id = if (editingPresetId != null) baseId else uniqueId(baseId, existingPresets)

        ImagePreset(
            id = id,
            name = trimmedName,
            diffusionModel = diffusionModel.trim(),
            uncondDiffusionModel = uncondDiffusionModel.trim(),
            vae = vae.trim(),
            clipL = clipL.trim(),
            clipG = clipG.trim(),
            t5xxl = t5xxl.trim(),
            llm = llm.trim(),
            clipOnCpu = clipOnCpu,
            vaeOnCpu = vaeOnCpu,
            offloadParamsToCpu = offloadParamsToCpu,
            flashAttention = flashAttention,
            maxVramGb = 0.0,
            streamLayers = streamLayers,
            promptMode = promptMode,
            defaultWidth = defaultWidth.toIntOrNull()?.coerceIn(64, 4096) ?: error("Width must be numeric."),
            defaultHeight = defaultHeight.toIntOrNull()?.coerceIn(64, 4096) ?: error("Height must be numeric."),
            defaultSteps = defaultSteps.toIntOrNull()?.coerceIn(1, 200) ?: error("Steps must be numeric."),
            defaultCfgScale = defaultCfgScale.toDoubleOrNull() ?: error("CFG scale must be numeric."),
            defaultSampler = defaultSampler.trim().ifBlank { "euler_a" },
            defaultNegativePrompt = defaultNegativePrompt.trim(),
        )
    }

    private fun LlmPresetForm.toPreset(editingPresetId: String?, existingPresets: List<LlmPreset>): Result<LlmPreset> = runCatching {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Preset name is required." }
        require(modelPath.trim().isNotBlank()) { "Model path is required." }
        val args = CommandLineArgs.parse(advancedArgs).getOrThrow()
        CommandLineArgs.validateNoReservedOptions(args).getOrThrow()

        val baseId = editingPresetId ?: slugify(trimmedName)
        val id = if (editingPresetId != null) baseId else uniqueLlmId(baseId, existingPresets)

        LlmPreset(
            id = id,
            name = trimmedName,
            modelPath = modelPath.trim(),
            mmprojPath = mmprojPath.trim(),
            placement = placement,
            advancedArgs = advancedArgs,
        )
    }

    private fun slugify(value: String): String {
        val slug = value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.ifBlank { "preset" }
    }

    private fun uniqueId(baseId: String, existingPresets: List<ImagePreset>): String {
        val existingIds = existingPresets.map { it.id }.toSet()
        if (baseId !in existingIds) return baseId
        var index = 2
        while ("$baseId-$index" in existingIds) index += 1
        return "$baseId-$index"
    }

    private fun uniqueLlmId(baseId: String, existingPresets: List<LlmPreset>): String {
        val existingIds = existingPresets.map { it.id }.toSet()
        if (baseId !in existingIds) return baseId
        var index = 2
        while ("$baseId-$index" in existingIds) index += 1
        return "$baseId-$index"
    }

    private fun update(transform: LibraryUiState.() -> LibraryUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
