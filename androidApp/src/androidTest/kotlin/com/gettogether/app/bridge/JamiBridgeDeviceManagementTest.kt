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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for JamiBridge audio/video device management operations.
 * These tests verify that the Kotlin bridge can properly manage cameras,
 * microphones, and audio output devices through the native Jami daemon.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JamiBridgeDeviceManagementTest {

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
            // Stop video if it was started
            try {
                bridge.stopVideo()
            } catch (e: Exception) {
                // Ignore if video wasn't started
            }

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

    // ==================== Video Device Operations ====================

    @Test
    fun testGetVideoDevices() = runTest {
        // When: Get available video devices
        val devices = bridge.getVideoDevices()

        // Then: Returns a list (may be empty on emulator)
        assertThat(devices).isNotNull()
        assertThat(devices).isInstanceOf(List::class.java)
    }

    @Test
    fun testGetCurrentVideoDevice() = runTest {
        // When: Get current video device
        val currentDevice = bridge.getCurrentVideoDevice()

        // Then: Returns a string (may be empty if no device selected)
        assertThat(currentDevice).isNotNull()
    }

    @Test
    fun testSetVideoDevice() = runTest {
        // Given: Available video devices
        val devices = bridge.getVideoDevices()

        // When: Set video device (use dummy if no devices available)
        val deviceId = if (devices.isNotEmpty()) devices[0] else "dummy-device"
        bridge.setVideoDevice(deviceId)

        // Then: Method completes without error
    }

    @Test
    fun testSetVideoDeviceWithDummyId() = runTest {
        // When: Set video device with dummy ID
        val dummyDeviceId = "non-existent-camera"
        bridge.setVideoDevice(dummyDeviceId)

        // Then: Method completes without crash (daemon handles gracefully)
    }

    @Test
    fun testStartVideo() = runTest {
        // When: Start video capture
        bridge.startVideo()

        // Then: Method completes without error
    }

    @Test
    fun testStopVideo() = runTest {
        // Given: Video is started
        bridge.startVideo()

        // When: Stop video capture
        bridge.stopVideo()

        // Then: Method completes without error
    }

    @Test
    fun testStartStopVideoSequence() = runTest {
        // When: Start and stop video multiple times
        bridge.startVideo()
        bridge.stopVideo()

        bridge.startVideo()
        bridge.stopVideo()

        bridge.startVideo()
        bridge.stopVideo()

        // Then: All operations complete without crash
    }

    @Test
    fun testSwitchCamera() = runTest {
        // When: Switch camera (front/back)
        bridge.switchCamera()

        // Then: Method completes without error
        // Note: On emulator, this may not have visible effect
    }

    @Test
    fun testSwitchCameraMultipleTimes() = runTest {
        // When: Switch camera multiple times
        bridge.switchCamera()
        bridge.switchCamera()
        bridge.switchCamera()

        // Then: All operations complete without crash
    }

    @Test
    fun testVideoDeviceWorkflow() = runTest {
        // When: Complete video device workflow
        // 1. Get available devices
        val devices = bridge.getVideoDevices()
        assertThat(devices).isNotNull()

        // 2. Get current device
        val currentDevice = bridge.getCurrentVideoDevice()
        assertThat(currentDevice).isNotNull()

        // 3. Set device (if available)
        if (devices.isNotEmpty()) {
            bridge.setVideoDevice(devices[0])
        }

        // 4. Start video
        bridge.startVideo()

        // 5. Switch camera
        bridge.switchCamera()

        // 6. Stop video
        bridge.stopVideo()

        // Then: Complete workflow executes without crash
    }

    // ==================== Audio Output Device Operations ====================

    @Test
    fun testGetAudioOutputDevices() = runTest {
        // When: Get available audio output devices
        val devices = bridge.getAudioOutputDevices()

        // Then: Returns a list
        assertThat(devices).isNotNull()
        assertThat(devices).isInstanceOf(List::class.java)
    }

    @Test
    fun testSetAudioOutputDevice() = runTest {
        // Given: Available audio output devices
        val devices = bridge.getAudioOutputDevices()

        // When: Set audio output device
        val index = if (devices.isNotEmpty()) 0 else -1
        bridge.setAudioOutputDevice(index)

        // Then: Method completes without error
    }

    @Test
    fun testSetAudioOutputDeviceWithInvalidIndex() = runTest {
        // When: Set audio output device with invalid index
        val invalidIndex = 999
        bridge.setAudioOutputDevice(invalidIndex)

        // Then: Method completes without crash (daemon handles gracefully)
    }

    @Test
    fun testSwitchAudioOutputToSpeaker() = runTest {
        // When: Switch to speaker
        bridge.switchAudioOutput(useSpeaker = true)

        // Then: Method completes without error
    }

    @Test
    fun testSwitchAudioOutputToEarpiece() = runTest {
        // When: Switch to earpiece
        bridge.switchAudioOutput(useSpeaker = false)

        // Then: Method completes without error
    }

    @Test
    fun testSwitchAudioOutputMultipleTimes() = runTest {
        // When: Toggle audio output multiple times
        bridge.switchAudioOutput(useSpeaker = true)
        bridge.switchAudioOutput(useSpeaker = false)
        bridge.switchAudioOutput(useSpeaker = true)
        bridge.switchAudioOutput(useSpeaker = false)

        // Then: All operations complete without crash
    }

    // ==================== Audio Input Device Operations ====================

    @Test
    @Ignore("Native crash when getting audio input devices list. Native library bug, not bridge issue.")
    fun testGetAudioInputDevices() = runTest {
        // When: Get available audio input devices
        val devices = bridge.getAudioInputDevices()

        // Then: Returns a list
        assertThat(devices).isNotNull()
        assertThat(devices).isInstanceOf(List::class.java)
    }

    @Test
    @Ignore("Native crash when setting audio input device. Native library bug, not bridge issue.")
    fun testSetAudioInputDevice() = runTest {
        // Given: Available audio input devices
        val devices = bridge.getAudioInputDevices()

        // When: Set audio input device
        val index = if (devices.isNotEmpty()) 0 else -1
        bridge.setAudioInputDevice(index)

        // Then: Method completes without error
    }

    @Test
    @Ignore("Native crash when setting audio input device. Native library bug, not bridge issue.")
    fun testSetAudioInputDeviceWithInvalidIndex() = runTest {
        // When: Set audio input device with invalid index
        val invalidIndex = 999
        bridge.setAudioInputDevice(invalidIndex)

        // Then: Method completes without crash (daemon handles gracefully)
    }

    @Test
    @Ignore("Native crash when switching audio input devices. Native library bug, not bridge issue.")
    fun testSwitchBetweenMultipleInputDevices() = runTest {
        // Given: Available audio input devices
        val devices = bridge.getAudioInputDevices()

        // When: Switch between devices (if multiple available)
        if (devices.size >= 2) {
            bridge.setAudioInputDevice(0)
            bridge.setAudioInputDevice(1)
            bridge.setAudioInputDevice(0)
        } else {
            // Test with single device
            bridge.setAudioInputDevice(0)
        }

        // Then: All operations complete without crash
    }

    // ==================== Combined Audio Device Operations ====================

    @Test
    @Ignore("Native crash when setting audio input device. Native library bug, not bridge issue.")
    fun testAudioDeviceWorkflow() = runTest {
        // When: Complete audio device workflow
        // 1. Get output devices
        val outputDevices = bridge.getAudioOutputDevices()
        assertThat(outputDevices).isNotNull()

        // 2. Get input devices
        val inputDevices = bridge.getAudioInputDevices()
        assertThat(inputDevices).isNotNull()

        // 3. Set output device
        if (outputDevices.isNotEmpty()) {
            bridge.setAudioOutputDevice(0)
        }

        // 4. Set input device
        if (inputDevices.isNotEmpty()) {
            bridge.setAudioInputDevice(0)
        }

        // 5. Switch audio output
        bridge.switchAudioOutput(useSpeaker = true)
        bridge.switchAudioOutput(useSpeaker = false)

        // Then: Complete workflow executes without crash
    }

    @Test
    @Ignore("Native crash when enumerating all device types together. Native library bug, not bridge issue.")
    fun testAllDeviceTypesEnumeration() = runTest {
        // When: Enumerate all device types
        val videoDevices = bridge.getVideoDevices()
        val audioOutputDevices = bridge.getAudioOutputDevices()
        val audioInputDevices = bridge.getAudioInputDevices()
        val currentVideoDevice = bridge.getCurrentVideoDevice()

        // Then: All queries return successfully
        assertThat(videoDevices).isNotNull()
        assertThat(audioOutputDevices).isNotNull()
        assertThat(audioInputDevices).isNotNull()
        assertThat(currentVideoDevice).isNotNull()
    }

    // ==================== Device Management During Calls ====================

    @Test
    fun testSwitchCameraDuringCall() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Camera Switch Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:1234567890abcdef1234567890abcdef12345678"
        val callId = bridge.placeCall(accountId, uri, withVideo = true)

        // When: Switch camera during call
        bridge.switchCamera()

        // Then: Method completes without error
        // Cleanup
        bridge.hangUp(accountId, callId)
    }

    @Test
    fun testSwitchAudioOutputDuringCall() = runTest {
        // Given: Account with a call
        val accountId = bridge.createAccount(displayName = "Audio Switch Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:abcdef1234567890abcdef1234567890abcdef12"
        val callId = bridge.placeCall(accountId, uri, withVideo = false)

        // When: Switch audio output during call
        bridge.switchAudioOutput(useSpeaker = true)
        bridge.switchAudioOutput(useSpeaker = false)

        // Then: Method completes without error
        // Cleanup
        bridge.hangUp(accountId, callId)
    }

    @Test
    fun testVideoControlDuringCall() = runTest {
        // Given: Account with a video call
        val accountId = bridge.createAccount(displayName = "Video Control Call Test")
        createdAccounts.add(accountId)

        val uri = "ring:1111111111111111111111111111111111111111"
        val callId = bridge.placeCall(accountId, uri, withVideo = true)

        // When: Control video during call
        bridge.startVideo()
        bridge.switchCamera()
        bridge.stopVideo()

        // Then: All operations complete without crash
        // Cleanup
        bridge.hangUp(accountId, callId)
    }

    // ==================== Edge Cases ====================

    @Test
    fun testStopVideoWithoutStart() = runTest {
        // When: Stop video without starting it first
        bridge.stopVideo()

        // Then: Method completes gracefully
    }

    @Test
    fun testMultipleStartVideoWithoutStop() = runTest {
        // When: Start video multiple times without stopping
        bridge.startVideo()
        bridge.startVideo()
        bridge.startVideo()

        // Then: Operations complete without crash
        // Cleanup
        bridge.stopVideo()
    }

    @Test
    fun testSetVideoDeviceWhileVideoRunning() = runTest {
        // Given: Video is running
        bridge.startVideo()

        // When: Change video device while running
        val devices = bridge.getVideoDevices()
        if (devices.isNotEmpty()) {
            bridge.setVideoDevice(devices[0])
        }

        // Then: Operation completes without crash
        // Cleanup
        bridge.stopVideo()
    }

    @Test
    fun testRapidDeviceSwitching() = runTest {
        // When: Rapidly switch between devices
        repeat(5) {
            bridge.switchCamera()
            bridge.switchAudioOutput(useSpeaker = true)
            bridge.switchAudioOutput(useSpeaker = false)
        }

        // Then: All operations complete without crash
    }

    // ==================== Device State Verification ====================

    @Test
    fun testVideoDevicesListNotNull() = runTest {
        // When: Get video devices multiple times
        val devices1 = bridge.getVideoDevices()
        val devices2 = bridge.getVideoDevices()
        val devices3 = bridge.getVideoDevices()

        // Then: All calls return non-null lists
        assertThat(devices1).isNotNull()
        assertThat(devices2).isNotNull()
        assertThat(devices3).isNotNull()
    }

    @Test
    @Ignore("Native crash when getting audio input devices list. Native library bug, not bridge issue.")
    fun testAudioDevicesListNotNull() = runTest {
        // When: Get audio devices multiple times
        val outputDevices1 = bridge.getAudioOutputDevices()
        val outputDevices2 = bridge.getAudioOutputDevices()
        val inputDevices1 = bridge.getAudioInputDevices()
        val inputDevices2 = bridge.getAudioInputDevices()

        // Then: All calls return non-null lists
        assertThat(outputDevices1).isNotNull()
        assertThat(outputDevices2).isNotNull()
        assertThat(inputDevices1).isNotNull()
        assertThat(inputDevices2).isNotNull()
    }

    @Test
    fun testCurrentVideoDeviceConsistency() = runTest {
        // When: Get current device multiple times
        val device1 = bridge.getCurrentVideoDevice()
        val device2 = bridge.getCurrentVideoDevice()

        // Then: Both calls return non-null strings
        assertThat(device1).isNotNull()
        assertThat(device2).isNotNull()
    }

    // ==================== Multiple Accounts ====================

    @Test
    fun testDeviceManagementWithMultipleAccounts() = runTest {
        // Given: Multiple accounts
        val account1 = bridge.createAccount(displayName = "Device Test Account 1")
        val account2 = bridge.createAccount(displayName = "Device Test Account 2")
        createdAccounts.addAll(listOf(account1, account2))

        // When: Perform device operations with multiple accounts
        bridge.switchCamera()
        bridge.switchAudioOutput(useSpeaker = true)

        val videoDevices = bridge.getVideoDevices()
        val audioDevices = bridge.getAudioOutputDevices()

        // Then: Device operations work independently of accounts
        assertThat(videoDevices).isNotNull()
        assertThat(audioDevices).isNotNull()
    }
}
