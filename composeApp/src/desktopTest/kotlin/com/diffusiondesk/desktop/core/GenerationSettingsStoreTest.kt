package com.diffusiondesk.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerationSettingsStoreTest {
    @Test
    fun persistsMultilineIdeogramJsonPrompt() {
        val directory = Files.createTempDirectory("diffusion-desk-generation-settings").toFile()
        val file = directory.resolve("generation.properties")
        val store = GenerationSettingsStore(file)
        val jsonPrompt = """
            {
              "high_level_description": "Persisted scene",
              "compositional_deconstruction": {
                "background": "Studio",
                "elements": []
              }
            }
        """.trimIndent()

        try {
            store.save(SavedGenerationSettings(prompt = "Text prompt", ideogramJsonPrompt = jsonPrompt))

            val restored = store.load()
            assertEquals("Text prompt", restored.prompt)
            assertEquals(jsonPrompt, restored.ideogramJsonPrompt)
        } finally {
            directory.deleteRecursively()
        }
    }
}
