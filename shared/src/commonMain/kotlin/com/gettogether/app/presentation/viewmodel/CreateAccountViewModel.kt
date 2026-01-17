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

    init {
        println("CreateAccountViewModel: initialized")
    }

    private val _state = MutableStateFlow(CreateAccountState())
    val state: StateFlow<CreateAccountState> = _state.asStateFlow()

    fun onDisplayNameChanged(name: String) {
        _state.update { it.copy(displayName = name, error = null) }
    }

    fun createAccount() {
        println("[ACCOUNT-CREATE] CreateAccountViewModel.createAccount() called")
        val currentState = _state.value
        println("[ACCOUNT-CREATE] Current state: displayName='${currentState.displayName}', isValid=${currentState.isValid}")

        if (!currentState.isValid) {
            println("[ACCOUNT-CREATE] Validation failed: displayName too short (min 2 chars)")
            _state.update { it.copy(error = "Display name must be at least 2 characters") }
            return
        }

        println("[ACCOUNT-CREATE] Validation passed, starting account creation...")
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            println("[ACCOUNT-CREATE] State updated to isCreating=true")

            try {
                println("[ACCOUNT-CREATE] Calling accountRepository.createAccount('${currentState.displayName}')...")
                accountRepository.createAccount(currentState.displayName)
                println("[ACCOUNT-CREATE] ✓ Account creation succeeded")
                _state.update { it.copy(isCreating = false, isAccountCreated = true) }
                println("[ACCOUNT-CREATE] State updated: isCreating=false, isAccountCreated=true")
            } catch (e: Exception) {
                println("[ACCOUNT-CREATE] ✗ Account creation FAILED: ${e.message}")
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isCreating = false,
                        error = e.message ?: "Failed to create account"
                    )
                }
                println("[ACCOUNT-CREATE] State updated with error: ${e.message}")
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
