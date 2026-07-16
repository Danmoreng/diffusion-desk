package com.diffusiondesk.desktop.core.api

data class LlmHealth(
    val modelLoaded: Boolean,
    val modelPath: String,
    val placement: String,
    val nGpuLayers: Int,
    val advancedArgs: List<String>,
    val vramAllocatedMb: Int,
    val vramFreeMb: Int,
)

data class LlmChatMessage(
    val role: String,
    val content: String = "",
    val imageDataUri: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmChatCompletion(
    val content: String,
    val reasoningContent: String,
    val finishReason: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val promptTokensPerSecond: Double?,
    val predictedTokensPerSecond: Double?,
    val elapsedMs: Long,
    val rawResponsePreview: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
)
