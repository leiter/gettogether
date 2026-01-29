@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.jami

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of DataPathProvider.
 * Provides the app's Documents directory for Jami daemon data storage.
 *
 * On Android, getDataPath() returns {filesDir}/jami where the daemon config dir is {filesDir}/jami
 * and profiles live at {filesDir}/{accountId}/profiles/.
 * Common code (getContactVCardPath) does: daemonPath.substringBeforeLast("/jami") to get the
 * profile root ({filesDir}).
 *
 * On iOS, the daemon config dir is {Documents}/jami, and profiles also live under
 * {Documents}/jami/{accountId}/profiles/. So we return {Documents}/jami/jami here
 * so that substringBeforeLast("/jami") yields {Documents}/jami — the correct profile root.
 * initDaemon() strips the trailing "/jami" before passing to the native daemon.
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

        // Return {Documents}/jami/jami so common code's substringBeforeLast("/jami")
        // correctly resolves to {Documents}/jami (where profiles are stored)
        return "$jamiPath/jami"
    }
}
