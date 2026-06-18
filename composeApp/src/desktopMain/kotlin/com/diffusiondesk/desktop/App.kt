package com.diffusiondesk.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.ImagePromptMode
import com.diffusiondesk.desktop.screens.AssistantPanel
import com.diffusiondesk.desktop.screens.AnalyzeCompositionScreen
import com.diffusiondesk.desktop.screens.GalleryScreen
import com.diffusiondesk.desktop.screens.GenerateScreen
import com.diffusiondesk.desktop.screens.LibraryScreen
import com.diffusiondesk.desktop.screens.DeskCompactControlSpacing
import com.diffusiondesk.desktop.screens.DeskNavigationItem
import com.diffusiondesk.desktop.screens.NotificationStack
import com.diffusiondesk.desktop.screens.SettingsScreen
import com.diffusiondesk.desktop.screens.SystemScreen
import com.diffusiondesk.desktop.screens.UpscaleScreen
import com.diffusiondesk.desktop.theme.DiffusionDeskTheme
import com.diffusiondesk.desktop.viewmodel.AssistantContextSnapshot
import com.diffusiondesk.desktop.viewmodel.GenerationUiState
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionElement
import org.jetbrains.jewel.ui.component.Text

private enum class Screen(val label: String, val icon: ImageVector, val subtitle: String) {
    Generate("Generate", Icons.Default.Image, "Preset-driven image generation with the local SD worker."),
    Analyze("Analyze", Icons.Default.ImageSearch, "Translate images into structured Ideogram JSON."),
    Gallery("Gallery", Icons.Default.Collections, "Browse generated images and reuse embedded parameters."),
    Upscale("Upscale", Icons.Default.CropFree, "Classical ESRGAN image upscaling."),
    Library("Presets", Icons.Default.Inventory2, "Manage JSON-backed image generation presets."),
    System("System", Icons.Default.Memory, "Worker status, runtime controls, and diagnostics."),
    Settings("Settings", Icons.Default.Settings, "Local paths and static app configuration."),
}

