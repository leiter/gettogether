package com.gettogether.app.presentation.state

import com.gettogether.app.domain.model.ExistingAccount

data class ImportAccountState(
    val importMethod: ImportMethod = ImportMethod.Archive,
    val archivePath: String = "",
    val archivePassword: String = "",
    val accountPin: String = "",
    val isImporting: Boolean = false,
    val error: String? = null,
    val isAccountImported: Boolean = false,
    // Local account relogin fields
    val deactivatedAccounts: List<ExistingAccount> = emptyList(),
    val selectedLocalAccountId: String? = null,
    val isLoadingLocalAccounts: Boolean = false
) {
    val isValid: Boolean
        get() = when (importMethod) {
            ImportMethod.Archive -> archivePath.isNotBlank()
            ImportMethod.Pin -> accountPin.length >= 8
            ImportMethod.LocalAccount -> selectedLocalAccountId != null
        }

    val hasDeactivatedAccounts: Boolean
        get() = deactivatedAccounts.isNotEmpty()
}

enum class ImportMethod {
    Archive,
    Pin,
    LocalAccount
}
