package com.gettogether.app.jami

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of DataPathProvider.
 * Provides the app's Documents directory for Jami daemon data storage.
 */
actual class DataPathProvider {
    actual fun getDataPath(): String {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsUrl = urls.firstOrNull() as? NSURL
            ?: throw IllegalStateException("Could not find Documents directory")

        val jamiUrl = documentsUrl.URLByAppendingPathComponent("jami")
            ?: throw IllegalStateException("Could not create jami directory URL")

        val jamiPath = jamiUrl.path
            ?: throw IllegalStateException("Could not get jami directory path")

        // Create directory if it doesn't exist
        if (!fileManager.fileExistsAtPath(jamiPath)) {
            fileManager.createDirectoryAtPath(
                jamiPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        return jamiPath
    }
}
