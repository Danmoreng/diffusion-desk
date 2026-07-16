package com.diffusiondesk.desktop.core.coroutines

import kotlinx.coroutines.CancellationException

inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

inline fun <T, R> Result<T>.mapCatchingCancellable(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { value -> runCatchingCancellable { transform(value) } },
        onFailure = { error ->
            if (error is CancellationException) throw error
            Result.failure(error)
        },
    )
