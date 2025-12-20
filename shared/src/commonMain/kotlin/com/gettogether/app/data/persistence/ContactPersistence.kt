package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Contact

/**
 * Platform-agnostic interface for persisting contacts.
 */
interface ContactPersistence {
    /**
     * Save contacts for a specific account.
     */
    suspend fun saveContacts(accountId: String, contacts: List<Contact>)

    /**
     * Load contacts for a specific account.
     */
    suspend fun loadContacts(accountId: String): List<Contact>

    /**
     * Clear all persisted contacts for a specific account.
     */
    suspend fun clearContacts(accountId: String)

    /**
     * Clear all persisted contacts.
     */
    suspend fun clearAllContacts()
}

/**
 * Platform-specific factory for creating ContactPersistence.
 */
expect fun createContactPersistence(): ContactPersistence
