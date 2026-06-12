package com.diffusiondesk.desktop.core

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LlmDebugEntry(
    val id: Long,
    val startedAt: Instant,
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val response: String? = null,
    val error: String? = null,
)

class LlmDebugLog {
    private val nextId = AtomicLong(1)
    private val mutableEntries = MutableStateFlow<List<LlmDebugEntry>>(emptyList())
    val entries: StateFlow<List<LlmDebugEntry>> = mutableEntries.asStateFlow()

    fun start(model: String, systemPrompt: String, userPrompt: String): Long {
        val entry = LlmDebugEntry(
            id = nextId.getAndIncrement(),
            startedAt = Instant.now(),
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
        synchronized(this) {
            mutableEntries.value = (mutableEntries.value + entry).takeLast(MAX_ENTRIES)
        }
        return entry.id
    }

    fun complete(id: Long, response: String) = update(id) { it.copy(response = response) }

    fun fail(id: Long, error: Throwable) = update(id) {
        it.copy(error = error.message ?: error::class.simpleName ?: "LLM call failed.")
    }

    fun clear() {
        synchronized(this) {
            mutableEntries.value = emptyList()
        }
    }

    private fun update(id: Long, transform: (LlmDebugEntry) -> LlmDebugEntry) {
        synchronized(this) {
            mutableEntries.value = mutableEntries.value.map { if (it.id == id) transform(it) else it }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
