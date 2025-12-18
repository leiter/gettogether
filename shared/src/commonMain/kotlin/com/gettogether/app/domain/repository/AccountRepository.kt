package com.gettogether.app.domain.repository

import com.gettogether.app.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccounts(): Flow<List<Account>>
    fun getAccountById(id: String): Flow<Account?>
    suspend fun createAccount(displayName: String): Result<Account>
    suspend fun importAccount(archivePath: String, password: String): Result<Account>
    suspend fun exportAccount(accountId: String, password: String): Result<String>
    suspend fun updateProfile(accountId: String, displayName: String, avatarUri: String?): Result<Unit>
    suspend fun deleteAccount(accountId: String): Result<Unit>
}
