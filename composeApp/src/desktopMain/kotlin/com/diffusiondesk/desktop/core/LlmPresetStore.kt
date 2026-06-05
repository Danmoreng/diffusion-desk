package com.diffusiondesk.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Properties

class LlmPresetStore {
    val presetDir: File = File(AppPaths.appDir, "llm-presets")
    private val stateFile = File(AppPaths.appDir, "state.properties")
    private val json = Json { prettyPrint = true }

    fun load(): List<LlmPreset> {
        ensureSeedPreset()
        return presetDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file -> runCatching { parsePreset(file.readText()) }.getOrNull() }
    }

    fun save(preset: LlmPreset) {
        if (!presetDir.exists()) {
            presetDir.mkdirs()
        }
        File(presetDir, "${preset.id}.json").writeText(json.encodeToString(JsonObject.serializer(), preset.toJson()))
    }

    fun delete(id: String) {
        File(presetDir, "$id.json").delete()
        val roles = loadRoles()
        saveRoles(
            roles.copy(
                taggingPresetId = roles.taggingPresetId.takeUnless { it == id }.orEmpty(),
                assistantPresetId = roles.assistantPresetId.takeUnless { it == id }.orEmpty(),
                promptEnhancerPresetId = roles.promptEnhancerPresetId.takeUnless { it == id }.orEmpty(),
            ),
        )
    }

    fun loadRoles(): LlmRoleSettings {
        if (!stateFile.exists()) {
            return LlmRoleSettings()
        }
        return runCatching {
            val props = Properties()
            stateFile.inputStream().use(props::load)
            LlmRoleSettings(
                taggingPresetId = props.getProperty("taggingLlmPresetId", ""),
                assistantPresetId = props.getProperty("assistantLlmPresetId", ""),
                promptEnhancerPresetId = props.getProperty("promptEnhancerLlmPresetId", ""),
            )
        }.getOrDefault(LlmRoleSettings())
    }

    fun saveRoles(roles: LlmRoleSettings) {
        if (!AppPaths.appDir.exists()) {
            AppPaths.appDir.mkdirs()
        }
        val props = Properties()
        if (stateFile.exists()) {
            runCatching { stateFile.inputStream().use(props::load) }
        }
        props.setProperty("taggingLlmPresetId", roles.taggingPresetId)
        props.setProperty("assistantLlmPresetId", roles.assistantPresetId)
        props.setProperty("promptEnhancerLlmPresetId", roles.promptEnhancerPresetId)
        stateFile.outputStream().use { props.store(it, "Diffusion Desk App State") }
    }

    private fun ensureSeedPreset() {
        if (!presetDir.exists()) {
            presetDir.mkdirs()
        }
        val seedFile = File(presetDir, "small-tagger-cpu.example.json")
        if (!seedFile.exists()) {
            seedFile.writeText(json.encodeToString(JsonObject.serializer(), smallTaggerExample().toJson()))
        }
    }

    private fun parsePreset(raw: String): LlmPreset {
        val root = json.parseToJsonElement(raw).jsonObject
        val placement = root.string("placement", "cpu").lowercase()
        return LlmPreset(
            id = root.string("id"),
            name = root.string("name", root.string("id")),
            modelPath = root.string("model_path"),
            mmprojPath = root.string("mmproj_path"),
            placement = if (placement == "gpu") LlmPlacement.Gpu else LlmPlacement.Cpu,
            advancedArgs = root.string("advanced_args"),
        )
    }

    private fun LlmPreset.toJson(): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("model_path", JsonPrimitive(modelPath))
        if (mmprojPath.isNotBlank()) {
            put("mmproj_path", JsonPrimitive(mmprojPath))
        }
        put("placement", JsonPrimitive(placement.name.lowercase()))
        if (advancedArgs.isNotBlank()) {
            put("advanced_args", JsonPrimitive(advancedArgs))
        }
    }

    private fun smallTaggerExample() = LlmPreset(
        id = "small-tagger-cpu.example",
        name = "Small Tagger CPU Example",
        modelPath = "llm/small-tagger.gguf",
        placement = LlmPlacement.Cpu,
        advancedArgs = "--ctx-size 2048",
    )

    private fun JsonObject.string(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default
}
