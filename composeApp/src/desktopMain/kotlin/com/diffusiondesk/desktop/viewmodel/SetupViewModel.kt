package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.CommandLineArgs
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

enum class SetupLlmRole {
    Tagging,
    PromptEnhancement,
    Assistant,
}

data class SetupUiState(
    val step: Int = 1,
    val modelDir: String,
    val outputDir: String,
    val imageForm: ImagePresetForm = ImagePresetForm(name = "Starter Image"),
    val enableTaggingLlmPreset: Boolean = false,
    val taggingLlmForm: LlmPresetForm = LlmPresetForm(name = "Image Tagging", placement = LlmPlacement.Auto),
    val enablePromptEnhancerLlmPreset: Boolean = false,
    val promptEnhancerLlmForm: LlmPresetForm = LlmPresetForm(name = "Prompt Enhancement", placement = LlmPlacement.Auto),
    val enableAssistantLlmPreset: Boolean = false,
    val assistantLlmForm: LlmPresetForm = LlmPresetForm(name = "Local Assistant", placement = LlmPlacement.Auto),
    val imageModels: List<SetupModelOption> = emptyList(),
    val vaeModels: List<SetupModelOption> = emptyList(),
    val textEncoderModels: List<SetupModelOption> = emptyList(),
    val llmModels: List<SetupModelOption> = emptyList(),
    val mmprojModels: List<SetupModelOption> = emptyList(),
    val isScanning: Boolean = false,
    val isFinishing: Boolean = false,
    val message: String = "",
    val error: String? = null,
) {
    val canContinueFromFolders: Boolean get() = modelDir.isNotBlank() && outputDir.isNotBlank() && !isScanning
    val canFinish: Boolean get() = imageForm.diffusionModel.isNotBlank() && !isFinishing
}

