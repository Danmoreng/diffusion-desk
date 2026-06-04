package com.diffusiondesk.desktop

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GenerationSettingsStore
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.viewmodel.GenerationViewModel
import com.diffusiondesk.desktop.viewmodel.LibraryViewModel
import com.diffusiondesk.desktop.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing

class AppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val client = DiffusionDeskClient()
    private val settingsStore = DesktopSettingsStore()
    private val presetStore = ImagePresetStore()
    private val generationSettingsStore = GenerationSettingsStore()
    private val backendManager = BackendManager(scope, client)

    val settingsViewModel = SettingsViewModel(scope, settingsStore, backendManager, client)
    val generationViewModel = GenerationViewModel(scope, backendManager, client, presetStore, generationSettingsStore)
    val libraryViewModel = LibraryViewModel(presetStore)

    init {
        settingsViewModel.startBackend()
    }

    fun close() {
        backendManager.close()
        scope.cancel()
    }
}
