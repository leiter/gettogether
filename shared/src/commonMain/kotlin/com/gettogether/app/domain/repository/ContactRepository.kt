package com.gettogether.app.domain.repository

import com.gettogether.app.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getContacts(accountId: String): Flow<List<Contact>>
    fun getContactById(accountId: String, contactId: String): Flow<Contact?>

    /**
     * Get a contact synchronously from the cache.
     * Returns null if contact not found or cache not initialized.
     * Use this for quick lookups where Flow overhead is not needed (e.g., notifications).
     */
    fun getContactFromCache(accountId: String, contactId: String): Contact?

    suspend fun addContact(accountId: String, uri: String): Result<Contact>
    suspend fun removeContact(accountId: String, contactId: String): Result<Unit>
    suspend fun blockContact(accountId: String, contactId: String): Result<Unit>
    suspend fun unblockContact(accountId: String, contactId: String): Result<Unit>
    suspend fun updateCustomName(accountId: String, contactId: String, customName: String): Result<Unit>
}
