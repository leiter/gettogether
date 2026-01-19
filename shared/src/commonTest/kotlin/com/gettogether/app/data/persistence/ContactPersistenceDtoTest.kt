package com.gettogether.app.data.persistence

import com.gettogether.app.domain.model.Contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for ContactPersistenceDto and its conversion functions.
 *
 * These tests verify that volatile fields (isBanned, isOnline) are properly
 * excluded when converting to DTO and set to safe defaults when converting back.
 */
class ContactPersistenceDtoTest {

    @Test
    fun `toPersistenceDto excludes volatile fields`() {
        // Given a contact with isBanned=true and isOnline=true
        val contact = Contact(
            id = "contact-123",
            uri = "jami:abc123def456",
            displayName = "Alice",
            customName = "My Friend Alice",
            avatarUri = "/path/to/avatar.png",
            isOnline = true,
            isBanned = true
        )

        // When converting to DTO
        val dto = contact.toPersistenceDto()

        // Then volatile fields should not be in DTO
        assertEquals("contact-123", dto.id)
        assertEquals("jami:abc123def456", dto.uri)
        assertEquals("Alice", dto.displayName)
        assertEquals("My Friend Alice", dto.customName)
        assertEquals("/path/to/avatar.png", dto.avatarUri)
        // isBanned and isOnline are not in DTO - this is verified by the data class definition
    }

    @Test
    fun `toContact sets volatile fields to safe defaults`() {
        // Given a DTO
        val dto = ContactPersistenceDto(
            id = "contact-456",
            uri = "jami:xyz789abc012",
            displayName = "Bob",
            customName = null,
            avatarUri = null
        )

        // When converting to Contact
        val contact = dto.toContact()

        // Then volatile fields should have safe defaults
        assertEquals("contact-456", contact.id)
        assertEquals("jami:xyz789abc012", contact.uri)
        assertEquals("Bob", contact.displayName)
        assertNull(contact.customName)
        assertNull(contact.avatarUri)
        assertFalse(contact.isOnline, "isOnline should default to false")
        assertFalse(contact.isBanned, "isBanned should default to false")
    }

    @Test
    fun `round-trip conversion loses volatile state`() {
        // Given a contact with volatile state set
        val originalContact = Contact(
            id = "contact-789",
            uri = "jami:roundtrip123",
            displayName = "Charlie",
            customName = "Charlie Custom",
            avatarUri = "/avatar/charlie.jpg",
            isOnline = true,  // volatile - should be lost
            isBanned = true   // volatile - should be lost
        )

        // When round-tripping through DTO
        val dto = originalContact.toPersistenceDto()
        val restoredContact = dto.toContact()

        // Then non-volatile fields should be preserved
        assertEquals(originalContact.id, restoredContact.id)
        assertEquals(originalContact.uri, restoredContact.uri)
        assertEquals(originalContact.displayName, restoredContact.displayName)
        assertEquals(originalContact.customName, restoredContact.customName)
        assertEquals(originalContact.avatarUri, restoredContact.avatarUri)

        // And volatile fields should be reset to defaults
        assertFalse(restoredContact.isOnline, "isOnline should be lost in round-trip")
        assertFalse(restoredContact.isBanned, "isBanned should be lost in round-trip")
    }

    @Test
    fun `list conversion works correctly`() {
        // Given a list of contacts with various states
        val contacts = listOf(
            Contact(
                id = "c1",
                uri = "uri1",
                displayName = "Contact 1",
                isOnline = true,
                isBanned = false
            ),
            Contact(
                id = "c2",
                uri = "uri2",
                displayName = "Contact 2",
                customName = "Custom 2",
                isOnline = false,
                isBanned = true  // This ban status should be lost
            ),
            Contact(
                id = "c3",
                uri = "uri3",
                displayName = "Contact 3",
                avatarUri = "/avatar3.png",
                isOnline = true,
                isBanned = true  // This ban status should be lost
            )
        )

        // When converting list to DTOs and back
        val dtos = contacts.toPersistenceDtos()
        val restoredContacts = dtos.toContacts()

        // Then all contacts should be restored with default volatile values
        assertEquals(3, restoredContacts.size)

        restoredContacts.forEachIndexed { index, contact ->
            assertEquals(contacts[index].id, contact.id)
            assertEquals(contacts[index].uri, contact.uri)
            assertEquals(contacts[index].displayName, contact.displayName)
            assertEquals(contacts[index].customName, contact.customName)
            assertEquals(contacts[index].avatarUri, contact.avatarUri)
            assertFalse(contact.isOnline, "Contact $index: isOnline should be false")
            assertFalse(contact.isBanned, "Contact $index: isBanned should be false")
        }
    }

    @Test
    fun `empty list conversion`() {
        val emptyContacts = emptyList<Contact>()
        val dtos = emptyContacts.toPersistenceDtos()
        val restored = dtos.toContacts()

        assertEquals(0, dtos.size)
        assertEquals(0, restored.size)
    }
}
