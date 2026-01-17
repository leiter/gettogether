# Call Crash Analysis - 2025-12-21

## Crash Summary

**Device:** Pixel 7a (Caller - 37281JEHN03065)
**Time:** 15:21:49 (about 1 minute after call started)
**Error:** Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0

## Stack Trace

```
Fatal signal 11 (SIGSEGV) in tid 9590 (Thread-36)

#00 pc 0000000000a516d8  libjami-core.so
#15 pc 0000000000b055c8  libjami-core.so (pjsip_endpt_process_rx_data+488)
#16 pc 0000000000b04e20  libjami-core.so
#17 pc 0000000000b0dcb4  libjami-core.so (pjsip_tpmgr_receive_packet+224)
```

## Analysis

### Location
Crash in **pjsip_endpt_process_rx_data** - This is PJSIP (SIP library) processing incoming packets

###  Timing
- Call initiated: 15:20:47
- Connection established: 15:20:51
- **Crash: 15:21:49** (58 seconds after start)

### What Was Happening

**Pixel 7a (Caller):**
- Successfully initiated call
- ICE connection negotiated
- TLS session established
- Crash 1 minute into call

**Pixel 2 (Receiver):**
```
12-21 15:21:50.892  ICE send failed: Broken pipe
12-21 15:21:50.892  [TLS] Transport failure on tx: errno = 5: I/O error
12-21 15:21:50.892  [TLS] Fatal error in recv: The TLS connection was non-properly terminated
```

## Root Cause

**NOT the audio input enumeration bug we fixed!**

This is a **different crash** in PJSIP media processing during an active call.

### Likely Cause

This appears to be a **media codec or RTP handling bug** in the native library, triggered when:
1. Call is in progress
2. Media packets (audio/video) are being exchanged
3. PJSIP tries to process an incoming SIP/RTP packet
4. Null pointer dereference in packet processing

## Evidence

1. ✅ Audio input was initialized (no crash at call start)
2. ✅ Call connected successfully
3. ❌ Crash occurred during active call media processing
4. ❌ Null pointer in PJSIP RTP receive handler

## Is This Related to Our Fix?

**NO** - This is unrelated to the audio input device enumeration fix:

- Our fix prevents crashes during `getAudioInputDevices()`
- This crash is in PJSIP media handling during an active call
- Different location in code (PJSIP vs audio device enumeration)
- Different trigger (active call vs call setup)

## Possible Causes

### 1. Audio Codec Negotiation Issue
- Incompatible codec configuration
- Codec initialization failed silently
- Null codec pointer in RTP handler

### 2. Media Stream Initialization
- Audio stream not properly initialized
- Missing audio device setup before media starts
- Null audio endpoint

### 3. PJSIP Library Bug
- Known bug in PJSIP version used by Jami
- Race condition in media processing
- Buffer overflow or underflow

## Next Steps

### Immediate Investigation

1. **Check if audio was actually flowing** before crash
2. **Verify codec configuration**
3. **Look for PJSIP-related logs** around crash time
4. **Try audio-only call** (disable video)

### Potential Fixes

#### Option 1: Initialize Audio Endpoint Earlier
```kotlin
// Try initializing audio BEFORE placing call AND starting daemon
fun initializeOutgoingCall(...) {
    viewModelScope.launch {
        // Initialize audio input first
        jamiBridge.useDefaultAudioInputDevice()

        // ALSO initialize audio output
        jamiBridge.switchAudioOutput(useSpeaker = false)

        // Then load contact and start call
        loadContactInfo(contactId)
        ...
    }
}
```

#### Option 2: Add Audio Layer Initialization
The issue might be that the audio layer in PJSIP isn't initialized before media starts flowing.

We may need to call additional native methods to properly initialize the audio layer.

#### Option 3: Codec Configuration
Force specific codec configuration that's known to work:
- Use only basic codecs (PCMU, PCMA)
- Disable problematic codecs
- Set explicit audio parameters

## Test Matrix

| Test | Result | Notes |
|------|--------|-------|
| Call initiation | ✅ Works | No crash at start |
| Audio input init | ✅ Works | useDefaultAudioInputDevice() called |
| ICE negotiation | ✅ Works | Connection established |
| Media flow | ❌ CRASHES | After ~1 minute |

## Recommendations

1. **Try initializing audio output as well** - Not just input
2. **Test with audio-only call** - Disable video to isolate issue
3. **Add more defensive checks** - Null checks before media operations
4. **Check PJSIP configuration** - Verify audio endpoint setup

## Questions to Answer

- [ ] Does the crash happen immediately or after some time?
- [ ] Does it crash on EVERY call or intermittently?
- [ ] Does audio-only crash differently than video call?
- [ ] What codecs are being negotiated?
- [ ] Is the audio device actually opened before media starts?

## Workaround Ideas

### Try #1: Full Audio Initialization
```kotlin
suspend fun initializeAudioForCall() {
    // Input device
    jamiBridge.useDefaultAudioInputDevice()

    // Output device
    jamiBridge.setAudioOutputDevice(0)

    // Speaker mode
    jamiBridge.switchAudioOutput(useSpeaker = false)

    // Small delay to ensure initialization completes
    delay(200)
}
```

### Try #2: Start Audio Stream Explicitly
```kotlin
// Before placing call, try starting audio I/O
jamiBridge.startAudio() // If such method exists
```

### Try #3: Disable Video
```kotlin
// Test audio-only calls first
val callId = jamiBridge.placeCall(accountId, contactId, withVideo = false)
```

## Action Items

1. Add more audio initialization before call
2. Test audio-only call
3. Get more detailed PJSIP logs
4. Check if audio devices are actually opened
5. Verify codec negotiation

---

**Status:** Active investigation needed
**Priority:** High - Prevents making calls
**Type:** Native library bug (PJSIP media handling)
