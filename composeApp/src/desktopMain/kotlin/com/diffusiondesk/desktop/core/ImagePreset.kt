package com.diffusiondesk.desktop.core

enum class ImagePromptMode(
    val storageValue: String,
    val displayName: String,
) {
    Text("text", "Text"),
    Json("json", "JSON"),
    ;

    companion object {
        fun fromStorage(value: String, default: ImagePromptMode = Text): ImagePromptMode =
            values().firstOrNull { mode ->
                value.equals(mode.storageValue, ignoreCase = true) ||
                    value.equals(mode.name, ignoreCase = true) ||
                    value.equals(mode.displayName, ignoreCase = true)
            } ?: default
    }
}

data class ImagePreset(
    val id: String,
    val name: String,
    val diffusionModel: String,
    val uncondDiffusionModel: String = "",
    val vae: String = "",
    val clipL: String = "",
    val clipG: String = "",
    val t5xxl: String = "",
    val llm: String = "",
    val clipOnCpu: Boolean = false,
    val vaeOnCpu: Boolean = false,
    val offloadParamsToCpu: Boolean = false,
    val flashAttention: Boolean = false,
    val maxVramGb: Double = 0.0,
    val streamLayers: Boolean = false,
    val promptMode: ImagePromptMode = ImagePromptMode.Text,
    val defaultWidth: Int = 1024,
    val defaultHeight: Int = 1024,
    val defaultSteps: Int = 4,
    val defaultCfgScale: Double = 1.0,
    val defaultSampler: String = "euler_a",
    val defaultNegativePrompt: String = "deformed, blurry, low quality, watermark",
)
