package com.gettogether.app.presentation.state

data class ContactDetailsState(
    val contact: ContactDetails? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showRemoveDialog: Boolean = false,
    val showBlockDialog: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val isRemoving: Boolean = false,
    val isBlocking: Boolean = false,
    val contactRemoved: Boolean = false,
    val conversationStarted: String? = null,
    val activeCallInfo: ActiveCallInfo? = null  // Info about active call with this contact
)

/**
 * Information about an active call with a contact
 */
data class ActiveCallInfo(
    val callId: String,
    val isVideo: Boolean,
    val isWithCurrentContact: Boolean = true  // false if call is with a different contact
)

data class ContactDetails(
    val id: String,
    val displayName: String,
    val customName: String? = null,  // User-defined custom name
    val avatarUri: String? = null,
    val username: String,
    val jamiId: String,
    val isOnline: Boolean,
    val isTrusted: Boolean,
    val isBlocked: Boolean,
    val addedDate: String,
    val lastSeen: String?
) {
    /**
     * Returns the effective display name for this contact.
     * Priority: customName > displayName
     */
    fun getEffectiveName(): String {
        return customName?.takeIf { it.isNotBlank() } ?: displayName
    }
}
