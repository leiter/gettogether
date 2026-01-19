package com.gettogether.app.data.repository

/**
 * Utility for detecting ban-related errors from Jami daemon responses.
 *
 * Ban errors are often temporary (e.g., due to stale cached ban state) and
 * can potentially be resolved by refreshing contact status and retrying.
 */
object BanErrorDetector {

    /**
     * Keywords that indicate a ban or block-related error.
     */
    private val BAN_KEYWORDS = listOf(
        "ban",
        "blocked",
        "not allowed",
        "forbidden",
        "permission denied"
    )

    /**
     * Check if an exception message indicates a ban-related error.
     *
     * @param e The exception to check
     * @return true if the error appears to be ban-related
     */
    fun isBanRelatedError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return BAN_KEYWORDS.any { keyword -> msg.contains(keyword) }
    }

    /**
     * Check if a message string indicates a ban-related error.
     *
     * @param message The error message to check
     * @return true if the message appears to be ban-related
     */
    fun isBanRelatedMessage(message: String?): Boolean {
        val msg = message?.lowercase() ?: return false
        return BAN_KEYWORDS.any { keyword -> msg.contains(keyword) }
    }
}
