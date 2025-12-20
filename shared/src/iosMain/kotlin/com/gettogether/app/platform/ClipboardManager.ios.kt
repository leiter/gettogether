package com.gettogether.app.platform

import platform.UIKit.UIPasteboard

actual class ClipboardManager {
    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

actual fun provideClipboardManager(): ClipboardManager {
    return ClipboardManager()
}
