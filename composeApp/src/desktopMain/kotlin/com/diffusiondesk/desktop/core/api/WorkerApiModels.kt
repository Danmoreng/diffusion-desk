package com.diffusiondesk.desktop.core.api

data class ServerConfig(
    val modelDir: String,
    val outputDir: String,
    val setupCompleted: Boolean,
)

data class ModelSummary(
    val id: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val loaded: Boolean,
)

data class ModelMetadata(
    val id: String,
    val triggerWord: String = "",
    val previewPath: String = "",
)

data class ProgressSnapshot(
    val step: Int,
    val steps: Int,
    val time: Double,
    val phase: String,
    val message: String,
)
