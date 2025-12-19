package com.gettogether.app.presentation.state

data class ImportAccountState(
    val importMethod: ImportMethod = ImportMethod.Archive,
    val archivePath: String = "",
    val archivePassword: String = "",
    val accountPin: String = "",
    val isImporting: Boolean = false,
    val error: String? = null,
    val isAccountImported: Boolean = false
) {
    val isValid: Boolean
        get() = when (importMethod) {
            ImportMethod.Archive -> archivePath.isNotBlank()
            ImportMethod.Pin -> accountPin.length >= 8
        }
}

enum class ImportMethod {
    Archive,
    Pin
}
