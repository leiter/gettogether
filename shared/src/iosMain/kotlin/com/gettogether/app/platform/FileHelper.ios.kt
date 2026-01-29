@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.gettogether.app.platform

import platform.Foundation.*

actual class FileHelper {

    actual suspend fun copyUriToSendableFile(uri: String, conversationId: String): String {
        // For iOS, the ImagePicker already saves to a temp file
        // Just verify it exists and return the path
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(uri)) {
            return uri
        }

        // If file:// prefix, strip it
        val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
        if (fileManager.fileExistsAtPath(path)) {
            return path
        }

        throw IllegalArgumentException("File not found: $uri")
    }

    actual fun getDownloadPath(conversationId: String, fileName: String): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String
            ?: throw IllegalStateException("Cannot find Documents directory")

        val downloadDir = "$documentsDir/downloads/$conversationId"

        // Create directory if needed
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(downloadDir)) {
            fileManager.createDirectoryAtPath(
                downloadDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        return "$downloadDir/$fileName"
    }

    actual fun fileExists(path: String): Boolean {
        val fileManager = NSFileManager.defaultManager
        val effectivePath = if (path.startsWith("file://")) path.removePrefix("file://") else path
        return fileManager.fileExistsAtPath(effectivePath)
    }

    actual fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    actual fun getConversationFilePath(accountId: String, conversationId: String, fileId: String): String? {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String ?: return null
        val fileManager = NSFileManager.defaultManager

        // Daemon stores conversation files at the parent of its config dir:
        // {Documents}/{accountId}/conversation_data/{conversationId}/{fileId}
        // (mirrors Android: context.filesDir/{accountId}/conversation_data/...)
        val daemonPath = "$documentsDir/$accountId/conversation_data/$conversationId/$fileId"
        if (fileManager.fileExistsAtPath(daemonPath)) {
            return daemonPath
        }

        // Also check inside daemon root in case layout differs
        val jamiDataPath = "$documentsDir/jami/$accountId/conversation_data/$conversationId/$fileId"
        if (fileManager.fileExistsAtPath(jamiDataPath)) {
            return jamiDataPath
        }

        // Check app downloads directory
        val downloadPath = "$documentsDir/downloads/$conversationId/$fileId"
        if (fileManager.fileExistsAtPath(downloadPath)) {
            return downloadPath
        }

        return null
    }

    actual suspend fun saveToPublicStorage(sourcePath: String, fileName: String): Result<String> {
        return try {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            val documentsDir = paths.firstOrNull() as? String
                ?: return Result.failure(Exception("Cannot find Documents directory"))

            val savedFilesDir = "$documentsDir/SavedFiles"
            val fileManager = NSFileManager.defaultManager

            // Create directory if needed
            if (!fileManager.fileExistsAtPath(savedFilesDir)) {
                fileManager.createDirectoryAtPath(
                    savedFilesDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null
                )
            }

            val destPath = "$savedFilesDir/$fileName"
            val effectiveSource = if (sourcePath.startsWith("file://"))
                sourcePath.removePrefix("file://") else sourcePath

            // Copy file
            val success = fileManager.copyItemAtPath(effectiveSource, toPath = destPath, error = null)

            if (success) {
                Result.success(destPath)
            } else {
                Result.failure(Exception("Failed to copy file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
