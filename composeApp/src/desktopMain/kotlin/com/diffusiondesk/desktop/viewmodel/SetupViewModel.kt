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
    val imageForm: ImagePresetForm = ImagePresetForm(
        name = "Z-Image Turbo Starter",
        defaultSteps = "8",
        defaultCfgScale = "1.0",
        defaultSampler = "euler",
    ),
    val enableTaggingLlmPreset: Boolean = false,
    val taggingLlmForm: LlmPresetForm = LlmPresetForm(name = "Qwen3-VL 4B Image Tagger", placement = LlmPlacement.Auto),
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
    val canFinish: Boolean get() = imageForm.diffusionModel.isNotBlank() && imageForm.vae.isNotBlank() && imageForm.llm.isNotBlank() && !isFinishing
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
    fun startOnboarding() = update { copy(step = 2, error = null, message = "") }
    fun goBack() = update { copy(step = (step - 1).coerceAtLeast(1), error = null) }

    fun continueFromModelGuide() {
        val state = _uiState.value
        when {
            state.imageForm.diffusionModel.isBlank() -> update { copy(error = "Place a Z-Image Turbo GGUF file in the model folder and scan again.") }
            state.imageForm.llm.isBlank() -> update { copy(error = "Place a Qwen3-4B Text Encoder GGUF file in the text encoder folder and scan again.") }
            state.imageForm.vae.isBlank() -> update { copy(error = "Place ae.safetensors in the VAE folder and scan again.") }
            else -> update { copy(step = 4, error = null, message = "") }
        }
    }

    fun continueFromTaggingGuide() {
        val state = _uiState.value
        if (!state.validateLlmRole(SetupLlmRole.Tagging)) {
            return
        }
        update { copy(step = 5, error = null, message = "") }
    }

    fun continueFromImagePreset() {
        val state = _uiState.value
        if (state.imageForm.diffusionModel.isBlank()) {
            update { copy(error = "Select an image model before continuing.") }
            return
        }
        if (state.imageForm.llm.isBlank() || state.imageForm.vae.isBlank()) {
            update { copy(error = "Select the Z-Image text encoder and VAE before continuing.") }
            return
        }
        update { copy(step = 5, error = null, message = "") }
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
                        step = if (current.step >= 3) current.step else 3,
                        imageModels = result.imageModels,
                        vaeModels = result.vaeModels,
                        textEncoderModels = result.textEncoderModels,
                        llmModels = result.llmModels,
                        mmprojModels = result.mmprojModels,
                        imageForm = imageForm.copy(
                            diffusionModel = imageForm.diffusionModel.takeIf { it in imageIds }
                                ?: result.imageModels.preferredZImageTurbo()?.id
                                ?: result.imageModels.firstOrNull()?.id.orEmpty(),
                            vae = imageForm.vae.takeIf { it in result.vaeModels.map(SetupModelOption::id) }
                                ?: result.vaeModels.preferredFluxVae()?.id
                                ?: result.vaeModels.firstOrNull()?.id.orEmpty(),
                            llm = imageForm.llm.takeIf { it in llmIds }
                                ?: result.llmModels.preferredQwen3TextEncoder()?.id
                                ?: result.textEncoderModels.preferredQwen3TextEncoder()?.id
                                ?: result.llmModels.firstOrNull()?.id.orEmpty(),
                        ),
                        taggingLlmForm = taggingLlmForm.copy(
                            modelPath = taggingLlmForm.modelPath.takeIf { it in llmIds }
                                ?: result.llmModels.preferredQwen3VlTagger()?.id
                                ?: result.llmModels.firstOrNull()?.id.orEmpty(),
                            mmprojPath = taggingLlmForm.mmprojPath.takeIf { it in mmprojIds }
                                ?: result.mmprojModels.preferredQwen3VlProjector()?.id
                                ?: result.mmprojModels.firstOrNull()?.id.orEmpty(),
                        ),
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
            if (imageForm.llm.isBlank() || imageForm.vae.isBlank()) {
                update { copy(error = "Select the Z-Image text encoder and VAE before finishing setup.") }
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

        scanFolder(root, "stable-diffusion", "stable-diffusion", imageModels, ggufModelExtensions)
        scanFolder(root, "diffusion_models", "diffusion_models", imageModels, ggufModelExtensions)
        scanFolder(root, "unet", "unet", imageModels, ggufModelExtensions)
        scanRootImageModels(root, imageModels)
        scanFolder(root, "vae", "vae", vaeModels, vaeModelExtensions)
        scanFolder(root, "text-encoder", "text-encoder", textEncoderModels, ggufModelExtensions)
        scanFolder(root, "text_encoders", "text_encoders", textEncoderModels, ggufModelExtensions)
        scanFolder(root, "clip", "clip", textEncoderModels, ggufModelExtensions)
        scanFolder(root, "llm", "llm", llmModels, ggufModelExtensions)
        scanFolder(root, "text-encoder", "text-encoder", llmModels, ggufModelExtensions)
        scanFolder(root, "text_encoders", "text_encoders", llmModels, ggufModelExtensions)
        scanFolder(root, "mmproj", "mmproj", mmprojModels, ggufModelExtensions)
        scanMmprojCandidates(root.resolveChildIgnoreCase("llm"), mmprojModels)

        return SetupScanResult(
            imageModels = imageModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            vaeModels = vaeModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            textEncoderModels = textEncoderModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            llmModels = llmModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            mmprojModels = mmprojModels.distinctBy { it.id }.sortedBy { it.name.lowercase() },
        )
    }

    private fun scanFolder(
        root: File,
        folderName: String,
        type: String,
        target: MutableList<SetupModelOption>,
        extensions: Set<String>,
    ) {
        val folder = root.resolveChildIgnoreCase(folderName)
        if (!folder.isDirectory) return
        folder.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .take(maxScanResults)
            .forEach { file -> target += file.toOption(root, type) }
    }

    private fun scanRootImageModels(root: File, target: MutableList<SetupModelOption>) {
        root.listFiles { file -> file.isFile && file.extension.lowercase() in ggufModelExtensions }
            .orEmpty()
            .take(maxScanResults)
            .forEach { file -> target += file.toOption(root, "root") }
    }

    private fun scanMmprojCandidates(folder: File, target: MutableList<SetupModelOption>) {
        if (!folder.isDirectory) return
        folder.walkTopDown()
            .filter { it.isFile && it.name.contains("mmproj", ignoreCase = true) && it.extension.lowercase() in ggufModelExtensions }
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

    private fun List<SetupModelOption>.preferredZImageTurbo(): SetupModelOption? =
        bestMatch(
            required = listOf("z", "image", "turbo"),
            preferred = listOf("z-image-turbo", "z_image_turbo", "z image turbo", "unsloth"),
            rejected = listOf("qwen", "vl", "mmproj", "vae", "clip", "t5", "text-encoder", "text_encoder"),
        )

    private fun List<SetupModelOption>.preferredQwen3TextEncoder(): SetupModelOption? =
        bestMatch(
            required = listOf("qwen3", "4b"),
            preferred = listOf("qwen3-4b", "qwen3_4b", "qwen3 4b", "unsloth"),
            rejected = listOf("vl", "vision", "mmproj", "instruct", "coder", "embedding"),
        )

    private fun List<SetupModelOption>.preferredFluxVae(): SetupModelOption? =
        bestMatch(
            required = listOf("ae"),
            preferred = listOf("ae.safetensors", "/vae/ae", "\\vae\\ae", "flux"),
            rejected = listOf("diffusion", "qwen", "clip", "text-encoder", "text_encoder", "mmproj"),
        )

    private fun List<SetupModelOption>.preferredQwen3VlTagger(): SetupModelOption? =
        bestMatch(
            required = listOf("qwen3", "vl", "4b"),
            preferred = listOf("qwen3-vl-4b", "qwen3_vl_4b", "qwen3 vl 4b", "instruct", "unsloth"),
            rejected = listOf("mmproj", "projector"),
        )

    private fun List<SetupModelOption>.preferredQwen3VlProjector(): SetupModelOption? =
        bestMatch(
            required = listOf("mmproj"),
            preferred = listOf("qwen3-vl", "qwen3_vl", "qwen3 vl", "4b", "projector"),
            rejected = listOf("q4_", "q5_", "q6_", "q8_", "iq", "f16-00001"),
        )

    private fun List<SetupModelOption>.bestMatch(
        required: List<String>,
        preferred: List<String>,
        rejected: List<String>,
    ): SetupModelOption? =
        mapNotNull { option ->
            val searchable = option.searchableName()
            val requiredScore = required.sumOf { token ->
                if (searchable.contains(token, ignoreCase = true)) 25 else 0
            }
            if (requiredScore == 0) return@mapNotNull null
            val preferredScore = preferred.sumOf { token ->
                if (searchable.contains(token, ignoreCase = true)) 12 else 0
            }
            val rejectedPenalty = rejected.sumOf { token ->
                if (searchable.contains(token, ignoreCase = true)) 40 else 0
            }
            val folderBonus = when (option.type) {
                "diffusion_models", "unet", "stable-diffusion" -> if ("z" in required && "image" in required) 10 else 0
                "vae" -> if ("ae" in required) 10 else 0
                "llm" -> if ("qwen3" in required) 8 else 0
                "mmproj" -> if ("mmproj" in required) 12 else 0
                else -> 0
            }
            ScoredModelOption(option, requiredScore + preferredScore + folderBonus - rejectedPenalty)
        }
            .filter { it.score > 0 }
            .maxWithOrNull(
                compareBy<ScoredModelOption> { it.score }
                    .thenByDescending { it.option.id.length }
                    .thenBy { it.option.id.lowercase() },
            )
            ?.option

    private fun SetupModelOption.searchableName(): String =
        listOf(id, name, type)
            .joinToString(" ")
            .replace('_', '-')
            .replace('\\', '/')

    private data class ScoredModelOption(
        val option: SetupModelOption,
        val score: Int,
    )

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
            SetupLlmRole.Tagging -> 5
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
        val ggufModelExtensions = setOf("gguf")
        val vaeModelExtensions = setOf("safetensors", "gguf")
        const val maxScanResults = 500
    }
}

val SetupLlmRole.step: Int
    get() = when (this) {
        SetupLlmRole.Tagging -> 4
        SetupLlmRole.PromptEnhancement -> 5
        SetupLlmRole.Assistant -> 5
    }

val SetupLlmRole.displayName: String
    get() = when (this) {
        SetupLlmRole.Tagging -> "Image Tagging"
        SetupLlmRole.PromptEnhancement -> "Prompt Enhancement"
        SetupLlmRole.Assistant -> "Assistant"
    }
