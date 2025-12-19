package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.CreateAccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateAccountViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountState())
    val state: StateFlow<CreateAccountState> = _state.asStateFlow()

    fun onDisplayNameChanged(name: String) {
        _state.update { it.copy(displayName = name, error = null) }
    }

    fun createAccount() {
        val currentState = _state.value
        if (!currentState.isValid) {
            _state.update { it.copy(error = "Display name must be at least 2 characters") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                accountRepository.createAccount(currentState.displayName)
                _state.update { it.copy(isCreating = false, isAccountCreated = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isCreating = false,
                        error = e.message ?: "Failed to create account"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
