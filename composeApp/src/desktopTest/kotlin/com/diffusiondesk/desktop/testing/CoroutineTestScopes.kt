package com.diffusiondesk.desktop.testing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
fun testScope(dispatcher: TestDispatcher = StandardTestDispatcher()): TestScope = TestScope(dispatcher)

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.advancePendingWork() {
    testScheduler.advanceUntilIdle()
}

@OptIn(ExperimentalCoroutinesApi::class)
fun runDeterministicTest(block: suspend TestScope.() -> Unit) {
    runTest {
        block()
    }
}

suspend fun assertCancellationPropagates(block: suspend () -> Unit) {
    var cancellation: CancellationException? = null
    try {
        block()
    } catch (error: CancellationException) {
        cancellation = error
    }
    assertNotNull(cancellation, "Expected cancellation to propagate")
}
