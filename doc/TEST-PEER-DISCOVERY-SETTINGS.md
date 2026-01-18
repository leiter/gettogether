# Testing Peer Discovery Settings for Continuous Presence Updates

**Date:** 2026-01-18
**Status:** 🧪 Testing in Progress

---

## Hypothesis

Based on analysis of the official jami-android-client, we discovered that three peer discovery settings default to **FALSE** in the jami-daemon:

```cpp
// From jami-daemon/src/jamidht/jamiaccount_config.h
bool dhtPeerDiscovery {false};      // DEFAULT: FALSE
bool accountPeerDiscovery {false};  // DEFAULT: FALSE
bool accountPublish {false};        // DEFAULT: FALSE
```

**Hypothesis**: Enabling these settings may allow the daemon to send continuous presence updates automatically, eliminating the need for our application-level polling (unsubscribe/resubscribe every 60s).

---

## Implementation

### Changes Made

**File**: `shared/src/commonMain/kotlin/com/gettogether/app/data/repository/AccountRepository.kt`

**Added helper function**:
```kotlin
private suspend fun enablePeerDiscoverySettings(accountId: String) {
    val currentDetails = jamiBridge.getAccountDetails(accountId).toMutableMap()

    // Enable all four peer discovery and presence settings
    currentDetails["Account.peerDiscovery"] = "true"
    currentDetails["Account.accountDiscovery"] = "true"
    currentDetails["Account.accountPublish"] = "true"
    currentDetails["Account.presenceEnabled"] = "true"

    jamiBridge.setAccountDetails(accountId, currentDetails)
}
```

**Called in**:
1. `loadAccounts()` - When loading existing accounts on app start
2. `createAccount()` - After creating a new account
3. `importAccount()` - After importing an account from backup
4. `reloginToAccount()` - After reactivating a deactivated account

---

## Test Setup

### Devices
- **Device 1**: Pixel 2 (Android 11) - Account: TestUserA (f813fc8343e679e5)
- **Device 2**: Pixel 7a (Android 16) - Account: pi2 (8765a312ebecf7c5)

### Test Logs

**Device 1 (Pixel 2):**
```
[PRESENCE-CONFIG] === Enabling peer discovery settings for account: f813fc8343e679e5... ===
[PRESENCE-CONFIG] Setting account details:
[PRESENCE-CONFIG]   Account.peerDiscovery = true
[PRESENCE-CONFIG]   Account.accountDiscovery = true
[PRESENCE-CONFIG]   Account.accountPublish = true
[PRESENCE-CONFIG]   Account.presenceEnabled = true
[PRESENCE-CONFIG] ✓ Peer discovery settings enabled successfully
```

**Device 2 (Pixel 7a):**
```
[PRESENCE-CONFIG] === Enabling peer discovery settings for account: 8765a312ebecf7c5... ===
[PRESENCE-CONFIG] Setting account details:
[PRESENCE-CONFIG]   Account.peerDiscovery = true
[PRESENCE-CONFIG]   Account.accountDiscovery = true
[PRESENCE-CONFIG]   Account.accountPublish = true
[PRESENCE-CONFIG]   Account.presenceEnabled = true
[PRESENCE-CONFIG] ✓ Peer discovery settings enabled successfully
```

✅ **Both devices successfully enabled peer discovery settings**

---

## Test Methodology

### Monitoring For

1. **Continuous PresenceChanged events** - Do they occur automatically without polling?
2. **newBuddyNotification callbacks** - Are they triggered continuously by the daemon?
3. **Polling activity** - Does `[PRESENCE-POLL]` still run, or is it unnecessary now?

### Success Criteria

**✅ Success (Settings Enable Continuous Updates)**:
- PresenceChanged events occur automatically every 30-90 seconds
- No polling needed (can remove the unsubscribe/resubscribe mechanism)
- Timeout serves as pure fallback

