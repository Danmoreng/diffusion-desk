package com.diffusiondesk.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LlmRoleServiceTest {
    @Test
    fun infersContextLimitFromFitCtxWithKSuffix() {
        val limit = inferLlmContextLimit(listOf("--fit", "on", "--fit-ctx", "24k"))

        assertEquals(24 * 1024, limit.tokens)
        assertEquals("fit-ctx=24k", limit.source)
    }

    @Test
    fun infersContextLimitFromCtxSizeEqualsSyntax() {
        val limit = inferLlmContextLimit(listOf("--ctx-size=8192"))

        assertEquals(8192, limit.tokens)
        assertEquals("ctx-size=8192", limit.source)
    }
}
