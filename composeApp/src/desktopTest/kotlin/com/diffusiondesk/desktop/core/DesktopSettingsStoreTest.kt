package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun loadResolvesRelativeModelAndOutputPathsAgainstRepoRoot() {
        val tempDir = Files.createTempDirectory("diffusion-desk-settings-test").toFile()
        val settingsFile = tempDir.resolve("settings.properties")
        val repoRoot = tempDir.resolve("repo").apply { mkdirs() }

        try {
            settingsFile.writeText(
                """
                repoRoot=${repoRoot.absolutePath}
                modelDir=../models
                outputDir=./outputs
                """.trimIndent(),
            )

            val settings = DesktopSettingsStore(settingsFile).load()

            assertEquals(tempDir.resolve("models").canonicalPath, settings.modelDir)
            assertEquals(repoRoot.resolve("outputs").canonicalPath, settings.outputDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun loadPreservesAbsoluteModelPathEvenWhenDirectoryDoesNotExist() {
        val tempDir = Files.createTempDirectory("diffusion-desk-settings-test").toFile()
        val settingsFile = tempDir.resolve("settings.properties")
        val repoRoot = tempDir.resolve("repo").apply { mkdirs() }
        val modelDir = tempDir.resolve("external-models")

        try {
            settingsFile.writeText(
                """
                repoRoot=${repoRoot.absolutePath}
                modelDir=${modelDir.absolutePath}
                outputDir=outputs
                """.trimIndent(),
            )

            val settings = DesktopSettingsStore(settingsFile).load()

            assertEquals(modelDir.canonicalPath, settings.modelDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
