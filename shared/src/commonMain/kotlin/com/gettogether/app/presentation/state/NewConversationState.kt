package com.gettogether.app.presentation.state

data class NewConversationState(
    val searchQuery: String = "",
    val contacts: List<SelectableContact> = emptyList(),
    val filteredContacts: List<SelectableContact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdConversationId: String? = null
)

data class SelectableContact(
    val id: String,
    val name: String,
    val isOnline: Boolean
)
