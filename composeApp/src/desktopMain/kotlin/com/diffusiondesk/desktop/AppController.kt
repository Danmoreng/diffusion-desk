package com.diffusiondesk.desktop

import com.diffusiondesk.desktop.core.BackendManager
import com.diffusiondesk.desktop.core.DesktopSettingsStore
import com.diffusiondesk.desktop.core.DiffusionDeskClient
import com.diffusiondesk.desktop.core.GalleryDatabase
import com.diffusiondesk.desktop.core.GalleryRepository
import com.diffusiondesk.desktop.core.GenerationSettingsStore
import com.diffusiondesk.desktop.core.ImagePresetStore
import com.diffusiondesk.desktop.core.ImageTaggingService
import com.diffusiondesk.desktop.core.LlmPresetStore
import com.diffusiondesk.desktop.core.LlmDebugLog
import com.diffusiondesk.desktop.core.LlmRoleService
import com.diffusiondesk.desktop.core.LlmWorkerPool
import com.diffusiondesk.desktop.core.NotificationCenter
import com.diffusiondesk.desktop.composition.CompositionActionExecutor
import com.diffusiondesk.desktop.viewmodel.AssistantViewModel
import com.diffusiondesk.desktop.viewmodel.GalleryViewModel
import com.diffusiondesk.desktop.viewmodel.GenerationViewModel
import com.diffusiondesk.desktop.viewmodel.LibraryViewModel
import com.diffusiondesk.desktop.viewmodel.SettingsViewModel
import com.diffusiondesk.desktop.viewmodel.UpscaleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

class AppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val internalToken = UUID.randomUUID().toString()
    val llmDebugLog = LlmDebugLog()
    private val client = DiffusionDeskClient(internalToken, llmDebugLog)
    private val settingsStore = DesktopSettingsStore()
    private val presetStore = ImagePresetStore()
    private val llmPresetStore = LlmPresetStore()
    private val generationSettingsStore = GenerationSettingsStore()
    private val galleryRepository = GalleryRepository(GalleryDatabase())
    private val backendManager = BackendManager(scope, client, internalToken)
    private val llmWorkerPool = LlmWorkerPool(scope, client, internalToken)
    private val llmRoleService = LlmRoleService(llmWorkerPool, client)
    private val compositionActionExecutor = CompositionActionExecutor(llmRoleService)
    private val imageTaggingService = ImageTaggingService(galleryRepository, llmWorkerPool, client)
    val notificationCenter = NotificationCenter(scope)

    val settingsViewModel = SettingsViewModel(scope, settingsStore, backendManager, llmPresetStore, llmWorkerPool, imageTaggingService, client, notificationCenter)
    val generationViewModel = GenerationViewModel(
        scope,
        backendManager,
        client,
        presetStore,
        generationSettingsStore,
        settingsStore,
        llmPresetStore,
        galleryRepository,
        imageTaggingService,
        llmRoleService,
        llmWorkerPool,
        compositionActionExecutor,
    )
    val assistantViewModel = AssistantViewModel(
        scope = scope,
        settingsStore = settingsStore,
        llmPresetStore = llmPresetStore,
        llmRoleService = llmRoleService,
        latestImageProvider = generationViewModel::latestAssistantImageAttachment,
        runTool = generationViewModel::runAssistantTool,
    )
    val libraryViewModel = LibraryViewModel(scope, presetStore, llmPresetStore, backendManager, client)
    val galleryViewModel = GalleryViewModel(scope, galleryRepository, settingsStore, llmPresetStore, imageTaggingService)
    val upscaleViewModel = UpscaleViewModel(scope, backendManager, client, settingsStore)
    private val closed = AtomicBoolean(false)
    private val shutdownHook = Thread({ close() }, "diffusion-desk-shutdown")

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        settingsViewModel.startBackend()
        settingsViewModel.autostartLlmWorkersIfEnabled()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        scope.cancel()
        llmWorkerPool.close()
        backendManager.close()
    }
}
