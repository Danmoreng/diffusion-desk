package com.diffusiondesk.desktop.core

import java.io.File
import java.util.Properties

class DesktopSettingsStore {
    private val settingsFile = File(AppPaths.appDir, "settings.properties")

    fun load(): DesktopSettings {
        val repoRoot = detectDefaultRepoRoot()
        val defaults = DesktopSettings(
            repoRoot = repoRoot,
            listenPort = 1234,
            modelDir = detectDefaultModelDir(repoRoot),
            outputDir = detectDefaultOutputDir(repoRoot),
            setupCompleted = true,
        )

        if (!settingsFile.exists()) {
            return defaults
        }

        return runCatching {
            val props = Properties()
            settingsFile.inputStream().use(props::load)
            val repoRootFromSettings = props.getProperty("repoRoot", defaults.repoRoot)
            val modelDirFromSettings = props.getProperty("modelDir", defaults.modelDir)
            val outputDirFromSettings = props.getProperty("outputDir", defaults.outputDir)
            val detectedModelDir = detectDefaultModelDir(repoRootFromSettings)
            val detectedOutputDir = detectDefaultOutputDir(repoRootFromSettings)

            defaults.copy(
                repoRoot = repoRootFromSettings,
                listenPort = props.getProperty("listenPort", defaults.listenPort.toString()).toIntOrNull() ?: defaults.listenPort,
                modelDir = modelDirFromSettings.takeIf { File(it).exists() } ?: detectedModelDir,
                outputDir = outputDirFromSettings.takeIf { it.isNotBlank() } ?: detectedOutputDir,
                setupCompleted = props.getProperty("setupCompleted", defaults.setupCompleted.toString()).toBooleanStrictOrNull()
                    ?: defaults.setupCompleted,
            )
        }.getOrElse { defaults }
    }

    fun save(settings: DesktopSettings) {
        if (!AppPaths.appDir.exists()) {
            AppPaths.appDir.mkdirs()
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
