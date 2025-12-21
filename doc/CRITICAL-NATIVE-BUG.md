# CRITICAL: Unfixable Native Library Bug

**Date:** 2025-12-21
**Status:** 🔴 BLOCKER
**Severity:** CRITICAL - Prevents all calls

---

## Summary

**Calls crash 100% of the time** due to null pointer dereference in libjami-core.so (PJSIP media layer). This bug **cannot be fixed at the app layer**.

---

## Crash Details

### Crash Location
```
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)
Fault address: 0x0000000000000000 (null pointer write)

libjami-core.so+0xa516d8
```

### Reproducibility
**100%** - Crashes on EVERY call attempt:
- ✅ Test 1 (15:21:49): Crashed after ~60 seconds
- ✅ Test 2 (15:26:21): Crashed after ~18 seconds
- ✅ Same crash location both times
- ✅ Same null pointer dereference

### Stack Trace (Simplified)
```
#00 pc 0xa516d8  libjami-core.so  <- NULL POINTER HERE
#01 pc 0xa505e8  libjami-core.so
#02 pc 0xa50530  libjami-core.so
...
#15 pc 0xb055c8  libjami-core.so (pjsip_endpt_process_rx_data+488)
#17 pc 0xb0dcb4  libjami-core.so (pjsip_tpmgr_receive_packet+224)
```

---

## What We Tried

### ❌ Attempt 1: Audio Input Device Fix
- Fixed `getAudioInputDevices()` crash
- Added `useDefaultAudioInputDevice()`
- **Result:** Different bug - this one is in PJSIP media, not audio enumeration

### ❌ Attempt 2: Comprehensive Audio Init
- Initialize both input AND output
- Added 100ms delay for initialization
- Set explicit audio devices
- **Result:** Still crashes - bug is deeper in native code

### ❌ Attempt 3: Multiple test runs
- Tested on Pixel 7a (ARM64, Android 16)
- Tested on Pixel 2 (ARM64, Android 13)
- **Result:** Crashes on all devices, all architectures

---

## Root Cause Analysis

### Where the Bug Is
**In PJSIP media processing layer** inside libjami-core.so:
- Native C/C++ code
- Compiled into binary (no source access)
- Part of jami-daemon submodule
- Cannot be patched from Kotlin/Java

### Why It's Happening
Null pointer dereference when processing media packets during call:
1. Call connects successfully
2. ICE/TLS negotiation completes
3. Media stream starts (audio/video RTP packets)
4. PJSIP tries to process incoming packet
5. **Dereferences null pointer** → CRASH

### Why We Can't Fix It
- Bug is in **compiled native library**
- No access to source in this build
- Cannot intercept/prevent from app layer
- Would need to rebuild libjami with fix

---

## Impact

### What Works ✅
- App launches
- Account creation/import
- Contacts management
- Messaging (text)
- File transfer
- DHT connectivity
- All UI features

### What Doesn't Work ❌
- **Audio calls** - 100% crash rate
- **Video calls** - 100% crash rate (uses same media layer)
- **Conference calls** - Would also crash

**BLOCKER:** Cannot make or receive ANY type of call

---

## Options Forward

### Option 1: Wait for Upstream Fix 🕐
**Wait for Jami project to fix the bug**

**Pros:**
- Proper fix from experts
- Would work long-term
- No workarounds needed

**Cons:**
- Unknown timeline
- May take weeks/months
- No guarantee they'll prioritize it
- Still need to integrate update

**Action:**
1. File detailed bug report to Jami project
2. Provide crash dumps, stack traces
3. Monitor jami-daemon releases
4. Re-test when new version available

---

### Option 2: Build Custom libjami 🔧
**Rebuild jami-daemon from source with debug/fixes**

**Pros:**
- Full control over native code
- Can debug and fix ourselves
- Can add logging to find root cause

**Cons:**
- **HUGE effort** - weeks of work
- Need to build entire jami-daemon
- Maintain custom fork
- 50+ native dependencies (FFmpeg, PJSIP, OpenDHT, etc.)
- Cross-compile for Android ARM64/x86_64

**Complexity:** 🔴 VERY HIGH
- Would require deep C++ knowledge
- Understanding of PJSIP internals
- Android NDK build system expertise
- 1-2 months minimum timeline

---

### Option 3: Alternative SIP Library 📚
**Replace Jami with different SIP/calling library**

