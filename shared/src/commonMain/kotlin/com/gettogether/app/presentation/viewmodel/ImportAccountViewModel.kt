package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.data.repository.AccountRepository
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.ImportAccountState
import com.gettogether.app.presentation.state.ImportMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImportAccountViewModel(
    private val jamiBridge: JamiBridge,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ImportAccountState())
    val state: StateFlow<ImportAccountState> = _state.asStateFlow()

    init {
        loadDeactivatedAccounts()
    }

    /**
     * Load deactivated accounts that can be relogged into.
     */
    private fun loadDeactivatedAccounts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingLocalAccounts = true) }
            try {
                val accounts = accountRepository.getDeactivatedAccounts()
                _state.update {
                    it.copy(
                        isLoadingLocalAccounts = false,
                        deactivatedAccounts = accounts
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingLocalAccounts = false,
                        error = e.message ?: "Failed to load local accounts"
                    )
                }
            }
        }
    }

    fun onImportMethodChanged(method: ImportMethod) {
        _state.update { it.copy(importMethod = method, error = null) }
    }

    fun onArchivePathChanged(path: String) {
        _state.update { it.copy(archivePath = path, error = null) }
    }

    fun onArchivePasswordChanged(password: String) {
        _state.update { it.copy(archivePassword = password, error = null) }
    }

    fun onAccountPinChanged(pin: String) {
        _state.update { it.copy(accountPin = pin, error = null) }
    }

    /**
     * Select a local deactivated account for relogin.
     */
    fun onLocalAccountSelected(accountId: String) {
        _state.update { it.copy(selectedLocalAccountId = accountId, error = null) }
    }

    fun importAccount() {
        val currentState = _state.value
        if (!currentState.isValid) {
            val errorMessage = when (currentState.importMethod) {
                ImportMethod.Archive -> "Please provide a valid archive path"
                ImportMethod.Pin -> "PIN must be at least 8 characters"
                ImportMethod.LocalAccount -> "Please select an account to relogin"
            }
            _state.update { it.copy(error = errorMessage) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            try {
                when (currentState.importMethod) {
                    ImportMethod.Archive -> {
                        accountRepository.importAccount(
                            currentState.archivePath,
                            currentState.archivePassword
                        )
                    }
                    ImportMethod.Pin -> {
                        // PIN-based import - PIN is used as password with empty archive path
                        // The Jami daemon treats this as a DHT import
                        accountRepository.importAccount("", currentState.accountPin)
                    }
                    ImportMethod.LocalAccount -> {
                        // Relogin to existing local account
                        val accountId = currentState.selectedLocalAccountId!!
                        accountRepository.reloginToAccount(accountId)
                    }
                }
                _state.update { it.copy(isImporting = false, isAccountImported = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        error = e.message ?: "Failed to import account"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
