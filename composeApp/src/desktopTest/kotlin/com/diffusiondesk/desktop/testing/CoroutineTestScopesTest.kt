package com.diffusiondesk.desktop.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoroutineTestScopesTest {
    @Test
    fun delayedWorkAdvancesOnlyWhenTheSchedulerAdvances() = runDeterministicTest {
        var completed = false
        launch {
            delay(1_000)
            completed = true
        }

        runCurrent()
        assertFalse(completed)
        testScheduler.advanceTimeBy(999)
        runCurrent()
        assertFalse(completed)
        testScheduler.advanceTimeBy(1)
        runCurrent()
        assertTrue(completed)
    }

    @Test
    fun cancellationIsNotConvertedToARegularFailure() = runDeterministicTest {
        assertCancellationPropagates {
            throw kotlinx.coroutines.CancellationException("test cancellation")
        }
    }
}
