package com.gettogether.app.platform

/**
 * Platform-specific clipboard manager.
 */
expect class ClipboardManager {
    fun copyToClipboard(text: String)
}

expect fun provideClipboardManager(): ClipboardManager
