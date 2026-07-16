package com.diffusiondesk.desktop.core

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class SavedGenerationSettings(
    val prompt: String = "A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night.",
    val negativePrompt: String = "deformed, blurry, low quality, watermark",
    val width: String = "1024",
    val height: String = "768",
    val steps: String = "4",
    val cfgScale: String = "1.0",
    val seed: String = "-1",
    val sampler: String = "euler_a",
    val leftPanelWidthDp: Int = 560,
    val ideogramJsonPrompt: String = "",
)

class GenerationSettingsStore(
    private val settingsFile: File = File(AppPaths.appDir, "generation.properties"),
) {

    fun load(): SavedGenerationSettings {
        if (!settingsFile.exists()) {
            return SavedGenerationSettings()
        }
        return runCatching {
            val props = Properties()
            settingsFile.inputStream().use(props::load)
            SavedGenerationSettings(
                prompt = props.getProperty("prompt", SavedGenerationSettings().prompt),
                negativePrompt = props.getProperty("negativePrompt", SavedGenerationSettings().negativePrompt),
                width = props.getProperty("width", SavedGenerationSettings().width),
                height = props.getProperty("height", SavedGenerationSettings().height),
                steps = props.getProperty("steps", SavedGenerationSettings().steps),
                cfgScale = props.getProperty("cfgScale", SavedGenerationSettings().cfgScale),
                seed = props.getProperty("seed", SavedGenerationSettings().seed),
                sampler = props.getProperty("sampler", SavedGenerationSettings().sampler),
                leftPanelWidthDp = props.getProperty("leftPanelWidthDp", SavedGenerationSettings().leftPanelWidthDp.toString()).toIntOrNull()
                    ?: SavedGenerationSettings().leftPanelWidthDp,
                ideogramJsonPrompt = props.getProperty("ideogramJsonPrompt", SavedGenerationSettings().ideogramJsonPrompt),
            )
        }.getOrElse { SavedGenerationSettings() }
    }

    @Synchronized
    fun save(settings: SavedGenerationSettings) {
        val parentDir = settingsFile.absoluteFile.parentFile
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val props = Properties()
        props.setProperty("prompt", settings.prompt)
        props.setProperty("negativePrompt", settings.negativePrompt)
        props.setProperty("width", settings.width)
        props.setProperty("height", settings.height)
        props.setProperty("steps", settings.steps)
        props.setProperty("cfgScale", settings.cfgScale)
        props.setProperty("seed", settings.seed)
        props.setProperty("sampler", settings.sampler)
        props.setProperty("leftPanelWidthDp", settings.leftPanelWidthDp.toString())
        props.setProperty("ideogramJsonPrompt", settings.ideogramJsonPrompt)
        val tempFile = File.createTempFile("${settingsFile.name}.", ".tmp", parentDir)
        try {
            tempFile.outputStream().use { props.store(it, "Diffusion Desk Generation Settings") }
            try {
                Files.move(
                    tempFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            tempFile.delete()
        }
    }
}
