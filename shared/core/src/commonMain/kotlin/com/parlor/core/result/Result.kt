package com.parlor.core.result

/**
 * Result<T, E> — the project's standard wrapper for fallible operations.
 *
 * Differs from kotlin.Result by being typed on the error: callers know exactly
 * which error categories they need to handle, and the compiler enforces it.
 *
 * Use [map], [mapError], [flatMap], [onSuccess], [onFailure] to chain operations.
 */
sealed interface Result<out T, out E> {
    data class Success<out T>(val data: T) : Result<T, Nothing>
    data class Failure<out E>(val error: E) : Result<Nothing, E>
}

/**
 * For operations that succeed-with-no-data — e.g., persisting a snapshot.
 * Avoids the cluttered `Result<Unit, E>` signature.
 */
typealias EmptyResult<E> = Result<Unit, E>

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Success -> transform(data)
    is Result.Failure -> this
}

inline fun <T, E, F> Result<T, E>.mapError(transform: (E) -> F): Result<T, F> = when (this) {
    is Result.Success -> this
    is Result.Failure -> Result.Failure(transform(error))
}

inline fun <T, E> Result<T, E>.onSuccess(block: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) block(data)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(block: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) block(error)
    return this
}

fun <T, E> Result<T, E>.getOrNull(): T? = (this as? Result.Success)?.data
fun <T, E> Result<T, E>.errorOrNull(): E? = (this as? Result.Failure)?.error

inline fun <T, E> Result<T, E>.getOrElse(default: (E) -> T): T = when (this) {
    is Result.Success -> data
    is Result.Failure -> default(error)
}

fun <T> T.success(): Result<T, Nothing> = Result.Success(this)
fun <E> E.failure(): Result<Nothing, E> = Result.Failure(this)

/** Returns a successful empty result. Use as the success terminator for [EmptyResult]. */
val EmptyOk: EmptyResult<Nothing> = Result.Success(Unit)
