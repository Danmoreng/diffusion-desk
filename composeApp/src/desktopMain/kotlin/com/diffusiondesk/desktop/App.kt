package com.diffusiondesk.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diffusiondesk.desktop.screens.GalleryScreen
import com.diffusiondesk.desktop.screens.GenerateScreen
import com.diffusiondesk.desktop.screens.LibraryScreen
import com.diffusiondesk.desktop.screens.DeskCompactControlSpacing
import com.diffusiondesk.desktop.screens.NotificationStack
import com.diffusiondesk.desktop.screens.SettingsScreen
import com.diffusiondesk.desktop.screens.SystemScreen
import com.diffusiondesk.desktop.theme.DiffusionDeskTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

private enum class Screen(val label: String, val icon: ImageVector, val subtitle: String) {
    Generate("Generate", Icons.Default.Image, "Preset-driven image generation with the local SD worker."),
    Gallery("Gallery", Icons.Default.Collections, "Browse generated images and reuse embedded parameters."),
    Library("Library", Icons.Default.Inventory2, "Manage JSON-backed image generation presets."),
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
    val libraryState by controller.libraryViewModel.uiState.collectAsState()
    val galleryState by controller.galleryViewModel.uiState.collectAsState()
    val notifications by controller.notificationCenter.notifications.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (settingsState.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }

    DiffusionDeskTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationSidebar(
                    currentScreen = currentScreen,
                    darkTheme = darkTheme,
                    onSelect = { currentScreen = it },
                    onToggleTheme = controller.settingsViewModel::toggleThemeMode,
                )

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
                            onWidthChange = controller.generationViewModel::updateWidth,
                            onHeightChange = controller.generationViewModel::updateHeight,
                            onStepsChange = controller.generationViewModel::updateSteps,
                            onCfgScaleChange = controller.generationViewModel::updateCfgScale,
                            onSeedChange = controller.generationViewModel::updateSeed,
                            onBatchCountChange = controller.generationViewModel::updateBatchCount,
                            onSamplerChange = controller.generationViewModel::updateSampler,
                            onRandomizeSeed = controller.generationViewModel::randomizeSeed,
                            onReuseLastSeed = controller.generationViewModel::reuseLastSeed,
                            onSwapDimensions = controller.generationViewModel::swapDimensions,
                            onApplyAspectRatio = controller.generationViewModel::applyAspectRatio,
                            onScaleResolution = controller.generationViewModel::scaleResolution,
                            onResetToPresetDefaults = controller.generationViewModel::resetToPresetDefaults,
                            onGenerate = {
                                controller.generationViewModel.generate(settingsState.saveImagesAutomatically)
                            },
                            onToggleEndless = controller.generationViewModel::toggleEndless,
                            onPresetSelected = controller.generationViewModel::selectAndLoadPreset,
                            onGoBack = controller.generationViewModel::goBack,
                            onGoForward = controller.generationViewModel::goForward,
                            onLeftPanelWidthChange = controller.generationViewModel::updateLeftPanelWidth,
                            actionBarPosition = settingsState.actionBarPosition,
                            outputDir = settingsState.outputDir,
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
                            previewPanelWidthDp = settingsState.galleryPreviewWidthDp,
                            onPreviewPanelWidthChange = controller.settingsViewModel::updateGalleryPreviewWidth,
                            onReuseImage = { image ->
                                controller.generationViewModel.reuseGalleryParams(controller.galleryViewModel.reusableParams(image))
                                currentScreen = Screen.Generate
                            },
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
                            onUseCurrentRepo = controller.settingsViewModel::useCurrentRepo,
                            onSaveLocal = controller.settingsViewModel::saveLocalSettings,
                            onApplyToBackend = controller.settingsViewModel::applySettingsToBackend,
                            onReloadFromBackend = controller.settingsViewModel::loadConfigFromBackend,
                        )
                    }
                    NotificationStack(
                        notifications = notifications,
                        onDismiss = controller.notificationCenter::dismiss,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
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
    darkTheme: Boolean,
    onSelect: (Screen) -> Unit,
    onToggleTheme: () -> Unit,
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
                SidebarItem(
                    screen = screen,
                    selected = currentScreen == screen,
                    onClick = { onSelect(screen) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle theme",
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SidebarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) selectedColor else MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = screen.label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
