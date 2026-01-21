package com.gettogether.app.util

import kotlinx.coroutines.delay

/**
 * Delays execution and retries a condition check before committing to an action.
 * Useful for avoiding race conditions with daemon initialization.
 *
 * @param delayMs How long to wait before rechecking
 * @param condition Check to perform after delay - returns true if retry succeeded
 * @param onRetrySuccess Suspend action to take if condition becomes true after delay
 * @return true if condition was satisfied after delay, false otherwise
 */
suspend fun procrastinate(
    delayMs: Long,
    condition: () -> Boolean,
    onRetrySuccess: suspend () -> Unit
): Boolean {
    delay(delayMs)
    return if (condition()) {
        onRetrySuccess()
        true
    } else {
        false
    }
}
