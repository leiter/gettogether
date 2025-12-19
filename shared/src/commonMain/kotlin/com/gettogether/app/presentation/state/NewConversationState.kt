package com.gettogether.app.presentation.state

data class NewConversationState(
    val searchQuery: String = "",
    val contacts: List<SelectableContact> = emptyList(),
    val filteredContacts: List<SelectableContact> = emptyList(),
    val selectedContactIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val createdConversationId: String? = null
) {
    val selectedContacts: List<SelectableContact>
        get() = contacts.filter { it.id in selectedContactIds }

    val canStartGroupCall: Boolean
        get() = selectedContactIds.size >= 2
}

data class SelectableContact(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val isSelected: Boolean = false
)
