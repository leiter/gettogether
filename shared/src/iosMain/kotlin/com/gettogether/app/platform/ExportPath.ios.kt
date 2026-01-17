@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of ExportPathProvider.
 * Uses Documents directory for exports.
 */
actual class ExportPathProvider {
    actual fun getExportDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String ?: ""

        // Create exports subdirectory
        val exportsDir = "$documentsDir/exports"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(exportsDir)) {
            fileManager.createDirectoryAtPath(
                exportsDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        return exportsDir
    }
}

/**
 * iOS implementation of currentTimeMillis.
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
