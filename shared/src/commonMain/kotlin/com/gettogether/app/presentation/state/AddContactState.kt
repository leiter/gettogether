package com.gettogether.app.presentation.state

data class AddContactState(
    val contactId: String = "",
    val displayName: String = "",
    val isSearching: Boolean = false,
    val isAdding: Boolean = false,
    val searchResult: ContactSearchResult? = null,
    val error: String? = null,
    val isContactAdded: Boolean = false
) {
    val canSearch: Boolean get() = contactId.isNotBlank() && !isSearching
    val canAdd: Boolean get() = searchResult != null && !isAdding
}

data class ContactSearchResult(
    val id: String,
    val username: String,
    val displayName: String?,
    val isAlreadyContact: Boolean
)
