package com.gettogether.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String,
    val uri: String,
    val displayName: String,
    val customName: String? = null,  // User-defined custom name (takes priority over displayName)
    val avatarUri: String? = null,
    val isOnline: Boolean = false,
    val isBanned: Boolean = false,
    val profileVersion: Long = 0  // Incremented on profile updates to force StateFlow emission
) {
    /**
     * Returns the effective display name for this contact.
     * Priority: customName > displayName
     */
    fun getEffectiveName(): String {
        return customName?.takeIf { it.isNotBlank() } ?: displayName
    }
}
