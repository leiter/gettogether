package com.gettogether.app.platform

import android.content.Context
import java.io.File

/**
 * Android implementation of ExportPathProvider.
 * Uses app's internal storage for exports (accessible via file manager apps).
 */
actual class ExportPathProvider(private val context: Context) {
    actual fun getExportDirectory(): String {
        val exportsDir = File(context.filesDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        return exportsDir.absolutePath
    }
}

/**
 * Android implementation of currentTimeMillis.
 */
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
