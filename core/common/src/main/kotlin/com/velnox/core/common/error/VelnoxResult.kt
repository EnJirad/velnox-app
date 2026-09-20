package com.velnox.core.common.error

import kotlinx.coroutines.CancellationException

/**
 * Result wrapper used by every repository in the project.
 *
 * Call sites must branch on [VelnoxResult.Failure] — there is deliberately no
 * `getOrNull()` shortcut for critical commerce data, because the product rule is
 * that a failed catalogue/order call must never be rendered as empty success.
 */
sealed interface VelnoxResult<out T> {

    data class Success<out T>(val data: T) : VelnoxResult<T>

    data class Failure(val error: AppError) : VelnoxResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

/** `map` on the success channel, passing failures through untouched. */
inline fun <T, R> VelnoxResult<T>.map(transform: (T) -> R): VelnoxResult<R> = when (this) {
    is VelnoxResult.Success -> VelnoxResult.Success(transform(data))
    is VelnoxResult.Failure -> this
}

/** Chain a second fallible call on the success channel. */
inline fun <T, R> VelnoxResult<T>.flatMap(transform: (T) -> VelnoxResult<R>): VelnoxResult<R> = when (this) {
    is VelnoxResult.Success -> transform(data)
    is VelnoxResult.Failure -> this
}

/** Run [block], converting thrown errors to [AppError.Unexpected]. */
inline fun <T> velnoxRunCatching(block: () -> T): VelnoxResult<T> = try {
    VelnoxResult.Success(block())
} catch (cancellation: CancellationException) {
    // Structured concurrency must always win — never swallow cancellation.
    throw cancellation
} catch (throwable: Throwable) {
    VelnoxResult.Failure(throwable.toAppError())
}

/** Last-resort throwable → [AppError] mapping (network/IO types live in core:network). */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    else -> AppError.Unexpected(serverMessage = message, cause = this)
}
