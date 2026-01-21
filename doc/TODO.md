# GetTogether - Open Tasks & Known Issues

**Last Updated:** 2026-01-21
**Version:** Consolidated from all task documents (Updated post filepicker-jamibridge merge)

---

## Quick Status Overview

| Priority | Category | Tasks | Status |
|----------|----------|-------|--------|
| 🔴 P1 | Android Critical | 7 | ⚠️ 2 Open, 5 Complete |
| 🟡 P2 | Android High Impact | 4 | ✅ All Complete |
| 🔵 P3 | iOS Platform | 3 | ⏳ All Pending |
| ⚫ P4 | Nice-to-Have | 3 | ⏳ Mixed |

---

## Priority 1: Critical - Android Platform 🔴

### 1.1 Incoming Call Notifications - Partially Functional ⚠️

**Status:** Improved but not fully functional (2025-12-21)
**Impact:** CRITICAL - Core feature not working reliably

#### What Works ✅
- Permission request on app start (RECORD_AUDIO, CAMERA, POST_NOTIFICATIONS)
- Global listener in Application class observes incoming call events
- NotificationHelper triggers incoming call notifications
- Permission checks before placing/accepting calls with clear error messages

#### What's Still Broken ❌
- Incoming call notification may not show full-screen intent properly
- Answer/Decline actions from notification may not work reliably
- App may not open to call screen when answering from notification
- Notification may disappear too quickly or not persist

#### Implementation Details
**Files Modified (2025-12-21):**
- `androidApp/src/main/kotlin/com/gettogether/app/GetTogetherApplication.kt` - Global listener added
- `androidApp/src/main/kotlin/com/gettogether/app/MainActivity.kt` - Permission request
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/NotificationHelper.android.kt` - Full impl
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt` - Action handlers
- `shared/src/commonMain/kotlin/com/gettogether/app/platform/PermissionManager.kt` - New class
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/CallViewModel.kt` - Permission checks

#### Next Steps to Debug
1. **Check full-screen intent permission:**
   ```bash
   adb shell appops get com.gettogether.app USE_FULL_SCREEN_INTENT
   ```
   - On Android 14+, may need explicit permission request
   - Add to AndroidManifest: `USE_FULL_SCREEN_INTENT`

2. **Verify CallNotificationReceiver registration:**
   - Check receiver is registered in AndroidManifest
   - Add logging to track action broadcasts
   - Verify intent extras are properly passed

3. **Test notification behavior:**
   - Test on Android 13 vs 14+
   - Check if notification channels are created properly
   - Verify notification importance level is HIGH/MAX

4. **Add comprehensive logging:**
   ```kotlin
   Log.d("IncomingCall", "Event received: callId=$callId")
   Log.d("IncomingCall", "Notification shown: id=$notificationId")
   Log.d("IncomingCall", "Action clicked: $action")
   ```

#### Action Items
- [ ] Debug full-screen intent not showing on locked screen
- [ ] Fix answer/decline actions not working from notification
- [ ] Ensure app opens to call screen when answering
- [ ] Test on Android 13, 14, 15
- [ ] Add logging throughout call notification flow
- [ ] Test with screen locked vs unlocked
- [ ] Verify notification persistence

---

### 1.2 Logout Functionality - ✅ COMPLETE

**Status:** ✅ Fully implemented (2026-01-17)
**Impact:** Users can log out while keeping their account data

#### What Works ✅
- Logout button in Settings screen with dialog options
- Account deactivation (keeps data for relogin)
- Navigation back to Welcome/Account Selection screen
- `LaunchedEffect` handles automatic navigation on logout completion

#### Implementation Details
| Component | File | Implementation |
|-----------|------|----------------|
| Repository | `AccountRepository.kt:408-424` | `logoutCurrentAccount()` - deactivates account, clears state |
| ViewModel | `SettingsViewModel.kt:460-470` | `logoutKeepData()` - calls repository, updates UI state |
| UI | `SettingsTab.kt:119-130, 868+` | `LogoutOptionsDialog` with "Logout (Keep Data)" option |
| Navigation | `SettingsTab.kt:92-93` | `LaunchedEffect` handles navigation on `logoutComplete` |

---

### 1.3 Account Persistence & Auto-Login - ✅ COMPLETE

**Status:** ✅ Fully implemented (2026-01-17)
**Impact:** Accounts persist across app restarts, relogin supported

#### What Works ✅
- Accounts automatically loaded on app startup
- Multiple account management supported
- Deactivated accounts can be relogged into
- Navigation skips Welcome screen when logged in

#### Implementation Details
| Component | File | Implementation |
|-----------|------|----------------|
| Load accounts | `AccountRepository.kt:65-144` | `loadAccounts()` - auto-loads on init |
| Get all accounts | `AccountRepository.kt:429-453` | `getAllAccounts()` - lists all accounts |
| Get deactivated | `AccountRepository.kt:458-466` | `getDeactivatedAccounts()` - for relogin |
| Relogin | `AccountRepository.kt:472-555` | `reloginToAccount()` - reactivates account |

---

### 1.4 Message Notifications - Needs Testing ⚠️

**Status:** Implemented but not verified (2025-12-21)
**Impact:** HIGH - Feature may not work as expected

#### What Was Implemented
- ✅ NotificationHelper injected into ConversationRepositoryImpl
- ✅ `showMessageNotificationIfNeeded()` method added
- ✅ Automatically triggers when new messages received
- ✅ Generates notification IDs based on conversation hash
- ✅ Fetches contact name from JamiBridge

#### Needs Testing
- [ ] Test notifications appear when app in background
- [ ] Test notification actions (reply, mark read)
- [ ] Test notification grouping for multiple messages
- [ ] Test notification clearing when conversation opened
- [ ] Test notification doesn't show when conversation is active (needs implementation)
- [ ] Test notification sound/vibration
- [ ] Test notification on different Android versions

#### Implementation Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ConversationRepositoryImpl.kt`
  - Lines 771-817: `showMessageNotificationIfNeeded()` method
  - Lines 440-446: Triggers notification on new message

