package com.diffusiondesk.desktop.viewmodel

import com.diffusiondesk.desktop.composition.StagedIdeogramGenerator
import com.diffusiondesk.desktop.composition.StagedIdeogramProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class StagedCompositionDraftTest {
    @Test
    fun everyPublishedDraftCanRenderInCompositionUi() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                """{"high_level_description":"A poster","style_description":{"aesthetics":"clean","lighting":"soft","photo":"editorial","medium":"photograph"}}""",
                """{"background":"A quiet studio"}""",
                """{"elements":[{"type":"obj","desc":"Strawberry"},{"type":"text","text":"EAT ME","desc":"Title"}]}""",
                """{"element":{"type":"obj","desc":"Detailed strawberry","color_palette":["#FF0000"]}}""",
                """{"element":{"type":"text","text":"EAT ME","desc":"Bold title","color_palette":["#FFFFFF"]}}""",
                """{"placements":[{"index":0,"bbox":[100,200,800,800]},{"index":1,"bbox":[820,200,940,800]}]}""",
            ),
        )
        val progress = mutableListOf<StagedIdeogramProgress>()

        StagedIdeogramGenerator { _, _ -> responses.removeFirst() }
            .run("poster", 1000, 1500, onProgress = progress::add)
            .getOrThrow()

        progress.forEach { update ->
            assertNotNull(parseIdeogramCompositionDocument(update.draft.previewJson()).getOrNull())
        }
        assertEquals(0, parseIdeogramCompositionDocument(progress.first().draft.previewJson()).getOrThrow().elements.size)
        assertEquals(2, parseIdeogramCompositionDocument(progress[2].draft.previewJson()).getOrThrow().elements.size)
    }
}
