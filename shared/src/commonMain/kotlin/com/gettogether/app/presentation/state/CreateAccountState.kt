package com.gettogether.app.presentation.state

data class CreateAccountState(
    val displayName: String = "",
    val isCreating: Boolean = false,
    val error: String? = null,
    val isAccountCreated: Boolean = false
) {
    val isValid: Boolean
        get() = displayName.isNotBlank() && displayName.length >= 2
}
