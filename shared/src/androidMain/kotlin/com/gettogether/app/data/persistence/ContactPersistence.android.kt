package com.gettogether.app.data.persistence

import android.content.Context
import android.content.SharedPreferences
import com.gettogether.app.domain.model.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android implementation of ContactPersistence using SharedPreferences.
 *
 * Uses ContactPersistenceDto internally to avoid persisting volatile fields
 * like isBanned and isOnline, which should be fetched fresh from the daemon.
 */
class AndroidContactPersistence(context: Context) : ContactPersistence {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override suspend fun saveContacts(accountId: String, contacts: List<Contact>) {
        val key = getContactsKey(accountId)
        // Convert to DTOs to exclude volatile fields (isBanned, isOnline)
        val dtos = contacts.toPersistenceDtos()
        println("[CONTACT-PERSIST] Saving ${dtos.size} contacts for account: $accountId")
        dtos.forEach { d ->
            println("[CONTACT-PERSIST]   - ${d.displayName}: avatarUri=${d.avatarUri?.take(50) ?: "null"}")
        }
        val jsonString = json.encodeToString(dtos)
        prefs.edit().putString(key, jsonString).apply()
        println("[CONTACT-PERSIST] ✓ Saved, JSON length: ${jsonString.length}")
    }

    override suspend fun loadContacts(accountId: String): List<Contact> {
        val key = getContactsKey(accountId)
        val jsonString = prefs.getString(key, null)
        if (jsonString == null) {
            println("[CONTACT-PERSIST] No persisted contacts found for account: $accountId")
            return emptyList()
        }
        println("[CONTACT-PERSIST] Loading contacts, JSON length: ${jsonString.length}")
        return try {
            // Try to deserialize as DTOs first (new format)
            val dtos = json.decodeFromString<List<ContactPersistenceDto>>(jsonString)
            val contacts = dtos.toContacts()
            println("[CONTACT-PERSIST] ✓ Loaded ${contacts.size} contacts as DTOs")
            contacts.forEach { c ->
                println("[CONTACT-PERSIST]   - ${c.displayName}: avatarUri=${c.avatarUri?.take(50) ?: "null"}")
            }
            contacts
        } catch (e: Exception) {
            println("[CONTACT-PERSIST] DTO deserialization failed: ${e.message}, trying old format...")
            // Fall back to old Contact format for migration
            try {
                val contacts = json.decodeFromString<List<Contact>>(jsonString)
                println("[CONTACT-PERSIST] ✓ Loaded ${contacts.size} contacts as old Contact format, migrating...")
                // Re-save in new DTO format for future loads
                saveContacts(accountId, contacts)
                // Return with volatile fields reset to defaults
                val result = contacts.map { it.copy(isBanned = false, isOnline = false) }
                result.forEach { c ->
                    println("[CONTACT-PERSIST]   - ${c.displayName}: avatarUri=${c.avatarUri?.take(50) ?: "null"}")
                }
                result
            } catch (e2: Exception) {
                println("[CONTACT-PERSIST] ✗ Both formats failed: ${e2.message}")
                // If both fail, return empty list
                emptyList()
            }
        }
    }

    override suspend fun clearContacts(accountId: String) {
        val key = getContactsKey(accountId)
        prefs.edit().remove(key).apply()
    }

    override suspend fun clearAllContacts() {
        prefs.edit().apply {
            prefs.all.keys.forEach { key ->
                if (key.startsWith(KEY_PREFIX)) {
                    remove(key)
                }
            }
            apply()
        }
    }

    private fun getContactsKey(accountId: String): String {
        return "${KEY_PREFIX}$accountId"
    }

    companion object {
        private const val PREFS_NAME = "gettogether_contacts"
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
