package com.diffusiondesk.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryPromptModeTest {
    @Test
    fun recognizesIdeogramMetadataAndCompositionJson() {
        val json = """{"compositional_deconstruction":{"background":"Studio","elements":[]}}"""

        assertEquals(ImagePromptMode.Json, inferGalleryPromptMode("prompt", "", "ideogram4-q8"))
        assertEquals(ImagePromptMode.Json, inferGalleryPromptMode("prompt", "stable-diffusion/ideogram4.gguf", ""))
        assertEquals(ImagePromptMode.Json, inferGalleryPromptMode(json, "", ""))
        assertEquals(ImagePromptMode.Text, inferGalleryPromptMode("A normal image prompt", "model.gguf", "photo"))
    }
}
