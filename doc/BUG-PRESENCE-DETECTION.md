# Bug Report: Contact Presence Detection Showing False Online Status

**Date Reported:** 2026-01-18
**Date Resolved:** 2026-01-18
**Severity:** Medium
**Component:** Contact Presence Detection
**Status:** ✅ **RESOLVED** - See [PRESENCE-BROADCASTING-IMPLEMENTATION.md](./PRESENCE-BROADCASTING-IMPLEMENTATION.md)

---

## ✅ RESOLUTION (2026-01-18)

**This issue has been fully resolved by implementing contact presence polling.**

### What Was Fixed

1. **Implemented presence polling**: Periodic unsubscribe/resubscribe forces fresh DHT queries every 60 seconds
2. **Optimized for efficiency**: Only polls contacts currently marked as ONLINE
3. **Extended timeout**: Increased to 90 seconds to allow polling to refresh first
4. **Timeout as fallback**: Now serves its proper purpose (safety net when polling fails)

### Implementation

See comprehensive documentation:
→ **[PRESENCE-POLLING-SOLUTION.md](./PRESENCE-POLLING-SOLUTION.md)** ← **WORKING SOLUTION**

### Failed Approaches

Before finding the working solution, we attempted:
- **Presence broadcasting** via `JamiService.publish()` - Daemon limitation, doesn't trigger network events
- See: [PRESENCE-BROADCASTING-IMPLEMENTATION.md](./PRESENCE-BROADCASTING-IMPLEMENTATION.md) (superseded)

### Result

- **Before**: Contacts showed stale cached "online" status, timeout was only correction mechanism
- **After**: Contacts refreshed every 60 seconds via polling, timeout is fallback
- **Impact**: Presence now reflects actual online/offline status with 60-second refresh rate
- **Overhead**: Minimal (~0.1-1 second per cycle, only for online contacts)

---

## Original Bug Report (Historical)

## Problem Summary

Contacts are showing as "online" when they're actually offline. When the user performs a pull-to-refresh:
1. Offline contacts briefly show as online
2. After ~60 seconds, they correctly show as offline again
3. Refreshing again causes the cycle to repeat

---

## Root Cause Analysis

### The Bug Sequence

1. **User triggers pull-to-refresh**
   - `ContactsViewModel.refresh()` is called
   - `ContactRepository.refreshContacts(accountId)` is invoked

2. **Presence subscription happens** (ContactRepositoryImpl.kt:300-310)
   ```kotlin
   contacts.forEach { contact ->
       jamiBridge.subscribeBuddy(accountId, contact.uri, true)
   }
   ```

3. **Native daemon responds with STALE cached data**
   - When `subscribeBuddy()` is called, the Jami daemon immediately fires a presence notification
   - This notification contains **cached/stale** presence information from previous sessions
   - The daemon doesn't validate if the contact is actually online RIGHT NOW

4. **App treats stale data as fresh** (SwigJamiBridge.kt:252-269)
   ```kotlin
   override fun newBuddyNotification(accountId: String?, buddyUri: String?,
                                      status: Int, lineStatus: String?) {
       val isOnline = status > 0  // ← Blindly trusts the status
       val event = JamiContactEvent.PresenceChanged(accountId, buddyUri, isOnline)
       _contactEvents.tryEmit(event)
   }
   ```

5. **Repository updates presence** (ContactRepositoryImpl.kt:377-397)
   - Updates `_onlineStatusCache` with the stale "online" status
   - Updates `_lastPresenceTimestamp` to current time (resetting the timeout)
   - Contact now shows as online in UI

6. **Timeout eventually corrects it**
   - After 60 seconds of no real presence updates (`checkPresenceTimeouts()`)
   - Contact is marked offline
   - But this is a slow, delayed correction

7. **Refresh repeats the cycle**
   - User refreshes → subscribeBuddy called again → stale cache → shows online → timeout → offline

---

## Evidence

### Code Locations

| Component | File | Lines |
|-----------|------|-------|
| **Refresh trigger** | `ContactRepositoryImpl.kt` | 300-310 |
| **Presence callback** | `SwigJamiBridge.kt` | 252-269 |
| **Presence handler** | `ContactRepositoryImpl.kt` | 377-397 |
| **Timeout mechanism** | `ContactRepositoryImpl.kt` | 590-625 |

### Key Constants

- **PRESENCE_TIMEOUT_MS**: 60,000ms (60 seconds) - ContactRepositoryImpl.kt:47
- **Timeout check interval**: 10,000ms (10 seconds) - ContactRepositoryImpl.kt:590

### Native Layer Issue

The problem originates in the Jami native daemon's behavior:
- `JamiService.subscribeBuddy()` triggers an immediate presence notification
- This notification reflects the daemon's **internal cache**, not a fresh network check
- The cache can contain presence from hours/days ago when the contact was last online

---

## Impact

### User Experience

- **Confusing status indicators**: Users see contacts as online when they're not
- **Trust issues**: Users may try to message/call "online" contacts who can't respond
- **Visual inconsistency**: Status flickers between online/offline on refresh

### Technical Debt

