package com.diffusiondesk.desktop.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class CancellableResultTest {
    @Test
    fun runCatchingCancellableRethrowsCancellationWithoutFailureStateMutation() {
        var failureStateMutated = false

        assertFailsWith<CancellationException> {
            runCatchingCancellable<Unit> {
                throw CancellationException("cancelled")
            }.onFailure {
                failureStateMutated = true
            }
        }

        assertFalse(failureStateMutated)
    }

    @Test
    fun mapCatchingCancellableRethrowsCancellation() {
        assertFailsWith<CancellationException> {
            Result.success(Unit).mapCatchingCancellable {
                throw CancellationException("cancelled")
            }
        }
    }
}
