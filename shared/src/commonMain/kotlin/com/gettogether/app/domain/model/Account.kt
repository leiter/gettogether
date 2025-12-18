package com.gettogether.app.domain.model

data class Account(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUri: String? = null,
    val isRegistered: Boolean = false
)
