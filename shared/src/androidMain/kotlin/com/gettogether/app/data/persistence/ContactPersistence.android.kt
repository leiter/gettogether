package com.gettogether.app.data.persistence

import android.content.Context
import android.content.SharedPreferences
import com.gettogether.app.domain.model.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Android implementation of ContactPersistence using SharedPreferences.
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
        val jsonString = json.encodeToString(contacts)
        prefs.edit().putString(key, jsonString).apply()
    }

    override suspend fun loadContacts(accountId: String): List<Contact> {
        val key = getContactsKey(accountId)
        val jsonString = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Contact>>(jsonString)
        } catch (e: Exception) {
            // If deserialization fails, return empty list
            emptyList()
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