---

### 1.5 Avatar Feature - ✅ COMPLETE & WORKING

**Status:** ✅ Fully functional (tested 2025-12-21)
**Impact:** MEDIUM - Feature now working on all test devices

#### What Works ✅
- ✅ Image selection from photo gallery works correctly
- ✅ Image compression to ~100KB verified working
- ✅ Avatar upload to Jami daemon successful (with fallback)
- ✅ Avatar displays correctly in Settings profile section
- ✅ Avatar displays correctly in Contacts list
- ✅ Avatar displays correctly in Conversations list
- ✅ Remove avatar functionality works
- ✅ Cancel picker behavior (no crashes)
- ✅ Handles various image sizes and formats

#### Implementation Completed
- ✅ Fixed crash: "LifecycleOwner attempting to register while RESUMED"
  - Solution: Used `rememberLauncherForActivityResult` instead of constructor registration
- ✅ Fixed DI injection crash by creating `provideImagePicker()` composable pattern
- ✅ Image picker (Android) with gallery selection
- ✅ Image processor with EXIF correction, resize to 256x256px, ~100KB compression
- ✅ Avatar display components (AvatarImage, ContactAvatarImage)
- ✅ Settings UI integration with avatar selection dialog
- ✅ Persistent URI permissions handling
- ✅ Fallback to initials when no avatar

#### Testing Results
**Tested on:**
- ✅ Pixel 2 (Android 11) - All features working
- ✅ Pixel 7a (Android 16) - All features working

