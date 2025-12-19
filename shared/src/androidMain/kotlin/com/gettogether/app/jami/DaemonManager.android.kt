package com.gettogether.app.jami

import android.content.Context
import java.io.File

/**
 * Android implementation of DataPathProvider.
 * Provides the app's internal files directory for Jami daemon data storage.
 */
actual class DataPathProvider(private val context: Context) {
    actual fun getDataPath(): String {
        val jamiDir = File(context.filesDir, "jami")
        if (!jamiDir.exists()) {
            jamiDir.mkdirs()
        }
        return jamiDir.absolutePath
    }
}
