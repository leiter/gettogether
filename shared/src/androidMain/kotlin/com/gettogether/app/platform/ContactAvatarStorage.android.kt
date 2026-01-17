package com.gettogether.app.platform

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of ContactAvatarStorage.
 * Saves avatars to app's internal storage under "contact_avatars" directory.
 */
class AndroidContactAvatarStorage(private val context: Context) : ContactAvatarStorage {

    companion object {
        private const val TAG = "ContactAvatarStorage"
        private const val AVATARS_DIR = "contact_avatars"
    }

    private val avatarsDir: File by lazy {
        File(context.filesDir, AVATARS_DIR).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    override suspend fun saveContactAvatar(contactUri: String, base64Data: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Decode base64 data
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                if (imageBytes.isEmpty()) {
                    Log.w(TAG, "Decoded avatar data is empty for contact: $contactUri")
                    return@withContext null
                }

                // Generate filename from contact URI hash
                val filename = "${contactUri.hashCode()}.jpg"
                val avatarFile = File(avatarsDir, filename)

                // Write to file
                avatarFile.writeBytes(imageBytes)
                Log.i(TAG, "Saved avatar for $contactUri to ${avatarFile.absolutePath} (${imageBytes.size} bytes)")

                avatarFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save avatar for $contactUri: ${e.message}", e)
                null
            }
        }
    }

    override fun getContactAvatarPath(contactUri: String): String? {
        val filename = "${contactUri.hashCode()}.jpg"
        val avatarFile = File(avatarsDir, filename)
        return if (avatarFile.exists()) avatarFile.absolutePath else null
    }

    override suspend fun deleteContactAvatar(contactUri: String) {
        withContext(Dispatchers.IO) {
            try {
                val filename = "${contactUri.hashCode()}.jpg"
                val avatarFile = File(avatarsDir, filename)
                if (avatarFile.exists()) {
                    avatarFile.delete()
                    Log.i(TAG, "Deleted avatar for $contactUri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete avatar for $contactUri: ${e.message}", e)
            }
        }
    }
}

// Instance holder for dependency injection
private var contactAvatarStorageInstance: ContactAvatarStorage? = null

/**
 * Set the ContactAvatarStorage instance (called from Koin module).
 */
fun setContactAvatarStorage(storage: ContactAvatarStorage) {
    contactAvatarStorageInstance = storage
}

actual fun createContactAvatarStorage(): ContactAvatarStorage {
    return contactAvatarStorageInstance
        ?: throw IllegalStateException("ContactAvatarStorage not initialized. Call setContactAvatarStorage() first.")
}
