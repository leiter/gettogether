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

## Future Tasks

### Performance Optimization
- Monitor presence timeout checker running every 10 seconds - could be optimized
- Consider batch presence updates instead of individual cache mutations

### Testing
- Need comprehensive testing of cross-network scenarios
- Test presence behavior with multiple contacts on different networks
- Verify timeout behavior under various network conditions

---

**Last Updated:** 2025-12-21
**Document Created By:** Claude Code
