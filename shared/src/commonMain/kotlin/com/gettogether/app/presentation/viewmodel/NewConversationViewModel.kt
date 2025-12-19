package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.NewConversationState
import com.gettogether.app.presentation.state.SelectableContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewConversationViewModel(
    private val jamiBridge: JamiBridge
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
                // For now, load demo contacts
                val contacts = getDemoContacts()
                _state.update {
                    it.copy(
                        isLoading = false,
                        contacts = contacts,
                        filteredContacts = contacts
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load contacts"
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
                filteredContacts = filtered
            )
        }
    }

    fun startConversation(contactId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                // TODO: Actually create conversation via JamiBridge
                // val conversationId = jamiBridge.createConversation(contactId)

                // For now, use the contact ID as conversation ID
                _state.update {
                    it.copy(
                        isLoading = false,
                        createdConversationId = contactId
                    )
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
