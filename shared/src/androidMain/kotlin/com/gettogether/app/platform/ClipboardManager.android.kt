package com.gettogether.app.platform

import android.content.ClipData
import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class ClipboardManager(private val context: Context) {
    actual fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
    }
}

actual fun provideClipboardManager(): ClipboardManager {
    return object : KoinComponent {
        val context: Context by inject()
    }.run {
        ClipboardManager(context)
    }
}
