package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of ContactPersistence using UserDefaults.
 *
 * Uses ContactPersistenceDto internally to avoid persisting volatile fields
 * like isBanned and isOnline, which should be fetched fresh from the daemon.
 */
class IosContactPersistence : ContactPersistence {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override suspend fun saveContacts(accountId: String, contacts: List<Contact>) {
        val key = getContactsKey(accountId)
        // Convert to DTOs to exclude volatile fields (isBanned, isOnline)
        val dtos = contacts.toPersistenceDtos()
        val jsonString = json.encodeToString(dtos)
        userDefaults.setObject(jsonString, forKey = key)
        userDefaults.synchronize()
    }

    override suspend fun loadContacts(accountId: String): List<Contact> {
        val key = getContactsKey(accountId)
        val jsonString = userDefaults.stringForKey(key) ?: return emptyList()
        return try {
            // Try to deserialize as DTOs first (new format)
            val dtos = json.decodeFromString<List<ContactPersistenceDto>>(jsonString)
            dtos.toContacts()
        } catch (e: Exception) {
            // Fall back to old Contact format for migration
            try {
                val contacts = json.decodeFromString<List<Contact>>(jsonString)
                // Re-save in new DTO format for future loads
                saveContacts(accountId, contacts)
                // Return with volatile fields reset to defaults
                contacts.map { it.copy(isBanned = false, isOnline = false) }
            } catch (e2: Exception) {
                // If both fail, return empty list
                emptyList()
            }
        }
    }

    override suspend fun clearContacts(accountId: String) {
        val key = getContactsKey(accountId)
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
    }

    override suspend fun clearAllContacts() {
        val dictionary = userDefaults.dictionaryRepresentation()
        dictionary.keys.forEach { key ->
            val keyString = key as? String
            if (keyString?.startsWith(KEY_PREFIX) == true) {
                userDefaults.removeObjectForKey(keyString)
            }
        }
        userDefaults.synchronize()
    }

    private fun getContactsKey(accountId: String): String {
        return "${KEY_PREFIX}$accountId"
    }

    companion object {
        private const val KEY_PREFIX = "contacts_"
    }
}

// Platform-specific instance holder for expect/actual
private var contactPersistenceInstance: ContactPersistence? = null

/**
 * Sets the contact persistence instance (called from Koin module).
 */
fun setContactPersistence(persistence: ContactPersistence) {
    contactPersistenceInstance = persistence
}

actual fun createContactPersistence(): ContactPersistence {
    return contactPersistenceInstance
        ?: throw IllegalStateException("ContactPersistence not initialized. Call setContactPersistence() first.")
}
