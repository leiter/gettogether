# Open Tasks and Known Issues

## 1. Cross-Network Presence Detection

**Status:** Open
**Priority:** Medium
**Affected Components:** ContactRepositoryImpl, presence detection system

### Description
Online/offline status updates work differently depending on network configuration:

- **Same Network (Local/mDNS):** Status updates work automatically and reliably through mDNS (multicast DNS) broadcasts
- **Different Networks (DHT):** Status updates only occur when message activity happens. Contacts appear offline until they send a message, then are marked online.

### Current Behavior
1. When contacts are on the same local network, presence is detected via mDNS broadcasts
2. When contacts are on different networks, Jami uses DHT (Distributed Hash Table) for communication
3. DHT doesn't provide automatic presence updates - only message delivery
4. Activity-based presence is implemented: receiving a message marks sender as online
5. Presence timeout (60 seconds) marks contacts offline if no activity/updates received

### Root Cause
- Jami's DHT protocol doesn't include continuous presence broadcasting like mDNS does
- Cross-network presence detection requires active communication (sending/receiving messages)
- This is a protocol-level limitation, not a bug in the implementation

### Potential Solutions
1. **Accept current behavior:** This might be the expected Jami behavior for cross-network scenarios
2. **Implement periodic ping mechanism:** Send lightweight "ping" messages to check contact availability
   - Pros: Would provide more accurate online/offline status
   - Cons: Increased network traffic, battery usage, complexity
3. **Adjust timeout values:** Make cross-network timeout longer than local network timeout
   - Currently: 60 seconds for all presence
   - Could differentiate based on last detection method (mDNS vs activity)
4. **UI indication:** Show different states like "last seen X minutes ago" instead of binary online/offline

### Investigation Needed
- Verify if other Jami clients (official Android/iOS apps) have the same behavior
- Check if there's a Jami daemon API for active presence queries on DHT
- Determine user expectations: Is current behavior acceptable?

### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ContactRepositoryImpl.kt`
  - Lines 459-487: `updatePresenceFromActivity()` - Activity-based presence
  - Lines 493-528: `checkPresenceTimeouts()` - Timeout mechanism
  - Lines 344-363: `PresenceChanged` event handler
- `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ConversationRepositoryImpl.kt`
  - Lines 412-416: Message received triggers presence update

### Notes
- Activity-based presence detection was already implemented before recent changes
- Current 60-second timeout applies uniformly to all contacts
- No distinction between mDNS-detected and activity-detected presence

---

## 2. Avatar Feature Issues

**Status:** In Progress
**Priority:** High
**Affected Components:** ImagePicker, SettingsTab, Avatar display across app

### Description
Avatar feature implementation completed but experiencing issues during testing. App no longer crashes when clicking Settings, but functionality needs verification.

### Implementation Status
- ✅ Image picker (Android) with gallery selection
- ✅ Image processor with EXIF correction, resize to 256x256px, ~100KB compression
- ✅ AvatarImage and ContactAvatarImage reusable components
- ✅ Settings UI with avatar selection dialog
- ✅ Avatar display in Contacts tab
- ✅ Avatar display in Conversations tab
- ✅ Fixed crash: "LifecycleOwner attempting to register while RESUMED"
  - Solution: Used `rememberLauncherForActivityResult` instead of constructor registration

### Known Issues
**User reported: "Doesn't crash anymore but still has issues"**

Specific issues to investigate:
- [ ] Test image selection from gallery
- [ ] Verify image compression to ~100KB works correctly
- [ ] Test avatar upload to Jami daemon
- [ ] Verify avatar displays in Settings profile section
- [ ] Verify avatar displays in Contacts list
- [ ] Verify avatar displays in Conversations list
- [ ] Test "Remove avatar" functionality
- [ ] Test cancel picker behavior
- [ ] Test with various image sizes and formats
- [ ] Verify persistent URI permissions work correctly

### Potential Issues
1. **Image picker may not launch:** Permissions issue or launcher not properly initialized
2. **Image not processing:** ImageProcessor may fail on certain image formats/sizes
3. **Avatar not uploading:** Jami daemon updateProfile() may crash (known issue with fallback)
4. **Avatar not displaying:** Coil image loading may fail, URI access issues
5. **Avatar not persisting:** Database may not store avatarUri correctly

### Related Files
- `shared/src/commonMain/kotlin/com/gettogether/app/platform/ImagePicker.kt`
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImagePicker.android.kt` (lines 20-62)
- `shared/src/androidMain/kotlin/com/gettogether/app/platform/ImageProcessor.android.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/components/AvatarImage.kt`
- `shared/src/commonMain/kotlin/com/gettogether/app/ui/screens/home/SettingsTab.kt` (lines 110-135, 612-736)
- `shared/src/commonMain/kotlin/com/gettogether/app/presentation/viewmodel/SettingsViewModel.kt` (updateProfileWithAvatar method)

### Testing Needed
1. **Manual Testing:**
   - Open Settings
   - Click edit profile button
   - Click avatar edit button
   - Select image from gallery
   - Verify preview shows selected image
   - Click confirm
   - Verify avatar appears in Settings
   - Navigate to Contacts - verify avatar shows there
   - Navigate to Conversations - verify avatar shows there

2. **Error Scenarios:**
   - Cancel image picker (should not crash)
   - Select very large image (>10MB)
   - Select unsupported format
   - Test with no storage permission
   - Test remove avatar button

### Next Steps
1. Get specific error details from user about what isn't working
2. Check logcat for errors during avatar selection/upload
3. Add debug logging to ImagePicker and ImageProcessor
4. Test on both devices (Pixel 2 and Pixel 7a)

---

## Future Tasks

### Performance Optimization
- Monitor presence timeout checker running every 10 seconds - could be optimized
- Consider batch presence updates instead of individual cache mutations

### Testing
- Need comprehensive testing of cross-network scenarios
- Test presence behavior with multiple contacts on different networks
- Verify timeout behavior under various network conditions

---

**Last Updated:** 2025-12-21 16:00
**Document Created By:** Claude Code
