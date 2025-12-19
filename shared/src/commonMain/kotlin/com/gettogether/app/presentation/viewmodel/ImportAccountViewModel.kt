package com.gettogether.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gettogether.app.jami.JamiBridge
import com.gettogether.app.presentation.state.ImportAccountState
import com.gettogether.app.presentation.state.ImportMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImportAccountViewModel(
    private val jamiBridge: JamiBridge
) : ViewModel() {

    private val _state = MutableStateFlow(ImportAccountState())
    val state: StateFlow<ImportAccountState> = _state.asStateFlow()

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

    fun importAccount() {
        val currentState = _state.value
        if (!currentState.isValid) {
            val errorMessage = when (currentState.importMethod) {
                ImportMethod.Archive -> "Please provide a valid archive path"
                ImportMethod.Pin -> "PIN must be at least 8 characters"
            }
            _state.update { it.copy(error = errorMessage) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            try {
                when (currentState.importMethod) {
                    ImportMethod.Archive -> {
                        // TODO: Actually import via JamiBridge
                        // jamiBridge.importAccountFromArchive(
                        //     currentState.archivePath,
                        //     currentState.archivePassword
                        // )
                        kotlinx.coroutines.delay(1500) // Simulate import
                    }
                    ImportMethod.Pin -> {
                        // TODO: Actually import via JamiBridge
                        // jamiBridge.importAccountFromPin(currentState.accountPin)
                        kotlinx.coroutines.delay(1500) // Simulate import
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
