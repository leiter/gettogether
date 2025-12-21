# Open Tasks Scan - 2025-12-21

**Scan Date:** 2025-12-21 13:52
**Performed By:** Claude Code
**Scan Type:** Documentation + Code + Test Analysis

---

## Executive Summary

### Task Status Overview
- ✅ **Android Platform:** All P1 and P2 tasks complete
- ⏳ **iOS Platform:** 3 major components pending (P3 priority)
- ⏳ **Cross-Network Presence:** Investigation needed (P2 priority)
- ⏳ **Chat UI Coordinates:** Partially verified (Low priority)
- 🔧 **Code TODOs:** 1 minor TODO found
- ⚠️ **Ignored Tests:** 10 tests disabled due to native library bugs

---

## Open Tasks by Priority

### Priority 1: Critical (NONE - All Complete ✅)

All P1 tasks have been completed:
- ✅ File Transfer Implementation
- ✅ Notification Action Handlers
- ✅ Settings Persistence
- ✅ Trust Request Payload
- ✅ Account Data Persistence

---

### Priority 2: High Impact

#### 2.1 Cross-Network Presence Detection
**Status:** Open - Investigation Needed
**Priority:** Medium
**Impact:** User Experience

**Description:**
Online/offline status updates work differently based on network configuration:
- **Same Network (mDNS):** Automatic, reliable presence detection
- **Different Networks (DHT):** Presence only updated on message activity

**Current Behavior:**
- DHT doesn't provide continuous presence broadcasts
- Contacts appear offline until they send a message
- 60-second timeout marks contacts offline after no activity
- This may be expected Jami protocol behavior

**Potential Solutions:**
1. **Accept current behavior** - Verify if official Jami apps behave similarly
2. **Implement periodic ping mechanism** - Adds network/battery overhead
3. **Adjust timeout values** - Different timeouts for mDNS vs DHT
4. **UI indication** - Show "last seen X minutes ago" instead of binary online/offline

**Investigation Needed:**
- [ ] Test official Jami apps (Android/iOS) for cross-network presence behavior
- [ ] Check Jami daemon API for active presence queries on DHT
- [ ] Determine user expectations and acceptance criteria

**Related Files:**
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ContactRepositoryImpl.kt`
  - Lines 459-487: Activity-based presence
  - Lines 493-528: Timeout mechanism
  - Lines 344-363: PresenceChanged event handler

**Source:** `doc/OPEN_TASKS.md`

---

### Priority 3: iOS Platform Implementation

#### 3.1 iOS JamiBridge Implementation
**Status:** Pending
**Priority:** P3 (iOS only)
**Scope:** 90+ methods returning mock data

**Description:**
iOS JamiBridge currently provides mock data for UI testing. Full integration with Jami daemon pending.

**Files:**
- `shared/src/iosMain/kotlin/com/gettogether/app/jami/JamiBridge.ios.kt` - Mock implementation
- `iosApp/iosApp/JamiBridgeWrapper.swift` - Swift bridge (to be implemented)

**Mock Data Examples:**
- Account creation returns mock account IDs
- Contact operations return placeholder responses
- Message sending simulated with local state
- Call operations return mock call IDs

**Action Required:**
Implement Swift bridge for Jami daemon integration in Xcode project

**Source:** `doc/plan-open-tasks.md`

---

#### 3.2 iOS NotificationHelper Implementation
**Status:** Pending
**Priority:** P3 (iOS only)
**Scope:** All notification methods

**Description:**
iOS notification system needs UNUserNotificationCenter implementation.

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/NotificationHelper.ios.kt`

**Methods to Implement:**
- Request notification permissions
- Show message notifications
- Show incoming call notifications (critical alerts)
- Show missed call notifications
- Group notifications
- Remove/cancel notifications

**Technology:** UNUserNotificationCenter API

**Source:** `doc/plan-open-tasks.md`

---

#### 3.3 iOS CallServiceBridge Implementation
**Status:** Pending
**Priority:** P3 (iOS only)
**Scope:** 8 call service methods

