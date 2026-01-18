# Presence Broadcasting Inconsistency

**Date:** 2026-01-18
**Status:** Partially Working (Inconsistent)

## Summary

Presence broadcasting via `PresenceManager` is implemented and running, but Jami daemon cannot reliably parse the presence status. This causes inconsistent online/offline detection that relies primarily on polling rather than real-time presence updates.

## Current Behavior

### What Works ✅
- Initial presence detection: Contacts come online within ~3 seconds of app start
- Polling-based refresh: Every 60 seconds, contacts get re-polled and status updated
- Timeout mechanism: Contacts marked offline 90 seconds after last successful update
- Heartbeat publishing: Every 30 seconds, apps broadcast "I'm online"

### What Doesn't Work ❌
- Daemon presence parsing: Logs show `Presence: unable to parse status`
- Heartbeat propagation: Subsequent heartbeats don't keep contacts online
- Real-time updates: Status changes depend on polling, not instant network events
- DHT/ICE connectivity: Intermittent errors like "No response from DHT to ICE request"

## Technical Details

### Implementation

**PresenceManager** (shared/src/commonMain/kotlin/com/gettogether/app/data/repository/PresenceManager.kt):
- Broadcasts initial "online" when account starts
- Sends heartbeat every 30 seconds
- Uses `jamiBridge.publishPresence(accountId, isOnline = true, note = "")`
- Empty note format (custom formats like "heartbeat:timestamp" caused parse errors)

**AccountRepository** integration:
- Calls `presenceManager.startBroadcasting(accountId)` when account becomes active
- Broadcasts ARE being sent (confirmed in logs)

### Daemon Behavior

**Receiving Device Logs** (Pixel 2):
```
19:24:15 - [PRESENCE-UPDATE] Contact came ONLINE (transition) ✅
19:24:42 - Presence: unable to parse status ❌
19:24:42 - Presence: unable to parse status ❌
19:25:14 - [PRESENCE-UPDATE] Contact still ONLINE (polling refresh) ✅
19:25:54 - Presence timeout (99.7s since last update) ❌
```

**Broadcasting Device Logs** (Pixel 7a):
```
19:24:12 - [PRESENCE-MANAGER] Publishing initial ONLINE presence
19:24:42 - [PRESENCE-MANAGER] Heartbeat published
19:25:12 - [PRESENCE-MANAGER] Heartbeat published
(continues every 30s)
```

**Daemon Parse Errors**:
- Jami native daemon logs: `jamiaccount.cpp: Presence: unable to parse status`
- Appears at heartbeat times (every 30s)
- Does NOT prevent initial detection
- DOES prevent ongoing presence updates

### Workaround: Polling System

Since heartbeats don't work reliably, we use polling:

**ContactRepositoryImpl** (pollContactPresence):
- Runs every 60 seconds (foreground only)
- Unsubscribes then resubscribes to force fresh DHT query
- Updates `_lastSubscribeTimestamp` to track subscribe time
- Polls ALL contacts (not just online ones)

**Stale Filter** (DISABLED):
- Originally blocked daemon cache responses within 2s of subscribe
- Disabled because it also blocked legitimate initial presence detection
- Without it, polling works but causes brief "false online" from daemon cache

**Timeout Checker** (checkPresenceTimeouts):
- Runs every 10 seconds
- Marks contacts offline if no update in 90 seconds
- Acts as safety net for missed heartbeats

## Timing Behavior

### Online Detection
1. **App starts**: Both devices start PresenceManager
2. **Initial broadcast**: Daemon receives, contact marked online in ~3 seconds ✅
3. **First heartbeat** (30s later): Daemon can't parse, no update ❌
4. **First poll** (60s later): Polling refreshes status ✅
5. **Second heartbeat** (60s): Daemon can't parse ❌
6. **Timeout** (90s): Contact marked offline ❌
7. **Second poll** (120s): Contact refreshed to online ✅
8. **Cycle repeats**: Contact toggles offline (timeout at 90s) then online (poll at 60s)

### Offline Detection
1. **App killed**: PresenceManager stops, no more broadcasts
2. **Last poll** (up to 60s ago): Status still online from cache
3. **Timeout fires** (90s after last update): Contact marked offline ✅
4. **Next poll** (60s later): Polling confirms offline (no DHT response) ✅

**Net Result**:
- Contact appears online for ~90-150 seconds after app is killed
- Then correctly marked offline
- Not real-time, but eventually consistent

## Root Causes

### 1. Unknown Presence Format
- Daemon expects specific note format but documentation unclear
- Empty note ("") causes parse errors
- Custom notes ("heartbeat:timestamp") also fail
- May need specific XML/JSON structure or enum values

### 2. DHT/ICE Connectivity Issues
```
[device 3e42f895dd458de6] No response from DHT to ICE request.
[SwarmManager] Bootstrap: all connections failed
[Conversation] Bootstrap: Fallback failed. Wait for remote connections.
```

