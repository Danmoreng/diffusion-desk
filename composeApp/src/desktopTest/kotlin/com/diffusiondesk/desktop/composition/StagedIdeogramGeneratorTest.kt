package com.diffusiondesk.desktop.composition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class StagedIdeogramGeneratorTest {
    @Test
    fun completesAllStagesAndPublishesDrafts() = runBlocking {
        val responses = ArrayDeque(successResponses())
        val progress = mutableListOf<StagedIdeogramProgress>()
        val systemPrompts = mutableListOf<String>()
        val generator = StagedIdeogramGenerator { systemPrompt, _, _ ->
            systemPrompts += systemPrompt
            responses.removeFirst()
        }

        val result = generator.run("poster", 1000, 1500, onProgress = progress::add).getOrThrow()

        assertEquals("A strawberry poster with a title", result.highLevelDescription)
        assertEquals(2, result.elements.size)
        assertEquals(2, result.detailedElementCount)
        assertEquals(listOf(100, 200, 800, 800), result.elements[0]["bbox"]?.let(::jsonIntList))
        assertTrue(result.completeJson().contains("EAT ME"))
        assertEquals(StagedIdeogramStep.Finalize, progress.last().step)
        assertTrue(progress.any { it.elementIndex == 1 && it.elementCount == 2 })
        assertTrue(progress.first().draft.previewJson().contains("\"compositional_deconstruction\""))
        assertTrue(progress.first().draft.previewJson().contains("\"background\": \"\""))
        assertTrue(progress.first().draft.previewJson().contains("\"elements\": []"))
        assertTrue(systemPrompts.all { it.contains("<ideogram4_json_schema>") })
        assertTrue(systemPrompts.all { it.contains("\"${'$'}schema\": \"https://json-schema.org/draft/2020-12/schema\"") })
        assertTrue(systemPrompts.all { it.contains("\"${'$'}ref\": \"#/${'$'}defs/objectElement\"") })
        assertTrue(systemPrompts.all { it.contains("Return only the stage-specific response fragment") })
    }

    @Test
    fun sendsValidationErrorBackToModelAndContinuesAutomatically() = runBlocking {
        val invalidStyle = """{"high_level_description":"A strawberry poster with a title","style_description":{"aesthetics":"clean","lighting":"soft","medium":"photo"}}"""
        val responses = ArrayDeque(listOf(invalidStyle) + successResponses())
        val userPrompts = mutableListOf<String>()
        val repairs = mutableListOf<String>()
        val generator = StagedIdeogramGenerator { _, userPrompt, _ ->
            userPrompts += userPrompt
            responses.removeFirst()
        }

        val result = generator.run(
            sourcePrompt = "poster",
            width = 1000,
            height = 1500,
            onValidationRepair = { _, attempt, max, error -> repairs += "$attempt/$max: $error" },
            onProgress = {},
        ).getOrThrow()

        assertEquals(7, userPrompts.size)
        assertTrue(userPrompts[1].contains("The required \"medium\" field does not count"))
        assertTrue(userPrompts[1].contains(invalidStyle))
        assertEquals(1, repairs.size)
        assertTrue(repairs.single().contains("Add exactly one separate string field"))
        assertEquals("editorial", result.style?.get("photo")?.jsonPrimitive?.content)
    }

    @Test
    fun retriesFailedElementWithoutRepeatingAcceptedElement() = runBlocking {
        val firstResponses = ArrayDeque(successResponses().take(4) + "not-json")
        var acceptedDraft = StagedIdeogramDraft()
        var activeStep = StagedIdeogramStep.SceneAndStyle
        val first = StagedIdeogramGenerator { _, _, _ -> firstResponses.removeFirst() }

        val failed = first.run(
            sourcePrompt = "poster",
            width = 1000,
            height = 1500,
            onStepStarted = { activeStep = it },
            onProgress = { acceptedDraft = it.draft },
        )

        assertTrue(failed.isFailure)
        assertEquals(StagedIdeogramStep.ElementDetails, activeStep)
        assertEquals(1, acceptedDraft.detailedElementCount)

        var retryCalls = 0
        val retryResponses = ArrayDeque(listOf(successResponses()[4], successResponses()[5]))
        val retry = StagedIdeogramGenerator { _, _, _ -> retryCalls++; retryResponses.removeFirst() }
            .run(
                sourcePrompt = "poster",
                width = 1000,
                height = 1500,
                initialDraft = acceptedDraft,
                startStep = StagedIdeogramStep.ElementDetails,
                onProgress = { acceptedDraft = it.draft },
            ).getOrThrow()

        assertEquals(2, retryCalls)
        assertEquals(2, retry.detailedElementCount)
        assertEquals("Detailed strawberry", retry.elements[0]["desc"]?.jsonPrimitive?.content)
    }

    private fun successResponses(): List<String> = listOf(
        """{"high_level_description":"A strawberry poster with a title","style_description":{"aesthetics":"clean","lighting":"soft","photo":"editorial","medium":"photograph","color_palette":["#FF0000"]}}""",
        """{"background":"A quiet studio"}""",
        """{"elements":[{"type":"obj","desc":"Strawberry"},{"type":"text","text":"EAT ME","desc":"Title"}]}""",
        """{"element":{"type":"obj","desc":"Detailed strawberry","color_palette":["#FF0000","#008000"]}}""",
        """{"element":{"type":"text","text":"EAT ME","desc":"Bold white title","color_palette":["#FFFFFF"]}}""",
        """{"placements":[{"index":0,"bbox":[100,200,800,800]},{"index":1,"bbox":[820,200,940,800]}]}""",
    )
}

private fun jsonIntList(value: kotlinx.serialization.json.JsonElement): List<Int> =
    value.jsonArray.map { it.jsonPrimitive.content.toInt() }
