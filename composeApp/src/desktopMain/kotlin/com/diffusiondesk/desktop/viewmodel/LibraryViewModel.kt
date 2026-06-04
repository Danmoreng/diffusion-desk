package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.ImagePreset
import com.diffusiondesk.desktop.core.ImagePresetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LibraryMode {
    List,
    Editor,
}

data class ImagePresetForm(
    val name: String = "",
    val diffusionModel: String = "",
    val vae: String = "",
    val clipL: String = "",
    val clipG: String = "",
    val t5xxl: String = "",
    val llm: String = "",
    val clipOnCpu: Boolean = false,
    val vaeOnCpu: Boolean = false,
    val offloadParamsToCpu: Boolean = false,
    val flashAttention: Boolean = false,
    val defaultWidth: String = "1024",
    val defaultHeight: String = "1024",
    val defaultSteps: String = "4",
    val defaultCfgScale: String = "1.0",
    val defaultSampler: String = "euler_a",
    val defaultNegativePrompt: String = "deformed, blurry, low quality, watermark",
)

data class LibraryUiState(
    val mode: LibraryMode = LibraryMode.List,
    val presets: List<ImagePreset> = emptyList(),
    val editingPresetId: String? = null,
    val form: ImagePresetForm = ImagePresetForm(),
    val message: String = "",
    val error: String? = null,
) {
    val isEditing: Boolean get() = editingPresetId != null
}

class LibraryViewModel(
    private val presetStore: ImagePresetStore,
) {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        reloadPresets()
    }

    fun reloadPresets() {
        runCatching { presetStore.load() }
            .onSuccess { presets ->
                update {
                    copy(
                        presets = presets,
                        message = "Loaded ${presets.size} image preset(s) from ${presetStore.presetDir.absolutePath}.",
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                update { copy(error = error.message ?: "Failed to load image presets.") }
            }
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

    fun cancelEditor() {
        update {
            copy(
                mode = LibraryMode.List,
                editingPresetId = null,
                form = ImagePresetForm(),
                error = null,
            )
        }
    }

    fun updateForm(form: ImagePresetForm) {
        update { copy(form = form, error = null) }
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
        }.onFailure { error ->
            update { copy(error = error.message ?: "Failed to save image preset.") }
        }

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
        }.onFailure { error ->
            update { copy(error = error.message ?: "Failed to delete image preset.") }
        }

        return false
    }

    private fun ImagePreset.toForm() = ImagePresetForm(
        name = name,
        diffusionModel = diffusionModel,
        vae = vae,
        clipL = clipL,
        clipG = clipG,
        t5xxl = t5xxl,
        llm = llm,
        clipOnCpu = clipOnCpu,
        vaeOnCpu = vaeOnCpu,
        offloadParamsToCpu = offloadParamsToCpu,
        flashAttention = flashAttention,
        defaultWidth = defaultWidth.toString(),
        defaultHeight = defaultHeight.toString(),
        defaultSteps = defaultSteps.toString(),
        defaultCfgScale = defaultCfgScale.toString(),
        defaultSampler = defaultSampler,
        defaultNegativePrompt = defaultNegativePrompt,
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
            vae = vae.trim(),
            clipL = clipL.trim(),
            clipG = clipG.trim(),
            t5xxl = t5xxl.trim(),
            llm = llm.trim(),
            clipOnCpu = clipOnCpu,
            vaeOnCpu = vaeOnCpu,
            offloadParamsToCpu = offloadParamsToCpu,
            flashAttention = flashAttention,
            defaultWidth = defaultWidth.toIntOrNull()?.coerceIn(64, 4096) ?: error("Width must be numeric."),
            defaultHeight = defaultHeight.toIntOrNull()?.coerceIn(64, 4096) ?: error("Height must be numeric."),
            defaultSteps = defaultSteps.toIntOrNull()?.coerceIn(1, 200) ?: error("Steps must be numeric."),
            defaultCfgScale = defaultCfgScale.toDoubleOrNull() ?: error("CFG scale must be numeric."),
            defaultSampler = defaultSampler.trim().ifBlank { "euler_a" },
            defaultNegativePrompt = defaultNegativePrompt.trim(),
        )
    }

    private fun slugify(value: String): String {
        val slug = value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.ifBlank { "image-preset" }
    }

    private fun uniqueId(baseId: String, existingPresets: List<ImagePreset>): String {
        val existingIds = existingPresets.map { it.id }.toSet()
        if (baseId !in existingIds) {
            return baseId
        }
        var index = 2
        while ("$baseId-$index" in existingIds) {
            index += 1
        }
        return "$baseId-$index"
    }

    private fun update(transform: LibraryUiState.() -> LibraryUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
