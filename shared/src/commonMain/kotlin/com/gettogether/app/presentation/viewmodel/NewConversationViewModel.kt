package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.NewConversationState
import com.gettogether.app.presentation.state.SelectableContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewConversationViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewConversationState())
    val state: StateFlow<NewConversationState> = _state.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    val jamiContacts = jamiBridge.getContacts(accountId)
                    val contacts = jamiContacts.map { contact ->
                        SelectableContact(
                            id = contact.uri,
                            name = contact.displayName.ifEmpty { contact.uri.take(8) },
                            isOnline = contact.isConfirmed // Use confirmed status as online indicator
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            contacts = contacts,
                            filteredContacts = contacts
                        )
                    }
                } else {
                    // Demo contacts fallback
                    val contacts = getDemoContacts()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            contacts = contacts,
                            filteredContacts = contacts
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to demo contacts on error
                val contacts = getDemoContacts()
                _state.update {
                    it.copy(
                        isLoading = false,
                        contacts = contacts,
                        filteredContacts = contacts,
                        error = null
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { state ->
            val filtered = if (query.isBlank()) {
                state.contacts
            } else {
                state.contacts.filter { contact ->
                    contact.name.contains(query, ignoreCase = true)
                }
            }
            state.copy(
                searchQuery = query,
                filteredContacts = updateFilteredWithSelection(filtered, state.selectedContactIds)
            )
        }
    }

    fun toggleMultiSelectMode() {
        _state.update { state ->
            if (state.isMultiSelectMode) {
                // Exiting multi-select mode - clear selections
                state.copy(
                    isMultiSelectMode = false,
                    selectedContactIds = emptySet(),
                    filteredContacts = state.filteredContacts.map { it.copy(isSelected = false) }
                )
            } else {
                state.copy(isMultiSelectMode = true)
            }
        }
    }

    fun toggleContactSelection(contactId: String) {
        _state.update { state ->
            val newSelectedIds = if (contactId in state.selectedContactIds) {
                state.selectedContactIds - contactId
            } else {
                state.selectedContactIds + contactId
            }
            state.copy(
                selectedContactIds = newSelectedIds,
                filteredContacts = updateFilteredWithSelection(state.filteredContacts, newSelectedIds)
            )
        }
    }

    fun getSelectedContactIds(): List<String> {
        return _state.value.selectedContactIds.toList()
    }

    private fun updateFilteredWithSelection(
        contacts: List<SelectableContact>,
        selectedIds: Set<String>
    ): List<SelectableContact> {
        return contacts.map { it.copy(isSelected = it.id in selectedIds) }
    }

    fun startConversation(contactId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val accountId = accountRepository.currentAccountId.value

                if (accountId != null) {
                    // Create a new conversation
                    val conversationId = jamiBridge.startConversation(accountId)
                    // Add the contact to the conversation
                    jamiBridge.addConversationMember(accountId, conversationId, contactId)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            createdConversationId = conversationId
                        )
                    }
                } else {
                    // Demo mode - use contact ID as conversation ID
                    _state.update {
                        it.copy(
                            isLoading = false,
                            createdConversationId = contactId
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to start conversation"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun resetCreatedConversation() {
        _state.update { it.copy(createdConversationId = null) }
    }

    private fun getDemoContacts(): List<SelectableContact> {
        return listOf(
            SelectableContact("1", "Alice Smith", true),
            SelectableContact("2", "Bob Johnson", false),
            SelectableContact("3", "Carol Williams", true),
            SelectableContact("4", "David Brown", false),
            SelectableContact("5", "Emma Davis", true),
            SelectableContact("6", "Frank Miller", false),
            SelectableContact("7", "Grace Wilson", true),
            SelectableContact("8", "Henry Moore", false)
        )
    }
}
