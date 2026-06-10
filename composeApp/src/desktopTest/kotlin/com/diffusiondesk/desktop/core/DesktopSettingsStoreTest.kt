package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSettingsStoreTest {
    @Test
    fun compositionOverlayDefaultsToVisibleAndPersistsUserChoice() {
        val settingsFile = Files.createTempDirectory("diffusion-desk-settings-test")
            .resolve("settings.properties")
            .toFile()

        try {
            val store = DesktopSettingsStore(settingsFile)
            val defaults = store.load()
            assertTrue(defaults.showCompositionOverlay)

            store.save(defaults.copy(showCompositionOverlay = false))

            assertFalse(DesktopSettingsStore(settingsFile).load().showCompositionOverlay)
        } finally {
            settingsFile.delete()
            settingsFile.parentFile?.toPath()?.deleteIfExists()
        }
    }
}
