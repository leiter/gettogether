package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Contact
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for persisting contacts.
 *
 * This DTO excludes volatile fields like `isBanned` and `isOnline` that should
 * be fetched fresh from the daemon on load, not persisted. This prevents stale
 * state issues where a contact's ban status gets "stuck" after app restart.
 *
 * See: Issues #1098, #1081, #1071 for context on ban-related persistence bugs.
 */
@Serializable
data class ContactPersistenceDto(
    val id: String,
    val uri: String,
    val displayName: String,
    val customName: String? = null,
    val avatarUri: String? = null
    // isBanned excluded - fetched from daemon on load
    // isOnline excluded - always fetched fresh
)

/**
 * Converts a Contact to a persistence DTO, excluding volatile fields.
 */
fun Contact.toPersistenceDto(): ContactPersistenceDto {
    return ContactPersistenceDto(
        id = id,
        uri = uri,
        displayName = displayName,
        customName = customName,
        avatarUri = avatarUri
    )
}

/**
 * Converts a persistence DTO back to a Contact with default volatile field values.
 *
 * Note: `isBanned` defaults to false. The caller should query the daemon for
 * the current ban status and update this value accordingly.
 */
fun ContactPersistenceDto.toContact(): Contact {
    return Contact(
        id = id,
        uri = uri,
        displayName = displayName,
        customName = customName,
        avatarUri = avatarUri,
        isOnline = false,  // Always starts offline, presence updates will correct this
        isBanned = false   // Default to not banned, daemon will provide current status
    )
}

/**
 * Extension to convert a list of Contacts to persistence DTOs.
 */
fun List<Contact>.toPersistenceDtos(): List<ContactPersistenceDto> {
    return map { it.toPersistenceDto() }
}

/**
 * Extension to convert a list of persistence DTOs to Contacts.
 */
fun List<ContactPersistenceDto>.toContacts(): List<Contact> {
    return map { it.toContact() }
}