class SetupViewModel(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val imagePresetStore: ImagePresetStore,
    private val llmPresetStore: LlmPresetStore,
    private val notifications: NotificationCenter,
) {
    private var initialSettings = settingsStore.load()
    private val _uiState = MutableStateFlow(
        SetupUiState(
            modelDir = initialSettings.modelDir,
            outputDir = initialSettings.outputDir,
        ),
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateModelDir(value: String) = update { copy(modelDir = value, error = null) }
    fun updateOutputDir(value: String) = update { copy(outputDir = value, error = null) }
    fun updateImagePresetForm(value: ImagePresetForm) = update { copy(imageForm = value, error = null) }
    fun updateEnableTaggingLlmPreset(value: Boolean) = update { copy(enableTaggingLlmPreset = value, error = null) }
    fun updateTaggingLlmPresetForm(value: LlmPresetForm) = update { copy(taggingLlmForm = value, error = null) }
    fun updateEnablePromptEnhancerLlmPreset(value: Boolean) = update { copy(enablePromptEnhancerLlmPreset = value, error = null) }
    fun updatePromptEnhancerLlmPresetForm(value: LlmPresetForm) = update { copy(promptEnhancerLlmForm = value, error = null) }
    fun updateEnableAssistantLlmPreset(value: Boolean) = update { copy(enableAssistantLlmPreset = value, error = null) }
    fun updateAssistantLlmPresetForm(value: LlmPresetForm) = update { copy(assistantLlmForm = value, error = null) }
    fun goBack() = update { copy(step = (step - 1).coerceAtLeast(1), error = null) }

    fun continueFromImagePreset() {
        val state = _uiState.value
        if (state.imageForm.diffusionModel.isBlank()) {
            update { copy(error = "Select an image model before continuing.") }
            return
        }
        update { copy(step = 3, error = null, message = "") }
    }

    fun continueFromLlmStep(role: SetupLlmRole) {
        val state = _uiState.value
        if (!state.validateLlmRole(role)) {
            return
        }
        update { copy(step = nextStep(role), error = null, message = "") }
    }

    fun skipLlmStep(role: SetupLlmRole) {
        update {
            when (role) {
                SetupLlmRole.Tagging -> copy(enableTaggingLlmPreset = false, step = nextStep(role), error = null, message = "")
                SetupLlmRole.PromptEnhancement -> copy(enablePromptEnhancerLlmPreset = false, step = nextStep(role), error = null, message = "")
                SetupLlmRole.Assistant -> copy(enableAssistantLlmPreset = false, error = null, message = "")
            }
        }
    }

    fun reloadFromSettings() {
        initialSettings = settingsStore.load()
        update {
            SetupUiState(
                modelDir = initialSettings.modelDir,
                outputDir = initialSettings.outputDir,
            )
        }
    }

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
                val imageIds = result.imageModels.map(SetupModelOption::id)
                val llmIds = result.llmModels.map(SetupModelOption::id)
                val mmprojIds = result.mmprojModels.map(SetupModelOption::id)
                fun LlmPresetForm.withScannedDefaults(defaultProjector: Boolean): LlmPresetForm = copy(
                    modelPath = modelPath.takeIf { it in llmIds }
                        ?: result.llmModels.firstOrNull()?.id.orEmpty(),
                    mmprojPath = mmprojPath.takeIf { it in mmprojIds }
                        ?: if (defaultProjector) result.mmprojModels.firstOrNull()?.id.orEmpty() else "",
                )
                update {
                    copy(
                        step = 2,
                        imageModels = result.imageModels,
                        vaeModels = result.vaeModels,
                        textEncoderModels = result.textEncoderModels,
                        llmModels = result.llmModels,
                        mmprojModels = result.mmprojModels,
                        imageForm = imageForm.copy(
                            diffusionModel = imageForm.diffusionModel.takeIf { it in imageIds }
                                ?: result.imageModels.firstOrNull()?.id.orEmpty(),
                        ),
                        taggingLlmForm = taggingLlmForm.withScannedDefaults(defaultProjector = true),
                        promptEnhancerLlmForm = promptEnhancerLlmForm.withScannedDefaults(defaultProjector = false),
                        assistantLlmForm = assistantLlmForm.withScannedDefaults(defaultProjector = false),
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
            val imageForm = state.imageForm
            if (imageForm.diffusionModel.isBlank()) {
                update { copy(error = "Select an image model before finishing setup.") }
                return@launch
            }
            if (!state.validateLlmRole(SetupLlmRole.Tagging) ||
                !state.validateLlmRole(SetupLlmRole.PromptEnhancement) ||
                !state.validateLlmRole(SetupLlmRole.Assistant)
            ) {
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
                        id = uniqueImagePresetId(slugify(imageForm.name.ifBlank { "Starter Image" })),
                        name = imageForm.name.trim().ifBlank { "Starter Image" },
                        diffusionModel = imageForm.diffusionModel,
                        uncondDiffusionModel = imageForm.uncondDiffusionModel.trim(),
                        vae = imageForm.vae.trim(),
                        clipL = imageForm.clipL.trim(),
                        clipG = imageForm.clipG.trim(),
                        t5xxl = imageForm.t5xxl.trim(),
                        llm = imageForm.llm.trim(),
                        clipOnCpu = imageForm.clipOnCpu,
                        vaeOnCpu = imageForm.vaeOnCpu,
                        offloadParamsToCpu = imageForm.offloadParamsToCpu || imageForm.streamLayers,
                        flashAttention = imageForm.flashAttention,
                        maxVramGb = if (imageForm.useGlobalVramBudget) 0.0 else parseVramBudget(imageForm.maxVramGb),
                        streamLayers = imageForm.streamLayers,
                        promptMode = imageForm.promptMode,
                        defaultWidth = parseIntRange(imageForm.defaultWidth, "Width", 64, 4096),
                        defaultHeight = parseIntRange(imageForm.defaultHeight, "Height", 64, 4096),
                        defaultSteps = parseIntRange(imageForm.defaultSteps, "Steps", 1, 200),
                        defaultCfgScale = imageForm.defaultCfgScale.toDoubleOrNull() ?: error("CFG scale must be numeric."),
                        defaultSampler = imageForm.defaultSampler.trim().ifBlank { "euler_a" },
                        defaultNegativePrompt = imageForm.defaultNegativePrompt.trim(),
                    )
                    imagePresetStore.save(imagePreset)
                    imagePresetStore.saveLastPresetId(imagePreset.id)

                    var roles = llmPresetStore.loadRoles()
                    if (state.enableTaggingLlmPreset) {
                        val presetId = saveLlmPreset(state.taggingLlmForm, "Image Tagging")
                        roles = roles.copy(taggingPresetId = presetId)
                    }
                    if (state.enablePromptEnhancerLlmPreset) {
                        val presetId = saveLlmPreset(state.promptEnhancerLlmForm, "Prompt Enhancement")
                        roles = roles.copy(promptEnhancerPresetId = presetId)
                    }
                    if (state.enableAssistantLlmPreset) {
                        val presetId = saveLlmPreset(state.assistantLlmForm, "Local Assistant")
                        roles = roles.copy(assistantPresetId = presetId)
                    }
                    if (state.enableTaggingLlmPreset || state.enablePromptEnhancerLlmPreset || state.enableAssistantLlmPreset) {
                        llmPresetStore.saveRoles(
                            roles,
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

    private fun saveLlmPreset(form: LlmPresetForm, fallbackName: String): String {
        val preset = LlmPreset(
            id = uniqueLlmPresetId(slugify(form.name.ifBlank { fallbackName })),
            name = form.name.trim().ifBlank { fallbackName },
            modelPath = form.modelPath,
            mmprojPath = form.mmprojPath,
            placement = form.placement,
            advancedArgs = validateAdvancedArgs(form.advancedArgs),
        )
        llmPresetStore.save(preset)
        return preset.id
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
        val vaeModels = mutableListOf<SetupModelOption>()
        val textEncoderModels = mutableListOf<SetupModelOption>()
        val llmModels = mutableListOf<SetupModelOption>()
        val mmprojModels = mutableListOf<SetupModelOption>()

        scanFolder(root, "stable-diffusion", "stable-diffusion", imageModels)
        scanFolder(root, "diffusion_models", "diffusion_models", imageModels)
        scanFolder(root, "unet", "unet", imageModels)
        scanRootImageModels(root, imageModels)
        scanFolder(root, "vae", "vae", vaeModels)
        scanFolder(root, "text-encoder", "text-encoder", textEncoderModels)
        scanFolder(root, "text_encoders", "text_encoders", textEncoderModels)
        scanFolder(root, "clip", "clip", textEncoderModels)
        scanFolder(root, "llm", "llm", llmModels)
        scanFolder(root, "text-encoder", "text-encoder", llmModels)
        scanFolder(root, "text_encoders", "text_encoders", llmModels)
        scanFolder(root, "mmproj", "mmproj", mmprojModels)
        scanMmprojCandidates(root.resolveChildIgnoreCase("llm"), mmprojModels)

        return SetupScanResult(
            imageModels = imageModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            vaeModels = vaeModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            textEncoderModels = textEncoderModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
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

    private fun parseIntRange(value: String, label: String, min: Int, max: Int): Int =
        value.toIntOrNull()?.coerceIn(min, max) ?: error("$label must be numeric.")

    private fun parseVramBudget(value: String): Double =
        value.toDoubleOrNull()?.takeIf { it in 1.0..128.0 } ?: error("VRAM budget must be between 1 and 128 GiB.")

    private fun validateAdvancedArgs(value: String): String {
        val args = CommandLineArgs.parse(value).getOrThrow()
        CommandLineArgs.validateNoReservedOptions(args).getOrThrow()
        return value.trim()
    }

    private fun SetupUiState.validateLlmRole(role: SetupLlmRole): Boolean {
        val enabled = isLlmRoleEnabled(role)
        if (!enabled) return true

        val form = llmFormFor(role)
        val label = role.displayName
        if (form.modelPath.isBlank()) {
            update { copy(step = role.step, error = "Choose an LLM model for $label, or skip this step.") }
            return false
        }
        if (role == SetupLlmRole.Tagging && form.mmprojPath.isBlank()) {
            update { copy(step = role.step, error = "Choose a vision projector for image tagging, or skip this step.") }
            return false
        }
        val argsError = runCatching { validateAdvancedArgs(form.advancedArgs) }.exceptionOrNull()
        if (argsError != null) {
            update { copy(step = role.step, error = argsError.message ?: "Invalid advanced llama.cpp arguments.") }
            return false
        }
        return true
    }

    private fun SetupUiState.isLlmRoleEnabled(role: SetupLlmRole): Boolean =
        when (role) {
            SetupLlmRole.Tagging -> enableTaggingLlmPreset
            SetupLlmRole.PromptEnhancement -> enablePromptEnhancerLlmPreset
            SetupLlmRole.Assistant -> enableAssistantLlmPreset
        }

    private fun SetupUiState.llmFormFor(role: SetupLlmRole): LlmPresetForm =
        when (role) {
            SetupLlmRole.Tagging -> taggingLlmForm
            SetupLlmRole.PromptEnhancement -> promptEnhancerLlmForm
            SetupLlmRole.Assistant -> assistantLlmForm
        }

    private fun nextStep(role: SetupLlmRole): Int =
        when (role) {
            SetupLlmRole.Tagging -> 4
            SetupLlmRole.PromptEnhancement -> 5
            SetupLlmRole.Assistant -> 5
        }

    private fun update(transform: SetupUiState.() -> SetupUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private data class SetupScanResult(
        val imageModels: List<SetupModelOption>,
        val vaeModels: List<SetupModelOption>,
        val textEncoderModels: List<SetupModelOption>,
        val llmModels: List<SetupModelOption>,
        val mmprojModels: List<SetupModelOption>,
    )

    private companion object {
        val modelExtensions = setOf("gguf", "safetensors", "ckpt", "pth", "pt", "bin")
        const val maxScanResults = 500
    }
}

val SetupLlmRole.step: Int
    get() = when (this) {
        SetupLlmRole.Tagging -> 3
        SetupLlmRole.PromptEnhancement -> 4
        SetupLlmRole.Assistant -> 5
    }

val SetupLlmRole.displayName: String
    get() = when (this) {
        SetupLlmRole.Tagging -> "Image Tagging"
        SetupLlmRole.PromptEnhancement -> "Prompt Enhancement"
        SetupLlmRole.Assistant -> "Assistant"
    }
