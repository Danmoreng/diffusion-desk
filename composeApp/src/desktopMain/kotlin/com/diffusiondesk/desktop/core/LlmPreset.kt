package com.diffusiondesk.desktop.core

data class LlmPreset(
    val id: String,
    val name: String,
    val modelPath: String,
    val mmprojPath: String = "",
    val placement: LlmPlacement = LlmPlacement.Cpu,
    val advancedArgs: String = "",
)

enum class LlmPlacement {
    Auto,
    Cpu,
    Gpu,
}

private val autoFitDefaults = listOf(
    listOf("--fit", "on"),
    listOf("--fit-target", "1024"),
    listOf("--fit-ctx", "4096"),
)

fun LlmPreset.effectiveAdvancedArgs(): Result<List<String>> = runCatching {
    val args = CommandLineArgs.parse(advancedArgs).getOrThrow()
    CommandLineArgs.validateNoReservedOptions(args).getOrThrow()
    if (placement != LlmPlacement.Auto) {
        return@runCatching args
    }

    autoFitDefaults.fold(args) { current, defaultArg ->
        val option = defaultArg.first()
        if (current.hasOption(option)) current else current + defaultArg
    }
}

private fun List<String>.hasOption(longOption: String): Boolean {
    val aliases = when (longOption) {
        "--fit" -> setOf("--fit", "-fit")
        "--fit-target" -> setOf("--fit-target", "-fitt")
        "--fit-ctx" -> setOf("--fit-ctx", "-fitc")
        else -> setOf(longOption)
    }
    return any { token -> token.substringBefore("=") in aliases }
}

data class LlmRoleSettings(
    val taggingPresetId: String = "",
    val assistantPresetId: String = "",
    val promptEnhancerPresetId: String = "",
)
