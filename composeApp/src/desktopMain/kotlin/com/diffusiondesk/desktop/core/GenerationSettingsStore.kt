package com.diffusiondesk.desktop.core

import java.io.File
import java.util.Properties

data class SavedGenerationSettings(
    val prompt: String = "A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night.",
    val negativePrompt: String = "deformed, blurry, low quality, watermark",
    val width: String = "1024",
    val height: String = "768",
    val steps: String = "4",
    val cfgScale: String = "1.0",
    val seed: String = "-1",
    val batchCount: String = "1",
    val sampler: String = "euler_a",
)

class GenerationSettingsStore {
    private val settingsFile = File(AppPaths.appDir, "generation.properties")

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
                batchCount = props.getProperty("batchCount", SavedGenerationSettings().batchCount),
                sampler = props.getProperty("sampler", SavedGenerationSettings().sampler),
            )
        }.getOrElse { SavedGenerationSettings() }
    }

    fun save(settings: SavedGenerationSettings) {
        if (!AppPaths.appDir.exists()) {
            AppPaths.appDir.mkdirs()
        }
        val props = Properties()
        props.setProperty("prompt", settings.prompt)
        props.setProperty("negativePrompt", settings.negativePrompt)
        props.setProperty("width", settings.width)
        props.setProperty("height", settings.height)
        props.setProperty("steps", settings.steps)
        props.setProperty("cfgScale", settings.cfgScale)
        props.setProperty("seed", settings.seed)
        props.setProperty("batchCount", settings.batchCount)
        props.setProperty("sampler", settings.sampler)
        settingsFile.outputStream().use { props.store(it, "Diffusion Desk Generation Settings") }
    }
}
