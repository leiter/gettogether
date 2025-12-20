package com.gettogether.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String,
    val uri: String,
    val displayName: String,
    val avatarUri: String? = null,
    val isOnline: Boolean = false,
    val isBanned: Boolean = false
)