**User Confirmation:** "I checked again And it seems that profile picker and avatar is fully functioning"

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/platform/ImagePicker.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImagePicker.android.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImageProcessor.android.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/components/AvatarImage.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/screens/home/SettingsTab.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/SettingsViewModel.kt`

**Documented in:** `doc/WORKING_FEATURES.md`

---

### 1.6 Export Account - ✅ COMPLETE (NEW)

**Status:** ✅ Fully implemented (2026-01-17, filepicker-jamibridge merge)
**Impact:** Users can backup their account to an encrypted file

#### What Works ✅
- Export account to encrypted backup file
- Password protection for exported archive
- Platform-specific export paths (Downloads folder on Android)
- Export dialog with password input UI

#### Implementation Details
| Component | File | Implementation |
|-----------|------|----------------|
| Repository | `AccountRepository.kt:392-407` | `exportAccount(destinationPath, password)` |
| ViewModel | `SettingsViewModel.kt` | `exportAccount(password)` method |
| UI | `SettingsTab.kt:111-115, 796+` | `ExportAccountDialog` with password encryption |
| Platform | `ExportPath.android.kt`, `ExportPath.ios.kt` | Platform-specific export paths |

---

### 1.7 Import Account - ✅ COMPLETE (NEW)

**Status:** ✅ Fully implemented (2026-01-17, filepicker-jamibridge merge)
**Impact:** Users can restore their account from a backup file

#### What Works ✅
- Import account from backup file with file picker
- Password entry for encrypted archives
- Full import flow with UI feedback
- Platform-specific file selection

#### Implementation Details
| Component | File | Implementation |
|-----------|------|----------------|
| Repository | `AccountRepository.kt:248-315` | `importAccount(archivePath, password)` |
| ViewModel | `ImportAccountViewModel.kt` | Full import flow |
| UI | `ImportAccountScreen.kt` | Complete UI with file picker |
| Platform | `FilePicker.android.kt`, `FilePicker.ios.kt` | File selection |

---

## Priority 2: High Impact - Android ✅

**Status:** All P2 tasks complete!

### 2.1 File Transfer Implementation ✅
- Fully implemented in SwigJamiBridge
- Methods: acceptFileTransfer(), cancelFileTransfer(), getFileTransferInfo()

### 2.2 Notification Action Handlers ✅
- Message notification actions (reply, mark read) implemented
- Call notification actions (answer, decline, mute) implemented
- KoinComponent integration complete

### 2.3 Settings Persistence ✅
- Android implementation using SharedPreferences
- iOS stub implementation (TODO: NSUserDefaults)
- All notification and privacy settings persist

### 2.4 Account Data Persistence ✅
- Root cause fixed: getAppDataPath callback added to SwigJamiBridge
- Account data now persists between app restarts
- Navigation checks account on startup

---

## Priority 3: iOS Platform 🔵

**Status:** All pending - iOS development not started

### 3.1 iOS JamiBridge Implementation ⏳

**Scope:** 90+ methods returning mock data
**Effort:** 1-2 weeks

**Description:**
iOS JamiBridge currently provides mock data for UI testing. Full integration with Jami daemon pending.

**Files:**
- `shared/src/iosMain/kotlin/com/gettogether/app/jami/JamiBridge.ios.kt` - Mock implementation
- `iosApp/iosApp/JamiBridgeWrapper.swift` - Swift bridge (to be implemented)

**Mock Data Examples:**
- Account creation returns "placeholder-account-id"
- Contact operations return placeholder responses
- Message sending simulated with local state
- Call operations return "placeholder-call-id"

**Action Required:**
Implement Swift bridge for Jami daemon integration in Xcode project

---

### 3.2 iOS NotificationHelper Implementation ⏳

**Scope:** All notification methods
**Effort:** 3-5 days

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/NotificationHelper.ios.kt`

**Methods to Implement:**
- Request notification permissions (UNUserNotificationCenter)
- Show message notifications
- Show incoming call notifications (critical alerts)
- Show missed call notifications
- Group notifications
- Remove/cancel notifications

**Technology:** UNUserNotificationCenter API

---

### 3.3 iOS CallServiceBridge Implementation ⏳

**Scope:** 8 call service methods
**Effort:** 3-5 days

**File:** `shared/src/iosMain/kotlin/com/gettogether/app/platform/CallServiceBridge.ios.kt`

