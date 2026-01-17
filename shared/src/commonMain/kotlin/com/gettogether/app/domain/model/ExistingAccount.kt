package com.gettogether.app.domain.model

/**
 * Represents an existing account on the device that may be active or deactivated.
 * Used for the relogin feature to show accounts that can be logged back into.
 */
data class ExistingAccount(
    val accountId: String,
    val displayName: String,
    val username: String,
    val jamiId: String,
    val isActive: Boolean
)
