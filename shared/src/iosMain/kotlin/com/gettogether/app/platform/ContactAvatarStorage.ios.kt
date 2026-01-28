@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.gettogether.app.platform

import platform.Foundation.*

/**
 * iOS implementation of ContactAvatarStorage.
 * Saves avatars to app's Documents directory under "contact_avatars" directory.
 */
class IosContactAvatarStorage : ContactAvatarStorage {

    companion object {
        private const val TAG = "ContactAvatarStorage"
        private const val AVATARS_DIR = "contact_avatars"
    }

    private val avatarsDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDir = paths.firstOrNull() as? String ?: ""
        val dir = "$documentsDir/$AVATARS_DIR"

        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        dir
    }

    override suspend fun saveContactAvatar(contactUri: String, base64Data: String): String? {
        return try {
            // Decode base64 data
            val nsData = NSData.create(
                base64EncodedString = base64Data,
                options = NSDataBase64DecodingIgnoreUnknownCharacters
            )

            if (nsData == null || nsData.length.toInt() == 0) {
                NSLog("$TAG: Decoded avatar data is empty for contact: $contactUri")
                return null
            }

            // Generate filename from contact URI hash
            val filename = "${contactUri.hashCode()}.jpg"
            val avatarPath = "$avatarsDir/$filename"

            // Write to file
            val success = nsData.writeToFile(avatarPath, atomically = true)
            if (success) {
                NSLog("$TAG: Saved avatar for $contactUri to $avatarPath (${nsData.length} bytes)")
                avatarPath
            } else {
                NSLog("$TAG: Failed to write avatar file for $contactUri")
                null
            }
        } catch (e: Exception) {
            NSLog("$TAG: Failed to save avatar for $contactUri: ${e.message}")
            null
        }
    }

    override fun getContactAvatarPath(contactUri: String): String? {
        val filename = "${contactUri.hashCode()}.jpg"
        val avatarPath = "$avatarsDir/$filename"
        return if (NSFileManager.defaultManager.fileExistsAtPath(avatarPath)) avatarPath else null
    }

    override suspend fun deleteContactAvatar(contactUri: String) {
        try {
            val filename = "${contactUri.hashCode()}.jpg"
            val avatarPath = "$avatarsDir/$filename"
            val fileManager = NSFileManager.defaultManager
            if (fileManager.fileExistsAtPath(avatarPath)) {
                fileManager.removeItemAtPath(avatarPath, error = null)
                NSLog("$TAG: Deleted avatar for $contactUri")
            }
        } catch (e: Exception) {
            NSLog("$TAG: Failed to delete avatar for $contactUri: ${e.message}")
        }
    }
}

actual fun createContactAvatarStorage(): ContactAvatarStorage {
    return IosContactAvatarStorage()
}