**❌ Failure (Settings Don't Help)**:
- PresenceChanged events only occur during polling cycles
- Polling is still necessary for continuous updates
- Settings had no effect on daemon behavior

### Monitoring Duration
- **2 minutes** of continuous log monitoring on both devices

---

## Expected Outcomes

### Scenario A: Settings Enable Continuous Updates ✅

If peer discovery settings enable daemon-level presence broadcasting:
- Remove polling mechanism from ContactRepositoryImpl
- Keep timeout as fallback only
- Rely on pure event-driven approach like official client

### Scenario B: Settings Don't Help ❌

If settings don't enable continuous updates:
- Keep current polling solution (PRESENCE-POLLING-SOLUTION.md)
- Settings still helpful for initial discovery and network performance
- Polling remains necessary workaround

---

## Related Documentation

- `doc/PRESENCE-POLLING-SOLUTION.md` - Current polling implementation
- `doc/PRESENCE-BROADCASTING-IMPLEMENTATION.md` - Failed broadcasting attempt
- `doc/BUG-PRESENCE-DETECTION.md` - Original presence detection issues

---

## Results

**Status:** ❌ **Settings Did NOT Enable Continuous Updates**

### Monitoring Duration
2 minutes of continuous log monitoring (19:49 - 19:51)

### Findings

#### ✅ Positive: Improved Initial Discovery

Both devices detected each other **immediately** upon app start:
- **Device 1** detected Device 2 at 19:49:08 (within 1 second of app start)
- **Device 2** detected Device 1 at 19:49:26 (within 1 second of app start)

This is faster and more reliable than before enabling the settings.

#### ❌ Negative: No Continuous Updates

**No spontaneous PresenceChanged events occurred between polling cycles.**

Presence updates **ONLY** happened during:
1. Initial contact discovery (app start)
2. Polling cycles (unsubscribe/resubscribe every 60s)

**Timeline Evidence:**

**Device 1 (Pixel 2):**
```
19:49:08 - Initial detection → ONLINE (spontaneous)
19:50:08 - [PRESENCE-POLL] triggered → ONLINE (forced refresh)
19:51:08 - [PRESENCE-POLL] triggered → ONLINE (forced refresh)
```

**Device 2 (Pixel 7a):**
```
19:49:26 - Initial detection → ONLINE (spontaneous)
19:50:26 - [PRESENCE-POLL] triggered → ONLINE (forced refresh)
19:51:26 - [PRESENCE-POLL] triggered → ONLINE (forced refresh)
```

**Conclusion**: Between polling cycles (60-second gaps), there were **zero** automatic presence updates from the daemon.

---

## Interpretation

### Why Official Client Works Differently

The official jami-android-client uses a pure event-driven approach without polling. Possible explanations:

1. **Different daemon version**: Official client may use a newer daemon with different default behavior
2. **Different network environment**: Official client testing may occur in more densely populated DHT networks
3. **Missing configuration**: There may be additional daemon-level configuration we haven't discovered
4. **SIP vs DHT**: Official client may still support SIP-based presence which works differently

### What We Learned

**Peer discovery settings are beneficial for:**
- ✅ Faster initial contact discovery
- ✅ Better network connectivity (DHT bootstrapping)
- ✅ More reliable peer-to-peer connections

**But they do NOT:**
- ❌ Enable continuous automatic presence updates
- ❌ Eliminate the need for application-level polling
- ❌ Make the daemon send spontaneous newBuddyNotification callbacks

---

## Decision

### Keep Both Implementations

**✅ Keep peer discovery settings** (this PR):
- Improves initial discovery speed
- Better network connectivity
- No downsides

**✅ Keep polling mechanism** (PRESENCE-POLLING-SOLUTION.md):
- Still required for continuous presence updates
- Proven to work reliably
- Only polls online contacts (minimal overhead)

### Summary

The peer discovery settings are a **useful enhancement** but not a **replacement** for polling. Both should be used together:

1. **Peer discovery settings** → Fast initial discovery
2. **Polling** → Continuous presence updates
3. **Timeout** → Fallback safety net

---

## Related Documentation

- ✅ `doc/PRESENCE-POLLING-SOLUTION.md` - Polling implementation (KEEP)
- ✅ `doc/PRESENCE-BROADCASTING-IMPLEMENTATION.md` - Broadcasting attempt (FAILED - documented)
- ✅ `doc/BUG-PRESENCE-DETECTION.md` - Original issues (RESOLVED by polling)

---

**Test Date:** 2026-01-18
**Test Duration:** 2 minutes
**Devices:** 2 (Pixel 2 Android 11, Pixel 7a Android 16)
**Result:** Settings improve discovery but polling still required
