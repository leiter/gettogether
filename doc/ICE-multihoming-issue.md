# ICE Multi-Homing Issue: Root Cause Analysis

## Summary

Messages between Pixel 7a and Pixel 2 fail to deliver due to **asymmetric ICE connectivity** caused by **multi-homed network interfaces** on Pixel 7a.

## Environment

| Device | Serial | IPv4 | IPv6 (WiFi) | IPv6 (Mobile) |
|--------|--------|------|-------------|---------------|
| Pixel 7a | 37281JEHN03065 | 192.168.178.43 | 2a02:2455:865b:3400:* | 2a00:fbc:f63f:ead::2 |
| Pixel 2 | FA7AJ1A06417 | 192.168.178.22 | 2a02:2455:865b:3400:* | (none active) |

Both devices are on the same WiFi network (192.168.178.x / 2a02:2455:865b:3400::/64).

## Root Cause

**Pixel 7a has mobile data interface active alongside WiFi:**

```
rmnet2: inet6 2a00:fbc:f63f:ead::2/64 scope global  (ACTIVE)
wlan0:  inet 192.168.178.43/24                       (ACTIVE)
```

This causes Jami's ICE implementation to advertise **4 host candidates** from Pixel 7a:
1. `192.168.178.43` (WiFi IPv4) - reachable
2. `2a02:2455:865b:3400:3ff9:8520:9339:a77e` (WiFi IPv6) - reachable
3. `2a02:2455:865b:3400:9433:49ff:fed5:f371` (WiFi IPv6) - reachable
4. `2a00:fbc:f63f:ead::2` (Mobile IPv6) - **NOT reachable from Pixel 2**

## Observed Behavior

| Direction | ICE Result | TLS Result |
|-----------|------------|------------|
| Pixel 2 → Pixel 7a | SUCCESS | Handshake fails (push error/broken pipe) |
| Pixel 7a → Pixel 2 | FAILED | N/A (never reaches TLS) |

### Log Evidence

**Pixel 2 (successful ICE):**
```
[ice:0x724d5b8cf0] Negotiation starting 9 remote candidate(s)
[ice:0x724d5b8cf0] TCP negotiation success
```

**Pixel 7a (failed ICE):**
```
[ice:0xb40000718a2d3010] Negotiation starting 7 remote candidate(s)
[ice:0xb40000718a2d3010] TCP negotiation failed: All ICE checklists failed (PJNATH_EICEFAILED)
```

## Why Asymmetric?

1. **Pixel 2 → Pixel 7a works**: Pixel 2 only advertises WiFi candidates, which Pixel 7a can reach via either interface.

2. **Pixel 7a → Pixel 2 fails**: Pixel 7a's ICE checklist includes unreachable mobile interface candidates. When ICE attempts to connect, some/all connectivity checks fail.

## Additional Factor: Mullvad VPN

Pixel 7a has Mullvad VPN installed (seen in notification logs). This may:
- Route traffic through mobile interface
- Affect network interface priority
- Interfere with local network discovery

## Solutions

### Immediate Workaround
1. **Disable mobile data on Pixel 7a** during testing
2. **Disable VPN** (Mullvad) on Pixel 7a

### Long-term Fixes (in dhtnet/jami-daemon)

1. **Interface filtering**: Only advertise candidates from "preferred" interfaces
2. **Network type awareness**: Prefer WiFi over mobile for local connections
3. **Reachability checks**: Test candidate reachability before advertising
4. **Configuration option**: Allow users to specify which interfaces to use

### Code Location

ICE candidate gathering in dhtnet:
- `dhtnet/src/ice_transport.cpp` - `IceTransport::addSTUNServer()`, candidate handling
- `dhtnet/include/ice_options.h` - ICE configuration options

## Testing Verification

To verify the fix works:
```bash
# Disable mobile data on Pixel 7a
adb -s 37281JEHN03065 shell svc data disable

# Re-run messaging test
# Both directions should now succeed
```

## Timeline

- **2026-01-17**: Issue identified and root cause analyzed
- Logs captured in `tmp/pixel7a_tls_test.log` and `tmp/pixel2_tls_test.log`
- **2026-01-17**: Fix verified - disabling mobile data on Pixel 7a allowed successful messaging

## Related Issues

- TLS handshake failures after successful ICE (separate issue)
- SELinux denials for git operations (affects conversation sync)
