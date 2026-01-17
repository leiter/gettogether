package com.gettogether.app.platform

/**
 * Platform-specific storage for contact avatars.
 * Handles decoding base64 and saving avatar images to app storage.
 */
interface ContactAvatarStorage {
    /**
     * Save a contact's avatar from base64-encoded image data.
     * @param contactUri The contact's URI (used for filename)
     * @param base64Data The base64-encoded image data
     * @return The file path where the avatar was saved, or null if save failed
     */
    suspend fun saveContactAvatar(contactUri: String, base64Data: String): String?

    /**
     * Get the file path for a contact's avatar, if it exists.
     * @param contactUri The contact's URI
     * @return The file path or null if no avatar is saved
     */
    fun getContactAvatarPath(contactUri: String): String?

    /**
     * Delete a contact's avatar.
     * @param contactUri The contact's URI
     */
    suspend fun deleteContactAvatar(contactUri: String)
}

/**
 * Factory function to create platform-specific ContactAvatarStorage.
 */
expect fun createContactAvatarStorage(): ContactAvatarStorage
