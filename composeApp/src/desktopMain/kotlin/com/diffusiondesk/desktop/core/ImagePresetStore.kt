package com.diffusiondesk.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Properties

class ImagePresetStore {
    val presetDir: File = File(AppPaths.appDir, "image-presets")
    private val stateFile = File(AppPaths.appDir, "state.properties")
    private val json = Json { prettyPrint = true }

    fun load(): List<ImagePreset> {
        ensureSeedPreset()
        return presetDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file -> runCatching { parsePreset(file.readText()) }.getOrNull() }
    }

    fun save(preset: ImagePreset) {
        if (!presetDir.exists()) {
            presetDir.mkdirs()
        }
        File(presetDir, "${preset.id}.json").writeText(json.encodeToString(JsonObject.serializer(), preset.toJson()))
    }

    fun delete(id: String) {
        File(presetDir, "$id.json").delete()
        if (loadLastPresetId() == id) {
            saveLastPresetId("")
        }
    }

    fun loadLastPresetId(): String {
        if (!stateFile.exists()) {
            return ""
        }
        return runCatching {
            val props = Properties()
            stateFile.inputStream().use(props::load)
            props.getProperty("lastImagePresetId", "")
        }.getOrDefault("")
    }

    fun saveLastPresetId(id: String) {
        if (!AppPaths.appDir.exists()) {
            AppPaths.appDir.mkdirs()
        }
        val props = Properties()
        if (stateFile.exists()) {
            runCatching { stateFile.inputStream().use(props::load) }
        }
        props.setProperty("lastImagePresetId", id)
        stateFile.outputStream().use { props.store(it, "Diffusion Desk App State") }
    }

    private fun ensureSeedPreset() {
        if (!presetDir.exists()) {
            presetDir.mkdirs()
        }
        val seedFile = File(presetDir, "z-image-turbo.example.json")
        if (!seedFile.exists() || seedFile.readText().contains("z-image-turbo.gguf") || seedFile.readText().contains("Qwen3-4B.gguf")) {
            seedFile.writeText(json.encodeToString(JsonObject.serializer(), zImageTurboExample().toJson()))
        }
    }

    private fun parsePreset(raw: String): ImagePreset {
        val root = json.parseToJsonElement(raw).jsonObject
        val components = root["components"]?.jsonObject ?: JsonObject(emptyMap())
        val placement = root["placement"]?.jsonObject ?: JsonObject(emptyMap())
        val defaults = root["defaults"]?.jsonObject ?: JsonObject(emptyMap())

        return ImagePreset(
            id = root.string("id"),
            name = root.string("name", root.string("id")),
            diffusionModel = components.string("diffusion_model", root.string("diffusion_model")),
            vae = components.string("vae"),
            clipL = components.string("clip_l"),
            clipG = components.string("clip_g"),
            t5xxl = components.string("t5xxl"),
            llm = components.string("llm"),
            clipOnCpu = placement.boolean("clip_on_cpu"),
            vaeOnCpu = placement.boolean("vae_on_cpu"),
            offloadParamsToCpu = placement.boolean("offload_params_to_cpu"),
            flashAttention = placement.boolean("flash_attn"),
            defaultWidth = defaults.int("width", 1024),
            defaultHeight = defaults.int("height", 1024),
            defaultSteps = defaults.int("steps", 4),
            defaultCfgScale = defaults.double("cfg_scale", 1.0),
            defaultSampler = defaults.string("sampler", "euler_a"),
            defaultNegativePrompt = defaults.string("negative_prompt", "deformed, blurry, low quality, watermark"),
        )
    }

    private fun ImagePreset.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("components", buildJsonObject {
            put("diffusion_model", JsonPrimitive(diffusionModel))
            putIfNotBlank("vae", vae)
            putIfNotBlank("clip_l", clipL)
            putIfNotBlank("clip_g", clipG)
            putIfNotBlank("t5xxl", t5xxl)
            putIfNotBlank("llm", llm)
        })
        put("placement", buildJsonObject {
            put("clip_on_cpu", JsonPrimitive(clipOnCpu))
            put("vae_on_cpu", JsonPrimitive(vaeOnCpu))
            put("offload_params_to_cpu", JsonPrimitive(offloadParamsToCpu))
            put("flash_attn", JsonPrimitive(flashAttention))
        })
        put("defaults", buildJsonObject {
            put("width", JsonPrimitive(defaultWidth))
            put("height", JsonPrimitive(defaultHeight))
            put("steps", JsonPrimitive(defaultSteps))
            put("cfg_scale", JsonPrimitive(defaultCfgScale))
            put("sampler", JsonPrimitive(defaultSampler))
            put("negative_prompt", JsonPrimitive(defaultNegativePrompt))
        })
    }

    private fun zImageTurboExample() = ImagePreset(
        id = "z-image-turbo.example",
        name = "Z-Image Turbo Example",
        diffusionModel = "stable-diffusion/z_image_turbo-Q8_0.gguf",
        llm = "text-encoder/Qwen3-4B-Instruct-2507-Q8_0.gguf",
        vae = "vae/ae.safetensors",
        vaeOnCpu = true,
        flashAttention = true,
        defaultWidth = 1024,
        defaultHeight = 1024,
        defaultSteps = 4,
        defaultCfgScale = 1.0,
        defaultSampler = "euler_a",
    )

    private fun JsonObject.string(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.double(key: String, default: Double): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: default

    private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObjectBuilder.putIfNotBlank(key: String, value: String) {
        if (value.isNotBlank()) {
            put(key, JsonPrimitive(value))
        }
    }
}