- DHT has intermittent connectivity
- May prevent presence broadcasts from propagating
- Could be network-specific (same WiFi works better via mDNS?)

### 3. API Surface Unclear
**JamiService.publish()** (native method):
- Method signature: `publish(accountId: String, isOnline: Boolean, note: String)`
- No documentation on expected note format
- No return value or error indication
- Parse errors only visible in native daemon logs

**Alternative APIs not explored**:
- Does daemon have `setPresenceStatus()` or `announcePresence()`?
- Is there a DHT announce mechanism?
- Should presence be auto-published by daemon?

## Files Involved

| File | Purpose | Lines |
|------|---------|-------|
| `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/PresenceManager.kt` | Broadcasts heartbeats | 40-110 |
| `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/AccountRepository.kt` | Starts/stops PresenceManager | ~150-180 |
| `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/ContactRepositoryImpl.kt` | Polling + timeout logic | 679-792 |
| `androidApp/src/main/kotlin/com/gettogether/app/jami/SwigJamiBridge.kt` | Calls JamiService.publish() | Search "publishPresence" |
| `androidApp/src/main/kotlin/com/gettogether/app/GetTogetherApplication.kt` | Removed offline publishing (was corrupting state) | 118-126 |

## Previous Attempts

### Attempt 1: Custom Note Format
```kotlin
jamiBridge.publishPresence(accountId, isOnline = true, note = "heartbeat:$timestamp")
```
**Result**: Daemon logs "unable to parse status" ❌

### Attempt 2: Empty Note
```kotlin
jamiBridge.publishPresence(accountId, isOnline = true, note = "")
```
**Result**: Still "unable to parse status", but initial detection works ✅/❌

### Attempt 3: Offline Publishing on Shutdown
```kotlin
appLifecycleManager.setShutdownCallback {
    presenceManager.stopAll() // publishes offline
}
```
**Result**: Corrupted account state via broken `JamiService.publish()` API ❌
**Fix**: Removed offline publishing entirely

## Impact on User Experience

### Positive
- Contacts DO come online (within ~3 seconds)
- Contacts DO eventually go offline (within ~90-150 seconds)
- System is stable and doesn't corrupt state
- Battery-friendly (polling only in foreground)

### Negative
- Not real-time (depends on 60s polling interval)
- Offline detection delayed (90+ seconds)
- Logs show errors every 30s ("unable to parse")
- DHT errors may indicate underlying network issues

## Recommendations for Future Investigation

### Short Term (Low Hanging Fruit)
1. **Test different note formats**:
   - Try: `"available"`, `"online"`, `"1"`, `"true"`
   - Try XML: `"<status>online</status>"`
   - Try JSON: `"{\"status\":\"online\"}"`

2. **Check daemon source code**:
   - Location: `jami-daemon/src/jamidht/jamiaccount.cpp`
   - Search for: "unable to parse status"
   - Identify expected format

3. **Verify mDNS vs DHT**:
   - Test on same WiFi network (should use mDNS)
   - Test cross-network (requires DHT)
   - Check if presence works better on mDNS

### Medium Term (API Exploration)
1. **Alternative presence APIs**:
   - Check if `JamiService` has other presence methods
   - Review SWIG bindings for missed APIs
   - Check official Jami client source code

2. **DHT health monitoring**:
   - Track DHT connectivity state
   - Only broadcast when DHT connected
   - Add retry logic for failed broadcasts

### Long Term (Architectural)
1. **Replace polling with real presence**:
   - Fix parse format → heartbeats work
   - Remove polling entirely
   - Reduce timeout to 60s (2× heartbeat interval)

2. **Add presence state machine**:
   - Track: Unknown → Polling → Online/Offline
   - Show "Checking..." state during initial poll
   - Differentiate network offline vs app offline

## Current Configuration

| Setting | Value | Rationale |
|---------|-------|-----------|
| Heartbeat interval | 30 seconds | Standard keepalive (can't be too frequent) |
| Poll interval | 60 seconds | Before timeout, battery-friendly |
| Timeout | 90 seconds | 3× heartbeat, allows missed beats |
| Stale filter | DISABLED | Was blocking legitimate detection |
| Polling scope | ALL contacts | Prevents deadlock from stale filter |
| Timestamp update | On transition only | Allows timeout to fire correctly |
| Subscribe tracking | Updated before resubscribe | Enables stale filter (when re-enabled) |

## Conclusion

The presence system **works but is inefficient**. It relies on polling (60s) and timeout (90s) rather than real-time heartbeats (30s) due to daemon parse errors. The root cause is unclear presence format expectations and intermittent DHT issues. For production, this is acceptable but not optimal.

**Status**: Live with polling-based presence until daemon parse format is identified or DHT connectivity is improved.
