package com.diffusiondesk.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandLineArgsTest {
    @Test
    fun parsesQuotedValuesWithoutShellExpansion() {
        val parsed = CommandLineArgs.parse("""--threads 8 --ctx-size "4096" --prompt 'hello world'""").getOrThrow()

        assertEquals(listOf("--threads", "8", "--ctx-size", "4096", "--prompt", "hello world"), parsed)
    }

    @Test
    fun rejectsUnclosedQuotes() {
        val parsed = CommandLineArgs.parse("""--ctx-size "4096""")

        assertTrue(parsed.isFailure)
    }

    @Test
    fun rejectsReservedAppManagedOptions() {
        val parsed = CommandLineArgs.parse("--listen-port 9000 --threads 4").getOrThrow()
        val validated = CommandLineArgs.validateNoReservedOptions(parsed)

        assertTrue(validated.isFailure)
    }
}