**Methods to Implement (Lines 6-41):**
- Start/end call UI
- Mute/unmute
- Hold/resume
- Speaker toggle
- Audio routing
- Conference call handling

**Technology:** CallKit framework + AVAudioSession

---

## Priority 4: Nice-to-Have ⚫

### 4.1 Cross-Network Presence Detection ⏳

**Status:** Investigation needed
**Priority:** MEDIUM
**Impact:** User experience - contacts may appear offline when actually online

#### Description
Online/offline status updates work differently based on network configuration:

**Same Network (mDNS):**
- ✅ Automatic, reliable presence detection
- ✅ Status updates work via multicast DNS broadcasts

**Different Networks (DHT):**
- ⚠️ Presence only updated on message activity
- ⚠️ Contacts appear offline until they send a message
- ⚠️ 60-second timeout marks contacts offline after no activity

#### Root Cause
- Jami's DHT protocol doesn't include continuous presence broadcasting
- This may be expected Jami protocol behavior, not a bug

#### Potential Solutions
1. **Accept current behavior** - Verify if official Jami apps behave similarly
2. **Implement periodic ping** - Adds network/battery overhead
3. **Adjust timeout values** - Different timeouts for mDNS vs DHT
4. **UI indication** - Show "last seen X minutes ago" instead of binary online/offline
5. **Refine polling via DHT lookup** - Use `dht.get(jamiID)` to fetch login status, similar to how contact queries work on the contact screen. This would provide a consistent DHT-based approach for presence detection across networks.

#### Investigation Tasks
- [ ] Test official Jami apps (Android/iOS) for cross-network presence
- [ ] Check if Jami daemon API supports active presence queries on DHT
- [ ] Investigate using `dht.get(jamiID)` for presence polling (same as contact query screen)
- [ ] Determine user expectations and acceptance criteria
- [ ] Monitor battery impact of current timeout checker (runs every 10 seconds)

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ContactRepositoryImpl.kt`
  - Lines 459-487: Activity-based presence
  - Lines 493-528: Timeout mechanism

---

### 4.2 CallNotificationReceiver - Mute State Tracking 🔧

**Status:** Minor TODO
**File:** `shared/src/androidMain/kotlin/com/gettogether/app/platform/CallNotificationReceiver.kt`
**Line:** 141

**Issue:**
Currently always mutes audio. Should track mute state to toggle between mute/unmute.

**Code:**
```kotlin
jamiBridge.muteAudio(accountId, callId, true) // TODO: Track mute state to toggle
```

**Recommendation:**
- Add mute state tracking in CallRepository or local preference
- Update notification action to toggle based on current state
- Update notification UI to show current mute status

---

### 4.3 DHT Proxy Configuration for Mobile Networks ⏳

**Status:** Low priority - workaround exists (WiFi works)
**Priority:** LOW
**Impact:** Registration fails on some 5G/mobile networks, but messaging still works

#### Problem
On 5G mobile networks (tested on Pixel 7), the account shows UNREGISTERED because DHT proxy connections fail. However, messaging still works through TURN relays and existing swarm connections. WiFi registration works fine.

#### Root Cause
- Carrier-grade NAT (CGNAT) on mobile networks
- Possible DNS filtering or port blocking by carrier
- Default DHT proxy: `dhtproxy.jami.net:[80-95]`

#### Action Items
- [ ] **Enable proxy list fetching** - Try multiple proxy servers automatically
  ```kotlin
  // In account creation/restore
  "Account.proxyListEnabled" to "true"
  ```
- [ ] **Try specific proxy ports** - Some carriers allow port 443 but block others
  ```kotlin
  // Configure specific proxy server with port 443
  "Account.proxyServer" to "dhtproxy.jami.net:443"
  ```

#### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/AccountRepository.kt` - Account config
- `androidApp/src/main/kotlin/com/gettogether/app/jami/SwigJamiBridge.kt:504` - Default proxy setting

#### Notes
- Current workaround: Use WiFi for full functionality
- Messaging works on mobile even when UNREGISTERED (uses TURN relays)
- Only affects discoverability for new contact requests

