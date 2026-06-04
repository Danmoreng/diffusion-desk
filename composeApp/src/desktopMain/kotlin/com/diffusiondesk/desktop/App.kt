package com.diffusiondesk.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.diffusiondesk.desktop.screens.GenerateScreen
import com.diffusiondesk.desktop.screens.SettingsScreen
import com.diffusiondesk.desktop.theme.DiffusionDeskTheme

private enum class Screen(val label: String, val icon: ImageVector, val subtitle: String) {
    Generate("Generate", Icons.Default.Image, "Preset-driven image generation with the local SD worker."),
    Settings("Settings", Icons.Default.Settings, "Local paths, worker status, and preset storage."),
}

@Composable
fun App(
    controller: AppController,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf(Screen.Generate) }
    val settingsState by controller.settingsViewModel.uiState.collectAsState()
    val backendState by controller.settingsViewModel.backendState.collectAsState()
    val generationState by controller.generationViewModel.uiState.collectAsState()

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
                    onToggleTheme = onToggleTheme,
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.Generate -> GenerateScreen(
                            state = generationState,
                            backendState = backendState,
                            samplerOptions = controller.generationViewModel.samplers,
                            onPromptChange = controller.generationViewModel::updatePrompt,
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
                            onGenerate = controller.generationViewModel::generate,
                            onToggleEndless = controller.generationViewModel::toggleEndless,
                            onGoBack = controller.generationViewModel::goBack,
                            onGoForward = controller.generationViewModel::goForward,
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
    NavigationRail(
        modifier = Modifier.width(88.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))

        Screen.entries.forEach { screen ->
            NavigationRailItem(
                selected = currentScreen == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label, fontSize = 10.sp) },
                alwaysShowLabel = true,
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
