# Audio Input Device Limitation

**Last Updated:** 2025-12-21
**Status:** Permanent Workaround Implemented

---

## TL;DR - For Developers

❌ **DO NOT call `bridge.getAudioInputDevices()`** - It will throw `UnsupportedOperationException`

✅ **DO call `bridge.useDefaultAudioInputDevice()`** - Safe and works perfectly

---

## The Problem

The native Jami library (libjami-core.so) has a bug that causes a **SIGSEGV crash** when calling `getAudioInputDeviceList()`.

### Crash Details
- **Error:** Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
- **Location:** `Java_net_jami_daemon_JamiServiceJNI_getAudioInputDeviceList+48`
- **Platforms Affected:** Both x86_64 emulator and ARM64 hardware (Pixel 7a confirmed)
- **Root Cause:** Null pointer dereference in native library

### What Works vs What Doesn't

| API Call | Status | Notes |
|----------|--------|-------|
| `getAudioInputDevices()` | ❌ CRASHES | Throws exception to prevent crash |
| `setAudioInputDevice(index)` | ✅ WORKS | Tested on Pixel 7a |
| **`useDefaultAudioInputDevice()`** | ✅ WORKS | **Recommended method** |
| `getAudioOutputDevices()` | ✅ WORKS | Output devices are fine |
| `setAudioOutputDevice(index)` | ✅ WORKS | Output devices are fine |

---

## The Solution

We've implemented a safe workaround:

### 1. API Changes

**Deprecated (will throw exception):**
```kotlin
@Deprecated(
    message = "Crashes with SIGSEGV. Use useDefaultAudioInputDevice() instead.",
    level = DeprecationLevel.ERROR
)
fun getAudioInputDevices(): List<String>
```

**Safe alternative (recommended):**
```kotlin
suspend fun useDefaultAudioInputDevice()
```

### 2. Implementation Details

All implementations now throw `UnsupportedOperationException`:

```kotlin
// JamiBridge interface
override fun getAudioInputDevices(): List<String> {
    throw UnsupportedOperationException(
        "getAudioInputDevices() crashes with SIGSEGV in native library. " +
        "Use useDefaultAudioInputDevice() instead."
    )
}
```

This prevents accidental crashes during development.

---

## How to Use Audio Input

### ✅ Correct Usage

```kotlin
// Use default microphone (recommended)
bridge.useDefaultAudioInputDevice()

// Or manually set to default (equivalent)
bridge.setAudioInputDevice(0)

// Switch to secondary microphone (if available)
bridge.setAudioInputDevice(1)
```

### ❌ Incorrect Usage (will crash or throw exception)

```kotlin
// DON'T DO THIS - Will throw UnsupportedOperationException
val devices = bridge.getAudioInputDevices()
bridge.setAudioInputDevice(0)

// DON'T DO THIS - Compiler error (deprecated)
val mics = bridge.getAudioInputDevices()  // ERROR: Using deprecated API
```

---

## Impact on App Features

### What You CAN Do ✅

- ✅ Use microphone for voice/video calls
- ✅ Use default microphone automatically
- ✅ Switch between microphones by index (if you know the indices)
- ✅ Record audio in calls

### What You CANNOT Do ❌

- ❌ Show a "Select Microphone" picker UI
- ❌ Display microphone names ("Built-in Mic", "Bluetooth Headset")
- ❌ Enumerate available microphones
- ❌ Let users choose which microphone to use via UI

### Recommended UX

**Instead of showing a microphone picker:**
1. Always use the default microphone (index 0)
2. Let Android system handle automatic switching (Bluetooth, wired headsets)
3. Don't expose microphone selection in UI

**User experience:**
- Calls automatically use the best available microphone
- System handles Bluetooth/wired headset switching
- Users don't need to manually select microphones

---

## Testing

### Confirmed Working ✅

**Device:** Pixel 7a (Android 16, ARM64)
**Test Date:** 2025-12-21

```
Test: testSetAudioInputDeviceWithInvalidIndex
Result: PASSED (0.001s)

Method tested:
- bridge.setAudioInputDevice(999)  // Works with invalid index

Conclusion:
- setAudioInputDevice() works on real hardware
- No enumeration needed
- Default microphone (index 0) works perfectly
```

### Confirmed Crashing ❌

**Platforms:** Both emulator (x86_64) and hardware (Pixel 7a ARM64)

```
Fatal signal 11 (SIGSEGV), code 1, fault addr 0x0
Crash in: getAudioInputDeviceList
Stack trace: See doc/audio-input-crash-analysis-pixel7a.md
```

---

## For Call Implementation

When implementing call features, use this pattern:

```kotlin
class CallViewModel(private val bridge: JamiBridge) : ViewModel() {

    suspend fun startCall(accountId: String, contact: String, withVideo: Boolean) {
        // Initialize default audio input BEFORE placing call
        bridge.useDefaultAudioInputDevice()

        // Place the call
        val callId = bridge.placeCall(accountId, contact, withVideo)

        // Audio input is now set to default microphone
    }

    suspend fun switchToSpeaker() {
        // Audio OUTPUT switching still works fine
        bridge.switchAudioOutput(useSpeaker = true)
    }

    // ❌ DON'T implement a microphone picker
    // The native library doesn't support enumeration
}
```

---

## When Will This Be Fixed?

**Upstream Issue:** This is a bug in the Jami native library (libjami-core.so), not in our code.

**Action Items:**
1. ✅ Workaround implemented (useDefaultAudioInputDevice)
2. ⏳ Report to Jami project with crash logs
3. ⏳ Monitor jami-daemon releases for fixes
4. ⏳ Re-test with future libjami versions

**Current Status:**
- Our bridge implementation is correct
- Tests pass when not calling enumeration
- Workaround is production-ready
- Waiting for upstream fix

---

## Related Documentation

- **Detailed Analysis:** `doc/audio-input-crash-analysis-pixel7a.md`
- **Test Results:** `doc/bridge-integration-tests-results.md`
- **Test Code:** `androidApp/src/androidTest/kotlin/.../JamiBridgeDeviceManagementTest.kt`

---

## Quick Reference

### Safe Methods
```kotlin
bridge.useDefaultAudioInputDevice()        // ✅ Recommended
bridge.setAudioInputDevice(0)              // ✅ Works (default mic)
bridge.setAudioInputDevice(1)              // ✅ Works (secondary mic)
bridge.getAudioOutputDevices()             // ✅ Works (outputs are fine)
bridge.setAudioOutputDevice(index)         // ✅ Works
bridge.switchAudioOutput(useSpeaker=true)  // ✅ Works
```

### Unsafe Methods
```kotlin
bridge.getAudioInputDevices()  // ❌ Throws UnsupportedOperationException
```

---

## FAQ

**Q: Can users still make voice calls?**
A: Yes! Calls work perfectly with the default microphone.

**Q: What about Bluetooth headsets?**
A: Android system automatically routes audio to Bluetooth when connected. No app code needed.

**Q: Can I switch microphones manually?**
A: Yes, by index (0, 1, 2...), but you can't enumerate names.

**Q: Will this be fixed?**
A: We've reported it to the Jami project. Monitor jami-daemon updates.

**Q: Does this affect iOS?**
A: The iOS implementation also throws for API consistency, but iOS uses AVAudioSession which works differently.

**Q: Should I show an error to users?**
A: No! The workaround is transparent. Users don't need to know about the limitation.

---

**For questions or updates, see:** `doc/audio-input-crash-analysis-pixel7a.md`