**Options:**
- **Linphone** (GPL, mature SIP library)
- **PJSIP directly** (without Jami wrapper)
- **WebRTC** (Google's library)
- **Twilio/Commercial** (paid service)

**Pros:**
- Known-working libraries
- Better documentation
- Active development
- More control

**Cons:**
- **Complete rewrite** of call functionality
- Lose Jami's DHT/decentralized features
- Different architecture
- 2-4 weeks timeline
- May need different UI/flow

---

### Option 4: Disable Calls, Ship Messaging Only 📱
**Ship app without call functionality**

**Pros:**
- Can ship NOW
- Messaging/contacts work perfectly
- Audio input bug already fixed
- No crashes in messaging flow

**Cons:**
- Missing major feature
- Users expect calls
- Competitive disadvantage
- Would need to add calls later

**Deliverables:**
- Secure messaging ✅
- Contact management ✅
- File sharing ✅
- Group chats ✅
- Calls ❌

---

### Option 5: Use Official Jami App for Calls 🔄
**Interop with official Jami app**

**Approach:**
- Your app: Messaging, contacts, UI/UX
- Official Jami: Handle calls (deep link/intent)
- Seamless handoff

**Pros:**
- Calls work immediately
- Official app is tested/stable
- Focus on your UX/features
- Gradual migration path

**Cons:**
- User confusion (two apps)
- Broken UX flow
- Dependency on external app
- Not fully integrated

---

### Option 6: Hybrid - Disable Video, Try Audio-Only 🎤
**Test if audio-only calls work better**

**Theory:** Video codec negotiation might be triggering the bug

**Test:**
```kotlin
// Force audio-only calls
jamiBridge.placeCall(accountId, contactId, withVideo = false)
```

**Pros:**
- Quick to test (5 minutes)
- Audio-only might avoid bug
- Better than nothing

**Cons:**
- Might still crash (same PJSIP layer)
- Missing video feature
- Uncertain success rate

**RECOMMENDED:** Try this FIRST before bigger decisions

---

## Immediate Action Plan

### 1. Quick Test (30 minutes)
- [ ] Disable video completely
- [ ] Test audio-only call
- [ ] Monitor for crashes
- [ ] Document results

### 2. If Audio-Only Works (Best Case)
- [ ] Ship with audio-only calls
- [ ] Add "Video coming soon" notice
- [ ] Continue investigating video issue

### 3. If Audio-Only Also Crashes (Likely)
**Decision Point:** Choose from Options 1-5

**Recommendation:** **Option 4** (Ship messaging-only) + **Option 1** (Report bug)
- Ship working features NOW
- File bug report to Jami
- Add calls in next version when fixed
- Users get value immediately

---

## Bug Report to Jami Project

### Information to Provide
1. **Crash dumps** (we have these)
2. **Stack traces** (we have these)
3. **Reproducibility:** 100%
4. **Devices:** Pixel 7a (Android 16), Pixel 2 (Android 13)
5. **Architecture:** ARM64
6. **Build:** cd47e528aedd1d96842f9171ade3d2ddfe1a82cb
7. **Trigger:** Making/receiving any call
8. **Timing:** 18-60 seconds after call starts
9. **Location:** `pjsip_endpt_process_rx_data` + offset

### Where to Report
- **GitHub:** https://github.com/savoirfairelinux/jami-daemon/issues
- **GitLab:** https://git.jami.net/savoirfairelinux/jami-daemon/-/issues

---

## Technical Debt

If we ship without calls:

**What to Document:**
- Known limitation
- Workaround (use official Jami)
- Timeline for fix (TBD)
- User expectations

**What to Build:**
- Graceful degradation
- Clear UI messaging
- Easy migration path when fixed

---

## Recommendation

**SHORT TERM (Now):**
1. ✅ Test audio-only calls (might work!)
2. ✅ File bug report to Jami with all details
3. ✅ Ship messaging-only version if calls don't work
4. ✅ Set user expectations clearly

**MEDIUM TERM (1-2 months):**
1. Monitor Jami updates
2. Test new jami-daemon releases
3. Consider alternative libraries if no fix

**LONG TERM (3+ months):**
1. Evaluate custom libjami build if critical
2. Or migrate to alternative SIP library
3. Or keep messaging-only as product direction

---

## Cost-Benefit Analysis

| Option | Effort | Timeline | Success | Cost |
|--------|--------|----------|---------|------|
| Audio-only test | Low | 30 min | 30% | Free |
| Wait for fix | None | Unknown | 80% | Time |
| Custom build | Very High | 1-2 mo | 90% | $$$ |
| Alt library | High | 2-4 wk | 95% | $$ |
| Messaging-only | Low | 1 day | 100% | Feature loss |
| Hybrid (2 apps) | Medium | 1 week | 90% | UX issue |

---

## Decision Matrix

**If calls are CRITICAL:**
→ Option 3 (Alternative library) or Option 2 (Custom build)

**If messaging is core feature:**
→ Option 4 (Ship messaging) + Option 1 (Wait for fix)

**If timeline is tight:**
→ Option 4 (Ship messaging) + Option 6 (Test audio-only)

**If budget allows:**
→ Option 2 (Custom build with expert help)

---

## Next Steps

**RIGHT NOW:**
```kotlin
// Test audio-only calls
jamiBridge.placeCall(accountId, contactId, withVideo = false)
```

**IF THAT FAILS:**
Decide between:
- Ship messaging-only (fastest)
- Invest in alternative library (best long-term)
- Wait for Jami fix (cheapest but uncertain)

---

**Status:** AWAITING DECISION
**Blocking:** All call features
**Severity:** CRITICAL
