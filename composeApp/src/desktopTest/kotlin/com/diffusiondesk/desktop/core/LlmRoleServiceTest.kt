package com.diffusiondesk.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmRoleServiceTest {
    @Test
    fun parsesSingleValueFieldPatch() {
        assertEquals("Detailed studio background", parseIdeogramFieldImprovement("""{"value":"Detailed studio background"}"""))
    }

    @Test
    fun rejectsAdditionalPatchProperties() {
        assertFailsWith<IllegalArgumentException> {
            parseIdeogramFieldImprovement("""{"value":"Changed","background":"Also changed"}""")
        }
    }
}