@Composable
fun App(
    controller: AppController,
) {
    var currentScreen by remember { mutableStateOf(Screen.Generate) }
    val settingsState by controller.settingsViewModel.uiState.collectAsState()
    val backendState by controller.settingsViewModel.backendState.collectAsState()
    val generationState by controller.generationViewModel.uiState.collectAsState()
    val analyzeState by controller.generationViewModel.analyzeUiState.collectAsState()
    val assistantState by controller.assistantViewModel.uiState.collectAsState()
    val libraryState by controller.libraryViewModel.uiState.collectAsState()
    val galleryState by controller.galleryViewModel.uiState.collectAsState()
    val upscaleState by controller.upscaleViewModel.uiState.collectAsState()
    val notifications by controller.notificationCenter.notifications.collectAsState()
    val llmDebugEntries by controller.llmDebugLog.entries.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (settingsState.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }
    var assistantOpen by remember { mutableStateOf(false) }
    val assistantContext = remember(generationState, currentScreen) {
        generationState.toAssistantContext(currentScreen.label)
    }

    DiffusionDeskTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationSidebar(
                    currentScreen = currentScreen,
                    assistantOpen = assistantOpen,
                    onSelect = { currentScreen = it },
                    onToggleAssistant = { assistantOpen = !assistantOpen },
                )

                if (assistantOpen) {
                    Spacer(Modifier.width(DeskCompactControlSpacing))
                    AssistantPanel(
                        state = assistantState,
                        context = assistantContext,
                        onSend = controller.assistantViewModel::sendMessage,
                        onCancel = controller.assistantViewModel::cancel,
                        onClear = controller.assistantViewModel::clearHistory,
                        onClose = { assistantOpen = false },
                        onAttachImage = controller.assistantViewModel::attachImage,
                        onClearAttachedImage = controller.assistantViewModel::clearAttachedImage,
                        onInspectLatestImage = {
                            controller.assistantViewModel.sendMessage("Inspect the latest generated image.", assistantContext)
                        },
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.Generate -> GenerateScreen(
                            state = generationState,
                            backendState = backendState,
                            samplerOptions = generationState.samplerOptions,
                            onPromptChange = controller.generationViewModel::updatePrompt,
                            onPromptCommit = controller.generationViewModel::commitPrompt,
                            onUndoPrompt = controller.generationViewModel::undoPrompt,
                            onRedoPrompt = controller.generationViewModel::redoPrompt,
                            onNegativePromptChange = controller.generationViewModel::updateNegativePrompt,
                            onGenerateStructuredJson = controller.generationViewModel::generateIdeogramJsonPrompt,
                            onRetryStagedJson = controller.generationViewModel::retryStagedIdeogramJsonPrompt,
                            onStartOverComposition = controller.generationViewModel::startOverComposition,
                            onCompositionMutation = controller.generationViewModel::applyCompositionMutation,
                            onRunCompositionAction = controller.generationViewModel::runCompositionAction,
                            onUndoComposition = controller.generationViewModel::undoComposition,
                            onRedoComposition = controller.generationViewModel::redoComposition,
                            onCompositionBboxEditStart = controller.generationViewModel::beginCompositionBboxEdit,
                            onCompositionBboxChange = controller.generationViewModel::updateIdeogramElementBbox,
                            onCompositionBboxEditEnd = controller.generationViewModel::commitCompositionBboxEdit,
                            onCompositionBboxEditCancel = controller.generationViewModel::cancelCompositionBboxEdit,
                            onCompositionDescriptionChange = controller.generationViewModel::updateIdeogramElementDescription,
                            onCompositionTextChange = controller.generationViewModel::updateIdeogramElementText,
                            onCompositionPaletteChange = controller.generationViewModel::updateIdeogramElementPalette,
                            onCompositionElementSelected = controller.generationViewModel::selectCompositionElement,
                            showCompositionOverlay = settingsState.showCompositionOverlay,
                            onShowCompositionOverlayChange = controller.settingsViewModel::updateShowCompositionOverlay,
                            onUseImageAsCompositionReferenceChange = controller.generationViewModel::updateUseImageAsCompositionReference,
                            onWidthChange = controller.generationViewModel::updateWidth,
                            onHeightChange = controller.generationViewModel::updateHeight,
                            onStepsChange = controller.generationViewModel::updateSteps,
                            onCfgScaleChange = controller.generationViewModel::updateCfgScale,
                            onSeedChange = controller.generationViewModel::updateSeed,
                            onSamplerChange = controller.generationViewModel::updateSampler,
                            onLoraSearchChange = controller.generationViewModel::updateLoraSearchQuery,
                            onToggleLoraPanel = controller.generationViewModel::toggleLoraPanel,
                            onReloadLoras = controller.generationViewModel::reloadLoras,
                            onToggleLora = controller.generationViewModel::toggleLora,
                            onLoraWeightChange = controller.generationViewModel::updateLoraWeight,
                            onRandomizeSeed = controller.generationViewModel::randomizeSeed,
                            onReuseLastSeed = controller.generationViewModel::reuseLastSeed,
                            onSwapDimensions = controller.generationViewModel::swapDimensions,
                            onApplyAspectRatio = controller.generationViewModel::applyAspectRatio,
                            onScaleResolution = controller.generationViewModel::scaleResolution,
                            onResetToPresetDefaults = controller.generationViewModel::resetToPresetDefaults,
                            onEnhancePrompt = controller.generationViewModel::enhancePrompt,
                            onGenerate = {
                                controller.generationViewModel.generate(settingsState.saveImagesAutomatically)
                            },
                            onCancelGeneration = controller.generationViewModel::cancelGeneration,
                            onToggleEndless = controller.generationViewModel::toggleEndless,
                            onPresetSelected = controller.generationViewModel::selectAndLoadPreset,
                            onGoBack = controller.generationViewModel::goBack,
                            onGoForward = controller.generationViewModel::goForward,
                            onLeftPanelWidthChange = controller.generationViewModel::updateLeftPanelWidth,
                            actionBarPosition = settingsState.actionBarPosition,
                            modelDir = settingsState.modelDir,
                            outputDir = settingsState.outputDir,
                            showLlmDebugConsole = settingsState.showLlmDebugConsole,
                            llmDebugEntries = llmDebugEntries,
                            onClearLlmDebugLog = controller.llmDebugLog::clear,
                        )
                        Screen.Analyze -> AnalyzeCompositionScreen(
                            state = analyzeState,
                            outputDir = settingsState.outputDir,
                            showCompositionOverlay = settingsState.showCompositionOverlay,
                            onCaptureModeChange = controller.generationViewModel::updateAnalyzeCaptureMode,
                            onCaptureUploadSelected = controller.generationViewModel::selectAnalyzeUploadedCaptureImage,
                            onStartImageCapture = controller.generationViewModel::startAnalyzeImageCapture,
                            onApplyImageCapture = {
                                controller.generationViewModel.applyAnalyzeCompositionToGenerate()
                                currentScreen = Screen.Generate
                            },
                            onAddAnalyzeElementBox = controller.generationViewModel::addAnalyzeElementBox,
                            onAnalyzeSelectedElementBox = controller.generationViewModel::analyzeSelectedElementBox,
                            onAnalyzeAllElementBoxes = controller.generationViewModel::analyzeAllElementBoxes,
                            onCompositionMutation = controller.generationViewModel::applyAnalyzeCompositionMutation,
                            onRunCompositionAction = controller.generationViewModel::runAnalyzeCompositionAction,
                            onStartOverComposition = controller.generationViewModel::startOverAnalyzeComposition,
                            onUndoComposition = controller.generationViewModel::undoAnalyzeComposition,
                            onRedoComposition = controller.generationViewModel::redoAnalyzeComposition,
                            onCompositionBboxEditStart = controller.generationViewModel::beginAnalyzeCompositionBboxEdit,
                            onCompositionBboxChange = controller.generationViewModel::updateAnalyzeIdeogramElementBbox,
                            onCompositionBboxEditEnd = controller.generationViewModel::commitAnalyzeCompositionBboxEdit,
                            onCompositionBboxEditCancel = controller.generationViewModel::cancelAnalyzeCompositionBboxEdit,
                            onCompositionDescriptionChange = controller.generationViewModel::updateAnalyzeIdeogramElementDescription,
                            onCompositionTextChange = controller.generationViewModel::updateAnalyzeIdeogramElementText,
                            onCompositionPaletteChange = controller.generationViewModel::updateAnalyzeIdeogramElementPalette,
                            onCompositionElementSelected = controller.generationViewModel::selectAnalyzeCompositionElement,
                        )
                        Screen.Gallery -> GalleryScreen(
                            state = galleryState,
                            outputDir = settingsState.outputDir,
                            isTaggingGallery = settingsState.isTaggingGallery,
                            onRefresh = { controller.galleryViewModel.refresh(settingsState.outputDir) },
                            onTagAllPendingImages = controller.settingsViewModel::tagAllPendingImages,
                            onQueryChange = controller.galleryViewModel::updateQuery,
                            onSelectKeyword = controller.galleryViewModel::selectKeyword,
                            onClearKeywordFilter = controller.galleryViewModel::clearKeywordFilter,
                            onSelectImage = controller.galleryViewModel::selectImage,
                            onKeywordDraftChange = controller.galleryViewModel::updateKeywordDraft,
                            onAddKeyword = controller.galleryViewModel::addKeywordToSelected,
                            onRemoveKeyword = controller.galleryViewModel::removeKeyword,
                            onTagSelectedImage = controller.galleryViewModel::tagSelectedImage,
                            onDeleteImage = controller.galleryViewModel::deleteSelectedImage,
                            previewPanelWidthDp = settingsState.galleryPreviewWidthDp,
                            onPreviewPanelWidthChange = controller.settingsViewModel::updateGalleryPreviewWidth,
                            onReuseImage = { image ->
                                controller.generationViewModel.reuseGalleryParams(controller.galleryViewModel.reusableParams(image))
                                currentScreen = Screen.Generate
                            },
                            onUpscaleImage = { image ->
                                controller.upscaleViewModel.useGalleryImage(image)
                                currentScreen = Screen.Upscale
                            },
                            onAnalyzeComposition = { image ->
                                controller.generationViewModel.useGalleryImageForAnalyzeComposition(image)
                                currentScreen = Screen.Analyze
                            },
                        )
                        Screen.Upscale -> UpscaleScreen(
                            state = upscaleState,
                            backendState = backendState,
                            outputDir = settingsState.outputDir,
                            onSelectImage = controller.upscaleViewModel::loadFile,
                            onReloadModels = controller.upscaleViewModel::reloadModels,
                            onSelectModel = controller.upscaleViewModel::selectModel,
                            onFactorChange = controller.upscaleViewModel::updateFactor,
                            onUpscale = controller.upscaleViewModel::upscale,
                        )
                        Screen.Library -> LibraryScreen(
                            state = libraryState,
                            backendState = backendState,
                            selectedPresetId = generationState.selectedPresetId,
                            samplerOptions = generationState.samplerOptions,
                            onCreatePreset = controller.libraryViewModel::createPreset,
                            onEditPreset = controller.libraryViewModel::editPreset,
                            onDeletePreset = { id ->
                                if (controller.libraryViewModel.deletePreset(id)) {
                                    controller.generationViewModel.reloadPresets()
                                }
                            },
                            onLoadPreset = { id ->
                                controller.generationViewModel.updatePresetId(id)
                                controller.generationViewModel.loadSelectedPreset()
                            },
                            onReloadPresets = {
                                controller.libraryViewModel.reloadPresets()
                                controller.generationViewModel.reloadPresets()
                                controller.settingsViewModel.reloadLlmPresets()
                            },
                            onShowImagePresets = controller.libraryViewModel::showImagePresets,
                            onShowLlmPresets = controller.libraryViewModel::showLlmPresets,
                            onCancelEditor = controller.libraryViewModel::cancelEditor,
                            onFormChange = controller.libraryViewModel::updateForm,
                            onLlmFormChange = controller.libraryViewModel::updateLlmForm,
                            onSavePreset = {
                                if (controller.libraryViewModel.saveEditor()) {
                                    controller.generationViewModel.reloadPresets()
                                }
                            },
                            onCreateLlmPreset = controller.libraryViewModel::createLlmPreset,
                            onEditLlmPreset = controller.libraryViewModel::editLlmPreset,
                            onDeleteLlmPreset = { id ->
                                if (controller.libraryViewModel.deleteLlmPreset(id)) {
                                    controller.settingsViewModel.reloadLlmPresets()
                                }
                            },
                            onSaveLlmPreset = {
                                if (controller.libraryViewModel.saveLlmEditor()) {
                                    controller.settingsViewModel.reloadLlmPresets()
                                }
                            },
                        )
                        Screen.System -> SystemScreen(
                            state = settingsState,
                            backendState = backendState,
                            onStartBackend = controller.settingsViewModel::startBackend,
                            onStopBackend = controller.settingsViewModel::stopBackend,
                            onUnloadImageModel = controller.settingsViewModel::unloadImageModel,
                            onReloadLlmPresets = controller.settingsViewModel::reloadLlmPresets,
                            onTaggingPresetChange = controller.settingsViewModel::updateTaggingPresetId,
                            onAssistantPresetChange = controller.settingsViewModel::updateAssistantPresetId,
                            onPromptEnhancerPresetChange = controller.settingsViewModel::updatePromptEnhancerPresetId,
                            onAutostartLlmWorkersChange = controller.settingsViewModel::updateAutostartLlmWorkers,
                            onLoadLlmRole = controller.settingsViewModel::loadLlmRole,
                            onUnloadLlmPreset = controller.settingsViewModel::unloadLlmPreset,
                            onStopLlmWorker = controller.settingsViewModel::stopLlmWorker,
                            onStopAllLlmWorkers = controller.settingsViewModel::stopAllLlmWorkers,
                            onTagNextImage = controller.settingsViewModel::tagNextImage,
                        )
                        Screen.Settings -> SettingsScreen(
                            state = settingsState,
                            backendState = backendState,
                            onRepoRootChange = controller.settingsViewModel::updateRepoRoot,
                            onListenPortChange = controller.settingsViewModel::updateListenPort,
                            onModelDirChange = controller.settingsViewModel::updateModelDir,
                            onOutputDirChange = controller.settingsViewModel::updateOutputDir,
                            onThemeModeChange = controller.settingsViewModel::updateThemeMode,
                            onActionBarPositionChange = controller.settingsViewModel::updateActionBarPosition,
                            onSaveImagesAutomaticallyChange = controller.settingsViewModel::updateSaveImagesAutomatically,
                            onShowLlmDebugConsoleChange = controller.settingsViewModel::updateShowLlmDebugConsole,
                            onVramBudgetModeChange = controller.settingsViewModel::updateVramBudgetMode,
                            onManualVramBudgetGbChange = controller.settingsViewModel::updateManualVramBudgetGb,
                            onUseCurrentRepo = controller.settingsViewModel::useCurrentRepo,
                            onSaveLocal = controller.settingsViewModel::saveLocalSettings,
                            onApplyToBackend = controller.settingsViewModel::applySettingsToBackend,
                            onReloadFromBackend = controller.settingsViewModel::loadConfigFromBackend,
                            onRefreshModelFolders = {
                                controller.generationViewModel.reloadLoras()
                                controller.upscaleViewModel.reloadModels()
                            },
                        )
                    }
                    NotificationStack(
                        notifications = notifications,
                        onDismiss = controller.notificationCenter::dismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationSidebar(
    currentScreen: Screen,
    assistantOpen: Boolean,
    onSelect: (Screen) -> Unit,
    onToggleAssistant: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
        ) {
            Screen.entries.forEach { screen ->
                DeskNavigationItem(
                    label = screen.label,
                    icon = screen.icon,
                    selected = currentScreen == screen,
                    onClick = { onSelect(screen) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        DeskNavigationItem(
            label = "Assistant",
            icon = Icons.Default.SmartToy,
            selected = assistantOpen,
            onClick = onToggleAssistant,
        )

        Spacer(Modifier.height(DeskCompactControlSpacing))
    }
}

private fun GenerationUiState.toAssistantContext(screen: String): AssistantContextSnapshot {
    val preset = presets.firstOrNull { it.id == selectedPresetId }
    val promptMode = preset?.promptMode ?: ImagePromptMode.Text
    val document = ideogram.document
    val selectedElement = document?.elements?.getOrNull(selectedCompositionElementIndex)
    return AssistantContextSnapshot(
        screen = screen,
        promptMode = promptMode.displayName,
        prompt = if (promptMode == ImagePromptMode.Json) ideogram.jsonPrompt else prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        steps = steps,
        cfgScale = cfgScale,
        sampler = sampler,
        seed = seed,
        selectedPreset = preset?.name.orEmpty(),
        compositionSummary = document?.let {
            buildString {
                append("high_level_description: ${it.highLevelDescription}\n")
                append("background: ${it.background}\n")
                append("elements:\n")
                it.elements.forEachIndexed { index, element ->
                    append("${index + 1}. ${element.assistantLabel()}\n")
                }
            }
        }.orEmpty(),
        selectedCompositionElement = selectedElement?.assistantLabel().orEmpty(),
        hasStructuredComposition = document != null,
        hasSelectedCompositionElement = selectedElement != null,
    )
}

private fun IdeogramCompositionElement.assistantLabel(): String =
    buildString {
        append(type)
        if (text?.isNotBlank() == true) append(" text=\"").append(text).append("\"")
        if (description.isNotBlank()) append(" desc=\"").append(description.take(180)).append("\"")
        if (bbox.size == 4) append(" bbox=").append(bbox.joinToString(prefix = "[", postfix = "]"))
    }