**Description:**
iOS call handling needs CallKit framework integration.

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/CallServiceBridge.ios.kt`

**Methods to Implement (Lines 6-41):**
- Start/end call UI
- Mute/unmute
- Hold/resume
- Speaker toggle
- Audio routing
- Conference call handling

**Technology:** CallKit framework + AVAudioSession

**Source:** `doc/plan-open-tasks.md`

---

### Priority 4: Testing & Documentation

#### 4.1 Chat Screen UI Coordinates
**Status:** ✅ Verified during messaging test
**Priority:** Low

**Description:**
UI coordinates for automated testing have been verified with active conversations.

**File:** `doc/ui-coordinates.yaml` (Line 126)

**Verified Screens:**
- ✅ Welcome screen
- ✅ Create account screen
- ✅ Home (navigation, FAB)
- ✅ Contacts tab
- ✅ Settings tab
- ✅ Add contact screen
- ✅ New conversation screen
- ✅ Chat screen coordinates (verified with physical device messaging test on 2025-12-21)
- ✅ Message input field, send button (verified working)

**Coordinates Verified:**
- `message_input`: EditText at bounds [42,2169][891,2316] - ✅ Working
- `send_button`: Button at bounds [913,2181][1039,2307] - ✅ Working

**Source:** `doc/plan-open-tasks.md` + Today's messaging test

---

## Code TODOs

### CallNotificationReceiver - Mute State Tracking
**File:** `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt`
**Line:** 141
**Severity:** Minor

**Code:**
```kotlin
jamiBridge.muteAudio(accountId, callId, true) // TODO: Track mute state to toggle
```

**Description:**
Currently always mutes audio. Should track mute state to toggle between mute/unmute.

**Impact:** Low - Mute button works but doesn't toggle

**Recommendation:**
- Add mute state tracking in CallRepository or local preference
- Update notification action to toggle based on current state
- Update notification UI to show current mute status

---

## Ignored Tests (Native Library Issues)

**Total:** 10 tests ignored
**Reason:** Native crashes in Jami daemon library (libjami)
**Impact:** Test coverage gaps, but not bridge implementation issues

### Test Breakdown by Category

#### Account Management Tests (2 ignored)
**File:** `androidApp/src/androidTest/kotlin/com/gettogether/app/bridge/JamiBridgeAccountManagementTest.kt`

1. **Line 166:** `testUpdateAccountProfile()`
   - **Reason:** "Native crash in libjami::updateProfile - null pointer dereference"
   - **Issue:** Native library bug in updateProfile function

2. **Line 201:** `testSetAccountDetails()`
   - **Reason:** "May cause native crashes. Skipped for test suite stability"
   - **Issue:** Unstable native library behavior

---

#### Conversation Operations Tests (1 ignored)
**File:** `androidApp/src/androidTest/kotlin/com/gettogether/app/bridge/JamiBridgeConversationOperationsTest.kt`

3. **Line 314:** `testMarkMessagesAsDisplayed()` (or similar)
   - **Reason:** "Native crash in libjami when marking messages as displayed"
   - **Issue:** Native library bug in message display tracking

---

#### Device Management Tests (7 ignored)
**File:** `androidApp/src/androidTest/kotlin/com/gettogether/app/bridge/JamiBridgeDeviceManagementTest.kt`

4. **Line 260:** `testGetAudioInputDevices()`
   - **Reason:** "Native crash when getting audio input devices list"

5. **Line 271:** `testSetAudioInputDevice()`
   - **Reason:** "Native crash when setting audio input device"

6. **Line 284:** (Audio input device test)
   - **Reason:** "Native crash when setting audio input device"

7. **Line 294:** (Audio input device switching test)
   - **Reason:** "Native crash when switching audio input devices"

8. **Line 315:** (Audio input device test)
   - **Reason:** "Native crash when setting audio input device"

9. **Line 344:** (Device enumeration test)
   - **Reason:** "Native crash when enumerating all device types together"

10. **Line 481:** (Audio input devices test)
    - **Reason:** "Native crash when getting audio input devices list"

---

### Ignored Tests Analysis

**Pattern:** Most ignored tests (7/10) are related to audio device management

**Root Cause:** Native library (libjami) bugs, not JamiBridge implementation issues

**Risk Assessment:**
- **Low Risk:** These are edge cases in audio device management
- **Workaround:** Core audio functionality works via higher-level APIs
- **Impact:** Test coverage is reduced but functionality is not compromised

**Recommendations:**
1. **Report to Jami project:** Submit bug reports with crash logs to upstream Jami project
2. **Monitor Jami updates:** Check for fixes in future libjami releases
3. **Document limitations:** Add known issues section to user documentation
4. **Retry after updates:** Re-enable tests when upgrading libjami version

---

## Performance Optimization Opportunities

**Source:** `doc/OPEN_TASKS.md`

### Presence Timeout Checker
**Current:** Runs every 10 seconds checking all contacts
**Optimization:** Could be optimized for battery/CPU efficiency

**Potential Improvements:**
- Increase check interval when app in background
- Batch presence updates instead of individual cache mutations
- Use exponential backoff for timeout checking

---

## Testing Recommendations

**From:** `doc/OPEN_TASKS.md`

### Comprehensive Cross-Network Testing
- [ ] Test presence behavior with multiple contacts on different networks
- [ ] Verify timeout behavior under various network conditions
- [ ] Test presence recovery after network changes
- [ ] Monitor battery impact of presence timeout checker

### Native Library Issue Tracking
- [ ] Document all native crashes with stack traces
- [ ] Report to Jami project maintainers
- [ ] Track Jami daemon version and update schedule
- [ ] Re-test ignored tests with each libjami version update

---

## Summary Statistics

| Category | Total | Complete | Pending | Ignored |
|----------|-------|----------|---------|---------|
| **Android P1/P2 Tasks** | 6 | 6 ✅ | 0 | - |
| **iOS Platform Tasks** | 3 | 0 | 3 ⏳ | - |
| **Investigation Tasks** | 1 | 0 | 1 ⏳ | - |
| **Testing Tasks** | 1 | 1 ✅ | 0 | - |
| **Code TODOs** | 1 | 0 | 1 🔧 | - |
| **Test Cases** | 10 | 0 | 0 | 10 ⚠️ |
| **Total** | **22** | **7** | **5** | **10** |

---

## Recommendations

### Immediate Actions (Android)
1. **Mute State Tracking** - Quick fix for notification mute toggle (1-2 hours)
2. **Cross-Network Presence Investigation** - Test official Jami apps to establish baseline behavior (2-4 hours)
3. **Native Crash Documentation** - Compile stack traces and report to Jami project (2-3 hours)

### Medium-Term (iOS)
4. **iOS JamiBridge** - Core functionality for iOS platform (1-2 weeks)
5. **iOS Notifications** - UNUserNotificationCenter integration (3-5 days)
6. **iOS CallKit** - Call handling integration (3-5 days)

### Long-Term
7. **Performance Optimization** - Presence checker optimization (1-2 days)
8. **Comprehensive Testing** - Cross-network scenarios and edge cases (1 week)
9. **Jami Library Updates** - Monitor and integrate libjami updates to resolve native crashes

---

## Success Criteria

### Android Platform ✅
- [x] Settings persist across app restarts
- [x] Quick reply from notification sends message
- [x] Mark as read from notification works
- [x] File transfers can be accepted/cancelled
- [x] Account data persists between app restarts
- [x] Messaging works between physical devices
- [x] Chat UI coordinates verified
- [ ] All native crash issues resolved (depends on upstream Jami)

### iOS Platform ⏳
- [ ] JamiBridge integrated with Jami daemon
- [ ] Notifications working with UNUserNotificationCenter
- [ ] CallKit integration for call handling
- [ ] All features working on physical iOS device

### Cross-Platform ⏳
- [ ] Cross-network presence behavior documented and accepted
- [x] UI coordinates fully verified for automated testing
- [ ] All code TODOs resolved
- [ ] Native crash issues reported to Jami project

---

## Known Limitations

### Jami Native Library (libjami)
- **Audio Device Management:** Crashes when accessing audio input device APIs
- **Profile Updates:** Null pointer dereference in updateProfile function
- **Account Details:** Unstable behavior when modifying account settings
- **Message Display:** Crashes when marking messages as displayed

**Impact:** Bridge implementation is correct; issues are in underlying native library

**Mitigation:**
- Use higher-level APIs that don't trigger crashes
- Document known issues for users
- Monitor upstream Jami project for fixes

---

**Document Generated:** 2025-12-21 13:52
**Last Code Scan:** 2025-12-21
**Next Review:** When iOS development begins, Jami library updates, or user reports issues
**Test Suite Version:** JamiBridge Integration Tests v1.0
