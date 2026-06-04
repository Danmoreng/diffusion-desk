package com.diffusiondesk.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
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
import com.diffusiondesk.desktop.screens.GenerateScreen
import com.diffusiondesk.desktop.screens.LibraryScreen
import com.diffusiondesk.desktop.screens.SettingsScreen
import com.diffusiondesk.desktop.theme.DiffusionDeskTheme
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

private enum class Screen(val label: String, val icon: ImageVector, val subtitle: String) {
    Generate("Generate", Icons.Default.Image, "Preset-driven image generation with the local SD worker."),
    Library("Library", Icons.Default.Inventory2, "Manage JSON-backed image generation presets."),
    Settings("Settings", Icons.Default.Settings, "Local paths, worker status, and preset storage."),
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
                            samplerOptions = controller.generationViewModel.samplers,
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
                            onGoBack = controller.generationViewModel::goBack,
                            onGoForward = controller.generationViewModel::goForward,
                            onLeftPanelWidthChange = controller.generationViewModel::updateLeftPanelWidth,
                            actionBarPosition = settingsState.actionBarPosition,
                            outputDir = settingsState.outputDir,
                        )
                        Screen.Library -> LibraryScreen(
                            state = libraryState,
                            backendState = backendState,
                            selectedPresetId = generationState.selectedPresetId,
                            samplerOptions = controller.generationViewModel.samplers,
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
                            },
                            onCancelEditor = controller.libraryViewModel::cancelEditor,
                            onFormChange = controller.libraryViewModel::updateForm,
                            onSavePreset = {
                                if (controller.libraryViewModel.saveEditor()) {
                                    controller.generationViewModel.reloadPresets()
                                }
                            },
                        )
                        Screen.Settings -> SettingsScreen(
                            state = settingsState,
                            backendState = backendState,
                            generationState = generationState,
                            onRepoRootChange = controller.settingsViewModel::updateRepoRoot,
                            onListenPortChange = controller.settingsViewModel::updateListenPort,
                            onModelDirChange = controller.settingsViewModel::updateModelDir,
                            onOutputDirChange = controller.settingsViewModel::updateOutputDir,
                            onSetupCompletedChange = controller.settingsViewModel::updateSetupCompleted,
                            onThemeModeChange = controller.settingsViewModel::updateThemeMode,
                            onActionBarPositionChange = controller.settingsViewModel::updateActionBarPosition,
                            onSaveImagesAutomaticallyChange = controller.settingsViewModel::updateSaveImagesAutomatically,
                            onUseCurrentRepo = controller.settingsViewModel::useCurrentRepo,
                            onSaveLocal = controller.settingsViewModel::saveLocalSettings,
                            onStartBackend = controller.settingsViewModel::startBackend,
                            onStopBackend = controller.settingsViewModel::stopBackend,
                            onApplyToBackend = controller.settingsViewModel::applySettingsToBackend,
                            onReloadFromBackend = controller.settingsViewModel::loadConfigFromBackend,
                            onPresetIdChange = controller.generationViewModel::updatePresetId,
                            onReloadPresets = controller.generationViewModel::reloadPresets,
                            onLoadPreset = controller.generationViewModel::loadSelectedPreset,
                        )
                    }
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
        Screen.entries.forEach { screen ->
            SidebarItem(
                screen = screen,
                selected = currentScreen == screen,
                onClick = { onSelect(screen) },
            )
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