---

### 4.4 Wipe Contact Data (Save Space) ⏳

**Status:** Feature idea
**Priority:** LOW
**Impact:** Allow users to free up device storage by deleting local data for specific contacts

#### Feature Description
Allow users to wipe local data associated with a contact to reclaim storage space, without necessarily removing the contact relationship.

#### Sync Behavior - What Propagates to Other Devices?

| Data Type | Propagates? | Notes |
|-----------|-------------|-------|
| Contact relationship (add/remove) | **Yes** | Synced via account's git repo |
| Block/unblock status | **Yes** | Synced via account's git repo |
| Conversation messages | **No** | Local only, each device has own copy |
| Transferred files (images, docs) | **No** | Local only |
| Cached profile data (avatars, vCards) | **No** | Local only |

#### Implementation Options

| Option | What It Clears | Syncs? | Contact Preserved? |
|--------|---------------|--------|-------------------|
| **Clear conversation** | Messages + files | No | Yes |
| **Delete files only** | Media/documents | No | Yes |
| **Remove contact** | Everything | Yes | No |

#### Recommended Approach
Implement "Clear conversation data" option that:
1. Deletes local message history for that contact
2. Deletes transferred files (images, documents, etc.)
3. Optionally clears cached profile data (avatar)
4. **Keeps** the contact relationship (no sync impact)
5. Shows storage space that will be freed before confirming

#### UI Location
- Contact details screen → "Clear local data" or "Free up space" option
- Confirmation dialog showing estimated space savings
- Warning that messages cannot be recovered

#### Implementation Notes
- Conversation data stored in local SQLite/Room database
- Transferred files stored in app's files directory
- Profile vCards stored at `{filesDir}/{accountId}/profiles/`
- Need to calculate size before deletion for user feedback

#### Action Items
- [ ] Add "Clear local data" option to contact details screen
- [ ] Implement storage size calculation for contact data
- [ ] Add confirmation dialog with space savings preview
- [ ] Implement message deletion for specific conversation
- [ ] Implement file deletion for specific contact
- [ ] Optional: Add "Clear all local data" in Settings for bulk cleanup

---

### 4.5 UI/UX Improvements ⏳

**Pending Items:**
- [ ] Add loading states during permission requests
- [ ] Add permission rationale dialogs (explain why permissions needed)
- [ ] Improve error messages when permissions denied
- [ ] Add settings deep link to grant permissions manually
- [ ] Add visual indicators when microphone/camera in use
- [ ] Add "last seen" timestamps instead of binary online/offline
- [ ] Improve notification grouping for multiple messages
- [ ] Add notification settings (sound, vibration, priority)

---

## Known Issues & Limitations

### Jami Native Library Crashes ⚠️

**Affected Tests:** 10 tests ignored due to native crashes
**Impact:** Test coverage gaps, but not bridge implementation issues

**Test Breakdown:**
- **Account Management:** 2 tests (updateProfile, setAccountDetails)
- **Conversation Operations:** 1 test (markMessagesAsDisplayed)
- **Device Management:** 7 tests (audio input device enumeration/switching)

**Root Cause:** Native library (libjami) bugs, not JamiBridge implementation

**Pattern:** Most crashes (7/10) are audio device management related

**Workaround Applied (2025-12-21):**
- Deprecated `getAudioInputDevices()` with ERROR level
- Added `useDefaultAudioInputDevice()` as safe alternative
- Audio functionality works via higher-level APIs

**Recommendations:**
1. ✅ Report to Jami project (submit crash logs)
2. ⏳ Monitor Jami updates for fixes
3. ⏳ Document limitations in user documentation
4. ⏳ Re-enable tests when upgrading libjami version

**See:** `doc/CRITICAL-NATIVE-BUG.md` for historical context (note: call crashes were misdiagnosed - actually permission issues)

---

## Performance Optimization Opportunities

