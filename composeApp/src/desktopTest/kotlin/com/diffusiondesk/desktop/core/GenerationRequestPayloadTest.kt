package com.diffusiondesk.desktop.core

import com.diffusiondesk.desktop.core.api.GenerationRequest
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GenerationRequestPayloadTest {
    @Test
    fun includesInitImageAndDenoisingStrengthForGenerativeUpscale() {
        val payload = buildGenerationPayload(
            GenerationRequest(
                modelId = "",
                prompt = "{\"scene_description\":\"test\"}",
                negativePrompt = "",
                width = 2560,
                height = 1600,
                steps = 20,
                cfgScale = 5.0,
                seed = 42,
                sampler = "euler",
                saveImage = true,
                initImageBase64 = "encoded-image",
                strength = 0.25,
            ),
        )

        assertEquals("encoded-image", payload["init_image"]?.jsonPrimitive?.content)
        assertEquals(0.25, payload["strength"]?.jsonPrimitive?.double)
        assertEquals(2560, payload["width"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1600, payload["height"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun omitsInitImageForTextToImageRequests() {
        val payload = buildGenerationPayload(
            GenerationRequest(
                modelId = "",
                prompt = "test",
                negativePrompt = "",
                width = 1024,
                height = 1024,
                steps = 20,
                cfgScale = 1.0,
                seed = -1,
                sampler = "euler",
                saveImage = true,
            ),
        )

        assertFalse(payload.containsKey("init_image"))
    }
}
