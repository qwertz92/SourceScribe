package app.sourcescribe.core

import kotlin.random.Random

private const val MAX_RETRY_DELAY_BASE_MILLIS = 86_400_000L
private const val MAX_RETRY_DELAY_JITTER_MILLIS = 5_000L

/**
 * Adds bounded, non-negative jitter to a retry delay.
 *
 * The caller supplies the already-clamped server minimum (for example
 * Retry-After). The result is never shorter than that minimum, and the base
 * is bounded to one day so malformed retry values cannot create unbounded
 * waits or overflow the addition.
 */
fun retryDelayMillis(baseMillis: Long): Long {
    require(baseMillis in 0..MAX_RETRY_DELAY_BASE_MILLIS) {
        "retry delay base must be between 0 and $MAX_RETRY_DELAY_BASE_MILLIS milliseconds"
    }
    val maximumJitter = minOf(MAX_RETRY_DELAY_JITTER_MILLIS, baseMillis / 5L)
    if (maximumJitter == 0L) return baseMillis

    val jitter = Random.Default.nextLong(maximumJitter + 1L)
    return baseMillis + jitter
}