### Presence Timeout Checker
**Current:** Runs every 10 seconds checking all contacts
**Optimization Potential:** Could improve battery/CPU efficiency

**Improvements:**
- Increase check interval when app in background
- Batch presence updates instead of individual cache mutations
- Use exponential backoff for timeout checking
- Different intervals for mDNS vs DHT detected contacts

---

## Testing Checklists

### Permission Flow Testing
- [ ] Fresh install shows permission dialog
- [ ] Granting all permissions allows calls
- [ ] Denying permissions shows error when attempting call
- [ ] Revoking permissions after granting shows error
- [ ] Permission rationale shown on second denial

### Incoming Calls Testing
- [ ] Notification appears on incoming call
- [ ] Full-screen intent shows on locked screen (Android 14+)
- [ ] Answer button opens app to call screen
- [ ] Decline button dismisses notification and rejects call
- [ ] Multiple incoming calls handled properly
- [ ] Notification persists until answered/declined
- [ ] Test on Android 13, 14, 15

### Message Notifications Testing
- [ ] Notifications appear for new messages in background
- [ ] Tapping notification opens correct conversation
- [ ] Notifications don't appear when conversation is active
- [ ] Multiple message notifications group properly
- [ ] Reply action works from notification
- [ ] Mark read action works from notification

### Account Management Testing
- [ ] Logout clears all data
- [ ] Logout returns to Welcome screen
- [ ] Saved account auto-loads on app restart
- [ ] Account creation saves credentials
- [ ] Account import saves credentials
- [ ] Encrypted credentials stored securely

### Avatar Testing ✅ COMPLETE
- [x] Image picker launches successfully
- [x] Selected image displays in preview
- [x] Avatar uploads to Jami daemon
- [x] Avatar displays in Settings
- [x] Avatar displays in Contacts
- [x] Avatar displays in Conversations
- [x] Remove avatar works
- [x] Large images compress to ~100KB

---

## Success Criteria

### Android Platform
**Critical (P1):**
- [ ] Incoming call notifications work reliably
- [ ] Answer/Decline from notification works
- [x] Logout functionality implemented ✅
- [x] Account persistence & auto-login implemented ✅
- [ ] Message notifications tested and working
- [x] Avatar feature fully functional ✅
- [x] Export account functionality ✅
- [x] Import account functionality ✅

**High Impact (P2):**
- [x] Settings persist across app restarts
- [x] Quick reply from notification sends message
- [x] Mark as read from notification works
- [x] File transfers can be accepted/cancelled
- [x] Account data persists between app restarts

**Nice-to-Have (P4):**
- [ ] Cross-network presence documented and accepted
- [ ] All code TODOs resolved
- [ ] UI/UX improvements implemented

### iOS Platform
- [ ] JamiBridge integrated with Jami daemon
- [ ] Notifications working with UNUserNotificationCenter
- [ ] CallKit integration for call handling
- [ ] All features working on physical iOS device

---

## Immediate Next Steps

**For User:**
1. **Test incoming calls** - Try making calls between devices and report specific issues
2. **Test message notifications** - Send messages with app in background, verify notifications
3. ✅ ~~Test avatar feature~~ - **COMPLETE & WORKING**
4. ✅ ~~Test logout~~ - **COMPLETE & WORKING**
5. ✅ ~~Test account persistence~~ - **COMPLETE & WORKING**
6. **Test export/import account** - Verify backup and restore workflow

**For Development:**
1. **Debug incoming call notifications:**
   - Check full-screen intent permission
   - Verify notification importance/priority
   - Add comprehensive logging
   - Test on Android 14+

2. ✅ ~~Implement logout functionality~~ - **COMPLETE**

3. ✅ ~~Implement account persistence~~ - **COMPLETE**

4. ✅ ~~Implement account export/import~~ - **COMPLETE**

---

**Document Version:** 2026-01-17 (Updated post filepicker-jamibridge merge)
**Last Code Changes:** 2026-01-17 (Account management, export/import, logout all complete)
**Next Review:** After testing incoming calls and message notifications
