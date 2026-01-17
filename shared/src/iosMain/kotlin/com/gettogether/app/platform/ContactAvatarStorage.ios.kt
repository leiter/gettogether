package com.gettogether.app.platform

import platform.Foundation.NSLog

/**
 * iOS implementation of ContactAvatarStorage.
 * TODO: Implement actual iOS file storage
 */
class IosContactAvatarStorage : ContactAvatarStorage {

    override suspend fun saveContactAvatar(contactUri: String, base64Data: String): String? {
        NSLog("IosContactAvatarStorage: saveContactAvatar not yet implemented for $contactUri")
        // TODO: Implement iOS file storage
        return null
    }

    override fun getContactAvatarPath(contactUri: String): String? {
        // TODO: Implement iOS file storage
        return null
    }

    override suspend fun deleteContactAvatar(contactUri: String) {
        NSLog("IosContactAvatarStorage: deleteContactAvatar not yet implemented for $contactUri")
        // TODO: Implement iOS file storage
    }
}

actual fun createContactAvatarStorage(): ContactAvatarStorage {
    return IosContactAvatarStorage()
}