- **Timeout workaround**: Relying on 60-second timeout is a band-aid fix
- **Network waste**: Unnecessary presence subscriptions on every refresh
- **State inconsistency**: Online status doesn't reflect reality for up to 60 seconds

---

## Proposed Solutions

### Option 1: Ignore Initial Presence on Subscribe (RECOMMENDED)

Add a flag to distinguish between "subscribe response" and "real presence update":

**Pros:**
- Minimal code changes
- Preserves existing timeout logic
- No breaking changes

**Cons:**
- Contacts won't show online immediately even if they genuinely are
- May need to wait for first real presence broadcast

**Implementation:**
- Add timestamp tracking for subscribe operations
- Ignore presence updates that arrive within 2 seconds of subscribe
- Or use a separate "subscription in progress" cache

---

### Option 2: Clear Online Status Before Refresh

Reset all contacts to offline before re-subscribing:

**Pros:**
- Simple implementation
- Avoids stale data propagation
- User sees gradual "coming online" rather than false positives

**Cons:**
- Brief flicker where all contacts show offline
- User might think contacts disconnected

**Implementation:**
```kotlin
// In refreshContacts(), before subscribing:
_onlineStatusCache.value = emptyMap()
_lastPresenceTimestamp.value = emptyMap()

// Then subscribe to all contacts
contacts.forEach { contact ->
    jamiBridge.subscribeBuddy(accountId, contact.uri, true)
}
```

---

### Option 3: Add Presence Validation Time Window

Only accept presence updates if they're "fresh enough":

**Pros:**
- Balances responsiveness with accuracy
- Works with daemon's caching behavior

**Cons:**
- More complex logic
- Requires estimating "freshness" window

**Implementation:**
- Add a "presence update threshold" (e.g., 5 seconds)
- If presence update arrives within threshold of subscribe, mark as "tentative"
- Only mark as "confirmed online" after receiving a second update

---

### Option 4: Unsubscribe-Then-Subscribe Pattern

Force the daemon to clear its cache:

**Pros:**
- Forces fresh presence checks
- Might reduce daemon's cache pollution

**Cons:**
- Adds complexity
- More native calls
- May not actually clear daemon cache

**Implementation:**
```kotlin
// Unsubscribe first
contacts.forEach { contact ->
    jamiBridge.subscribeBuddy(accountId, contact.uri, false)
}
delay(500) // Let daemon process
// Then subscribe
contacts.forEach { contact ->
    jamiBridge.subscribeBuddy(accountId, contact.uri, true)
}
```

---

### Option 5: Native Layer Fix (Long-term)

Fix the Jami daemon to not send stale presence on subscribe:

**Pros:**
- Solves root cause
- Benefits all Jami applications

**Cons:**
- Requires native code changes
- Outside our control
- Long development cycle

**Implementation:**
- Report issue to Jami upstream
- Modify daemon to send "presence unknown" on subscribe
- Wait for real presence broadcasts

---

## Recommended Action Plan

### Phase 1: Immediate Mitigation (Option 2)
1. Implement "clear status before refresh" logic
2. Add logging to track presence update sources
3. Test with multiple contacts

### Phase 2: Better Solution (Option 1)
1. Add subscribe timestamp tracking
2. Implement "ignore immediate presence" logic
3. Tune the ignore window (2-5 seconds)

### Phase 3: Long-term (Option 5)
1. Document the issue for Jami upstream
2. Consider contributing a patch
3. Monitor Jami releases for fixes

---

## Testing Checklist

- [ ] Verify presence shows correctly when contact genuinely online
- [ ] Verify offline contacts stay offline after refresh
- [ ] Verify timeout still works (contacts go offline after 60s)
- [ ] Test with multiple contacts (some online, some offline)
- [ ] Test on same network (mDNS) vs different networks (DHT)
- [ ] Verify pull-to-refresh doesn't cause flicker

---

## Additional Notes

### Related Components

- **JamiContactEvent.PresenceChanged** - Event definition (JamiBridge.kt:872-916)
- **PresenceCallback** - Native callback interface
- **getContacts() Flow** - Applies online status to contact objects

### Presence Detection Methods

1. **mDNS**: Same local network detection
2. **DHT**: Cross-network via distributed hash table
3. **Direct TCP/UDP**: When both devices are reachable

The bug affects all three methods equally since it's a caching issue at the daemon level.

---

## Logs to Monitor

When testing fixes, watch for these logs:

```bash
# Subscribe operations
adb logcat -s "SwigJamiBridge" | grep "subscribeBuddy"

# Presence callbacks
adb logcat -s "SwigJamiBridge" | grep "newBuddyNotification"

# Presence events
adb logcat | grep "PresenceChanged"

# Timeout operations
adb logcat | grep "Presence timeout"
```

---

## Conclusion

The bug is caused by the Jami daemon sending **stale cached presence data** when `subscribeBuddy()` is called. The app currently treats this stale data as fresh, causing incorrect "online" status.

The quickest fix is **Option 2** (clear status before refresh), but **Option 1** (ignore initial presence) is the most robust long-term solution.

A native layer fix (Option 5) would be ideal but requires upstream cooperation.
