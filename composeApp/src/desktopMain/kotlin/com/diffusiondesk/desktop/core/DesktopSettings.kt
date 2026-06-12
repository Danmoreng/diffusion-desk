package com.diffusiondesk.desktop.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DesktopSettings(
    val repoRoot: String,
    val listenPort: Int,
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
    val themeMode: String,
    val actionBarPosition: String,
    val saveImagesAutomatically: Boolean,
    val showCompositionOverlay: Boolean,
    val showLlmDebugConsole: Boolean,
    val vramBudgetMode: String,
    val manualVramBudgetGb: Double,
    val autostartLlmWorkers: Boolean,
    val galleryPreviewWidthDp: Int,
)

fun detectDefaultRepoRoot(): String {
    detectPackagedAppRoot()?.let { return it.absolutePath }

    var current = File(System.getProperty("user.dir")).absoluteFile
    repeat(6) {
        if (File(current, "settings.gradle.kts").exists() && File(current, "src").exists()) {
            return current.absolutePath
        }
        current = current.parentFile ?: return@repeat
    }
    return File(System.getProperty("user.dir")).absolutePath
}

private fun detectPackagedAppRoot(): File? {
    val candidates = mutableListOf<File>()
    candidates += File(System.getProperty("user.dir")).absoluteFile

    ProcessHandle.current().info().command().ifPresent { command ->
        val executable = File(command).absoluteFile
        executable.parentFile?.let { candidates += it }
    }

    return candidates
        .flatMap { candidate -> sequenceOf(candidate, candidate.parentFile).filterNotNull().toList() }
        .distinctBy { it.absolutePath.lowercase() }
        .firstOrNull { root ->
            File(root, "build/bin/diffusion_desk_sd_worker.exe").exists() ||
                File(root, "build/bin/diffusion_desk_sd_worker").exists()
        }
}

fun detectDefaultModelDir(repoRoot: String): String {
    val configModelDir = runCatching {
        val configFile = File(repoRoot, "config.json")
        if (!configFile.exists()) {
            return@runCatching ""
        }
        Json.parseToJsonElement(configFile.readText())
            .jsonObject["paths"]
            ?.jsonObject
            ?.get("model_dir")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
    }.getOrDefault("")

    return configModelDir.ifBlank { File(repoRoot, "models").absolutePath }
}

fun detectDefaultOutputDir(repoRoot: String): String {
    val configOutputDir = runCatching {
        val configFile = File(repoRoot, "config.json")
        if (!configFile.exists()) {
            return@runCatching ""
        }
        Json.parseToJsonElement(configFile.readText())
            .jsonObject["paths"]
            ?.jsonObject
            ?.get("output_dir")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
    }.getOrDefault("")

    return configOutputDir.ifBlank { File(repoRoot, "outputs").absolutePath }
}
