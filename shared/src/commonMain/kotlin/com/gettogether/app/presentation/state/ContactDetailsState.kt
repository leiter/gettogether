package com.gettogether.app.presentation.state

data class ContactDetailsState(
    val contact: ContactDetails? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showRemoveDialog: Boolean = false,
    val showBlockDialog: Boolean = false,
    val isRemoving: Boolean = false,
    val isBlocking: Boolean = false,
    val contactRemoved: Boolean = false,
    val conversationStarted: String? = null
)

data class ContactDetails(
    val id: String,
    val displayName: String,
    val username: String,
    val jamiId: String,
    val isOnline: Boolean,
    val isTrusted: Boolean,
    val isBlocked: Boolean,
    val addedDate: String,
    val lastSeen: String?
)
