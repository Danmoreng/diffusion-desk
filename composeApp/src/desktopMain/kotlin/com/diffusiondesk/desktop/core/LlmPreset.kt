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
    Cpu,
    Gpu,
}

data class LlmRoleSettings(
    val taggingPresetId: String = "",
    val assistantPresetId: String = "",
    val promptEnhancerPresetId: String = "",
)
