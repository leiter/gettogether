package com.gettogether.app.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BanErrorDetector utility.
 */
class BanErrorDetectorTest {

    @Test
    fun `detects ban keyword in error message`() {
        val exception = Exception("Contact is banned from this conversation")
        assertTrue(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `detects blocked keyword in error message`() {
        val exception = Exception("User is blocked")
        assertTrue(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `detects not allowed keyword in error message`() {
        val exception = Exception("Operation not allowed for this contact")
        assertTrue(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `detects forbidden keyword in error message`() {
        val exception = Exception("Forbidden: cannot send to banned contact")
        assertTrue(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `detects permission denied keyword in error message`() {
        val exception = Exception("Permission denied: contact is blocked")
        assertTrue(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `detection is case insensitive`() {
        val upperCase = Exception("CONTACT IS BANNED")
        val mixedCase = Exception("Contact is Blocked")
        val lowercase = Exception("operation not allowed")

        assertTrue(BanErrorDetector.isBanRelatedError(upperCase))
        assertTrue(BanErrorDetector.isBanRelatedError(mixedCase))
        assertTrue(BanErrorDetector.isBanRelatedError(lowercase))
    }

    @Test
    fun `returns false for non-ban errors`() {
        val networkError = Exception("Network connection failed")
        val timeoutError = Exception("Request timed out")
        val genericError = Exception("Unknown error occurred")
        val nullMessage = Exception()

        assertFalse(BanErrorDetector.isBanRelatedError(networkError))
        assertFalse(BanErrorDetector.isBanRelatedError(timeoutError))
        assertFalse(BanErrorDetector.isBanRelatedError(genericError))
        assertFalse(BanErrorDetector.isBanRelatedError(nullMessage))
    }

    @Test
    fun `returns false for null message`() {
        val exception = object : Exception() {
            override val message: String? = null
        }
        assertFalse(BanErrorDetector.isBanRelatedError(exception))
    }

    @Test
    fun `isBanRelatedMessage detects ban in string`() {
        assertTrue(BanErrorDetector.isBanRelatedMessage("User banned"))
        assertTrue(BanErrorDetector.isBanRelatedMessage("Request blocked"))
        assertTrue(BanErrorDetector.isBanRelatedMessage("Not allowed"))
    }

    @Test
    fun `isBanRelatedMessage returns false for null`() {
        assertFalse(BanErrorDetector.isBanRelatedMessage(null))
    }

    @Test
    fun `isBanRelatedMessage returns false for empty string`() {
        assertFalse(BanErrorDetector.isBanRelatedMessage(""))
    }

    @Test
    fun `isBanRelatedMessage returns false for non-ban message`() {
        assertFalse(BanErrorDetector.isBanRelatedMessage("Connection failed"))
        assertFalse(BanErrorDetector.isBanRelatedMessage("Invalid input"))
    }
}
