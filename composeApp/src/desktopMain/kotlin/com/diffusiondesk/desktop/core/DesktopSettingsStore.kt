package com.diffusiondesk.desktop.core

import java.io.File
import java.util.Properties

class DesktopSettingsStore {
    private val appDir = File(System.getProperty("user.home"), ".diffusion-desk-desktop")
    private val settingsFile = File(appDir, "settings.properties")

    fun load(): DesktopSettings {
        val repoRoot = detectDefaultRepoRoot()
        val defaults = DesktopSettings(
            repoRoot = repoRoot,
            listenPort = 1234,
            modelDir = File(repoRoot, "models").absolutePath,
            outputDir = File(repoRoot, "outputs").absolutePath,
            setupCompleted = true,
        )

        if (!settingsFile.exists()) {
            return defaults
        }

        return runCatching {
            val props = Properties()
            settingsFile.inputStream().use(props::load)
            defaults.copy(
                repoRoot = props.getProperty("repoRoot", defaults.repoRoot),
                listenPort = props.getProperty("listenPort", defaults.listenPort.toString()).toIntOrNull() ?: defaults.listenPort,
                modelDir = props.getProperty("modelDir", defaults.modelDir),
                outputDir = props.getProperty("outputDir", defaults.outputDir),
                setupCompleted = props.getProperty("setupCompleted", defaults.setupCompleted.toString()).toBooleanStrictOrNull()
                    ?: defaults.setupCompleted,
            )
        }.getOrElse { defaults }
    }

    fun save(settings: DesktopSettings) {
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val props = Properties()
        props.setProperty("repoRoot", settings.repoRoot)
        props.setProperty("listenPort", settings.listenPort.toString())
        props.setProperty("modelDir", settings.modelDir)
        props.setProperty("outputDir", settings.outputDir)
        props.setProperty("setupCompleted", settings.setupCompleted.toString())

        settingsFile.outputStream().use { props.store(it, "Diffusion Desk Desktop Settings") }
    }
}
