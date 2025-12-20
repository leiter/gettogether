package com.gettogether.app.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gettogether.app.jami.SwigJamiBridge
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for JamiBridge call operations.
 * These tests verify that the Kotlin bridge can properly handle voice and video calls,
 * including call control (hold, mute) and conference calls through the native Jami daemon.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeCallOperationsTest {

    private lateinit var context: Context
    private lateinit var bridge: SwigJamiBridge
    private lateinit var testDataPath: String
    private val createdAccounts = mutableListOf<String>()

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        bridge = SwigJamiBridge(context)

        // Create isolated test data directory
        testDataPath = File(context.cacheDir, "jami-test-${System.currentTimeMillis()}").absolutePath
        File(testDataPath).mkdirs()

        // Initialize and start daemon
        bridge.initDaemon(testDataPath)
        bridge.startDaemon()
    }

    @After
    fun tearDown() = runTest {
        try {
            // Clean up: delete all created accounts
            createdAccounts.forEach { accountId ->
                try {
                    bridge.deleteAccount(accountId)
                } catch (e: Exception) {
                    // Ignore errors during cleanup
                }
            }
            createdAccounts.clear()

            // Clean up test data directory
            File(testDataPath).deleteRecursively()

            // Note: We don't call stopDaemon() - see JamiBridgeDaemonLifecycleTest for explanation
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // ==================== Basic Call Operations ====================

    @Test
    fun testPlaceCall() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Call Test")
        createdAccounts.add(accountId)

        // When: Place a call to a test URI
        val uri = "ring:1234567890abcdef1234567890abcdef12345678"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // Then: Call ID is returned (may be empty depending on daemon state)
        assertThat(callId).isNotNull()
    }

    @Test
    fun testPlaceVideoCall() = runTest {
        // Given: Created account
        val accountId = bridge.createAccount(displayName = "Video Call Test")
        createdAccounts.add(accountId)

        // When: Place a video call
        val uri = "ring:abcdef1234567890abcdef1234567890abcdef12"
        val callId = bridge.placeCall(accountId, uri, withVideo = true)

        // Then: Call ID is returned
        assertThat(callId).isNotNull()
    }

    @Test
    fun testAcceptCall() = runTest {
        // Given: Account with a placed call
        val accountId = bridge.createAccount(displayName = "Accept Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:1111111111111111111111111111111111111111"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Accept the call (even though it's our own outgoing call)
        // This tests that the bridge method doesn't crash
        bridge.acceptCall(accountId, callId, withVideo = false)

        // Then: Method completes without error
    }

    @Test
    fun testAcceptCallWithVideo() = runTest {
        // Given: Account
        val accountId = bridge.createAccount(displayName = "Accept Video Test")
        createdAccounts.add(accountId)

        // When: Accept a dummy call with video
        val dummyCallId = "dummy-call-id"
        bridge.acceptCall(accountId, dummyCallId, withVideo = true)

        // Then: Method completes without crash
    }

    @Test
    fun testRefuseCall() = runTest {
        // Given: Account
        val accountId = bridge.createAccount(displayName = "Refuse Call Test")
        createdAccounts.add(accountId)

        // When: Refuse a dummy call
        val dummyCallId = "dummy-call-id"
        bridge.refuseCall(accountId, dummyCallId)

        // Then: Method completes without error
    }

    @Test
    fun testHangUp() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Hangup Test")
        createdAccounts.add(accountId)

        val uri = "ring:2222222222222222222222222222222222222222"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Hang up the call
        bridge.hangUp(accountId, callId)

        // Then: Method completes without error
    }

    @Test
    fun testHangUpNonExistentCall() = runTest {
        // Given: Account without calls
        val accountId = bridge.createAccount(displayName = "Hangup Nonexistent Test")
        createdAccounts.add(accountId)

        // When: Try to hang up a non-existent call
        val dummyCallId = "non-existent-call-id"
        bridge.hangUp(accountId, dummyCallId)

        // Then: Method completes gracefully (daemon handles this)
    }

    // ==================== Call Control Operations ====================

    @Test
    fun testHoldCall() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Hold Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:3333333333333333333333333333333333333333"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Hold the call
        bridge.holdCall(accountId, callId)

        // Then: Method completes without error
    }

    @Test
    fun testUnholdCall() = runTest {
        // Given: Account with a held call
        val accountId = bridge.createAccount(displayName = "Unhold Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:4444444444444444444444444444444444444444"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)
        bridge.holdCall(accountId, callId)

        // When: Unhold the call
        bridge.unholdCall(accountId, callId)

        // Then: Method completes without error
    }

    @Test
    fun testMuteAudio() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Mute Audio Test")
        createdAccounts.add(accountId)

        val uri = "ring:5555555555555555555555555555555555555555"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Mute audio
        bridge.muteAudio(accountId, callId, muted = true)

        // Then: Method completes without error
    }

    @Test
    fun testUnmuteAudio() = runTest {
        // Given: Account with a muted call
        val accountId = bridge.createAccount(displayName = "Unmute Audio Test")
        createdAccounts.add(accountId)

        val uri = "ring:6666666666666666666666666666666666666666"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)
        bridge.muteAudio(accountId, callId, muted = true)

        // When: Unmute audio
        bridge.muteAudio(accountId, callId, muted = false)

        // Then: Method completes without error
    }

    @Test
    fun testMuteVideo() = runTest {
        // Given: Account with a video call
        val accountId = bridge.createAccount(displayName = "Mute Video Test")
        createdAccounts.add(accountId)

        val uri = "ring:7777777777777777777777777777777777777777"
        val callId = bridge.placeCall(accountId, uri, withVideo = true)

        // When: Mute video
        bridge.muteVideo(accountId, callId, muted = true)

        // Then: Method completes without error
    }

    @Test
    fun testUnmuteVideo() = runTest {
        // Given: Account with a muted video call
        val accountId = bridge.createAccount(displayName = "Unmute Video Test")
        createdAccounts.add(accountId)

        val uri = "ring:8888888888888888888888888888888888888888"
        val callId = bridge.placeCall(accountId, uri, withVideo = true)
        bridge.muteVideo(accountId, callId, muted = true)

        // When: Unmute video
        bridge.muteVideo(accountId, callId, muted = false)

        // Then: Method completes without error
    }

    // ==================== Call Information ====================

    @Test
    fun testGetCallDetails() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Call Details Test")
        createdAccounts.add(accountId)

        val uri = "ring:9999999999999999999999999999999999999999"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Get call details
        val details = bridge.getCallDetails(accountId, callId)

        // Then: Returns a map
        assertThat(details).isNotNull()
        assertThat(details).isInstanceOf(Map::class.java)
    }

    @Test
    fun testGetCallDetailsForNonExistentCall() = runTest {
        // Given: Account without calls
        val accountId = bridge.createAccount(displayName = "Call Details Empty Test")
        createdAccounts.add(accountId)

        // When: Get details for non-existent call
        val details = bridge.getCallDetails(accountId, "non-existent-call-id")

        // Then: Returns empty or minimal map
        assertThat(details).isNotNull()
        assertThat(details).isInstanceOf(Map::class.java)
    }

    @Test
    fun testGetActiveCalls() = runTest {
        // Given: Account with calls
        val accountId = bridge.createAccount(displayName = "Active Calls Test")
        createdAccounts.add(accountId)

        val uri1 = "ring:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val uri2 = "ring:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        bridge.placeCall(accountId, uri1, withVideo = false)
        bridge.placeCall(accountId, uri2, withVideo = false)

        // When: Get active calls
        val activeCalls = bridge.getActiveCalls(accountId)

        // Then: Returns a list
        assertThat(activeCalls).isNotNull()
        assertThat(activeCalls).isInstanceOf(List::class.java)
    }

    @Test
    fun testGetActiveCallsReturnsEmptyForNewAccount() = runTest {
        // Given: New account without calls
        val accountId = bridge.createAccount(displayName = "No Active Calls Test")
        createdAccounts.add(accountId)

        // When: Get active calls
        val activeCalls = bridge.getActiveCalls(accountId)

        // Then: Returns empty list
        assertThat(activeCalls).isNotNull()
        assertThat(activeCalls).isEmpty()
    }

    // ==================== Multiple Calls ====================

    @Test
    fun testMultipleCalls() = runTest {
        // Given: Account
        val accountId = bridge.createAccount(displayName = "Multiple Calls Test")
        createdAccounts.add(accountId)

        // When: Place multiple calls
        val call1 = bridge.placeCall(accountId, "ring:cccccccccccccccccccccccccccccccccccccccc", withVideo = false)
        val call2 = bridge.placeCall(accountId, "ring:dddddddddddddddddddddddddddddddddddddddd", withVideo = false)
        val call3 = bridge.placeCall(accountId, "ring:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", withVideo = true)

        // Then: All calls placed without crash
        assertThat(call1).isNotNull()
        assertThat(call2).isNotNull()
        assertThat(call3).isNotNull()
    }

    @Test
    fun testCallOperationsWithMultipleAccounts() = runTest {
        // Given: Two different accounts
        val account1 = bridge.createAccount(displayName = "Multi Account Call 1")
        val account2 = bridge.createAccount(displayName = "Multi Account Call 2")
        createdAccounts.addAll(listOf(account1, account2))

        // When: Place calls from different accounts
        val call1 = bridge.placeCall(account1, "ring:fffffffffffffffffffffffffffffffffffffff", withVideo = false)
        val call2 = bridge.placeCall(account2, "ring:00000000000000000000000000000000000000000", withVideo = false)

        // Then: Both calls placed successfully
        assertThat(call1).isNotNull()
        assertThat(call2).isNotNull()

        // And: Get active calls for each account
        val activeCalls1 = bridge.getActiveCalls(account1)
        val activeCalls2 = bridge.getActiveCalls(account2)

        assertThat(activeCalls1).isNotNull()
        assertThat(activeCalls2).isNotNull()
    }

    // ==================== Conference Calls ====================

    @Test
    fun testCreateConference() = runTest {
        // Given: Account
        val accountId = bridge.createAccount(displayName = "Conference Test")
        createdAccounts.add(accountId)

        // When: Create a conference with participants
        val participants = listOf(
            "ring:1111111111111111111111111111111111111111",
            "ring:2222222222222222222222222222222222222222"
        )
        val conferenceId = bridge.createConference(accountId, participants)

        // Then: Conference ID is returned (may be empty)
        assertThat(conferenceId).isNotNull()
    }

    @Test
    fun testCreateConferenceWithEmptyParticipants() = runTest {
        // Given: Account
        val accountId = bridge.createAccount(displayName = "Empty Conference Test")
        createdAccounts.add(accountId)

        // When: Create a conference with no participants
        val conferenceId = bridge.createConference(accountId, emptyList())

        // Then: Method completes without crash
        assertThat(conferenceId).isNotNull()
    }

    @Test
    fun testAddParticipantToConference() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Add Participant Test")
        createdAccounts.add(accountId)

        val participants = listOf("ring:3333333333333333333333333333333333333333")
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Add another participant
        val newParticipantUri = "ring:4444444444444444444444444444444444444444"
        val callId = bridge.placeCall(accountId, newParticipantUri, withVideo = false)
        bridge.addParticipantToConference(accountId, callId, accountId, conferenceId)

        // Then: Method completes without error
    }

    @Test
    fun testHangUpConference() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Hangup Conference Test")
        createdAccounts.add(accountId)

        val participants = listOf("ring:5555555555555555555555555555555555555555")
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Hang up the conference
        bridge.hangUpConference(accountId, conferenceId)

        // Then: Method completes without error
    }

    @Test
    fun testGetConferenceDetails() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Conference Details Test")
        createdAccounts.add(accountId)

        val participants = listOf("ring:6666666666666666666666666666666666666666")
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Get conference details
        val details = bridge.getConferenceDetails(accountId, conferenceId)

        // Then: Returns a map
        assertThat(details).isNotNull()
        assertThat(details).isInstanceOf(Map::class.java)
    }

    @Test
    fun testGetConferenceParticipants() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Conference Participants Test")
        createdAccounts.add(accountId)

        val participants = listOf(
            "ring:7777777777777777777777777777777777777777",
            "ring:8888888888888888888888888888888888888888"
        )
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Get conference participants
        val participantList = bridge.getConferenceParticipants(accountId, conferenceId)

        // Then: Returns a list
        assertThat(participantList).isNotNull()
        assertThat(participantList).isInstanceOf(List::class.java)
    }

    @Test
    fun testGetConferenceInfos() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Conference Infos Test")
        createdAccounts.add(accountId)

        val participants = listOf("ring:9999999999999999999999999999999999999999")
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Get conference infos
        val infos = bridge.getConferenceInfos(accountId, conferenceId)

        // Then: Returns a list of maps
        assertThat(infos).isNotNull()
        assertThat(infos).isInstanceOf(List::class.java)
    }

    @Test
    fun testMuteConferenceParticipant() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Mute Participant Test")
        createdAccounts.add(accountId)

        val participantUri = "ring:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val participants = listOf(participantUri)
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Mute a conference participant
        bridge.muteConferenceParticipant(accountId, conferenceId, participantUri, muted = true)

        // Then: Method completes without error
    }

    @Test
    fun testUnmuteConferenceParticipant() = runTest {
        // Given: Account with a conference and muted participant
        val accountId = bridge.createAccount(displayName = "Unmute Participant Test")
        createdAccounts.add(accountId)

        val participantUri = "ring:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val participants = listOf(participantUri)
        val conferenceId = bridge.createConference(accountId, participants)
        bridge.muteConferenceParticipant(accountId, conferenceId, participantUri, muted = true)

        // When: Unmute the participant
        bridge.muteConferenceParticipant(accountId, conferenceId, participantUri, muted = false)

        // Then: Method completes without error
    }

    @Test
    fun testHangUpConferenceParticipant() = runTest {
        // Given: Account with a conference
        val accountId = bridge.createAccount(displayName = "Hangup Participant Test")
        createdAccounts.add(accountId)

        val participantUri = "ring:cccccccccccccccccccccccccccccccccccccccc"
        val participants = listOf(participantUri)
        val conferenceId = bridge.createConference(accountId, participants)

        // When: Hang up a specific participant
        val deviceId = "dummy-device-id"
        bridge.hangUpConferenceParticipant(accountId, conferenceId, participantUri, deviceId)

        // Then: Method completes without error
    }
}
