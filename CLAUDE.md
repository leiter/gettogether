# GetTogether (letsJam) - Project Instructions

## Project Overview

- **Project Name:** GetTogether (letsJam)
- **Type:** Kotlin Multiplatform (KMP) application
- **Purpose:** Jami-based secure messaging and calling
- **Package:** `com.gettogether.app`

---

## Directory Structure

| Path | Purpose |
|------|---------|
| `shared/` | KMP shared code |
| `shared/src/commonMain/` | Cross-platform code |
| `shared/src/androidMain/` | Android implementations |
| `shared/src/iosMain/` | iOS implementations |
| `androidApp/` | Android application |
| `iosApp/` | iOS application |
| `jami-daemon/` | Native library submodule |
| `script/` | Build scripts |
| `doc/` | Documentation |

---

## Architecture

- **Pattern:** MVVM with Repository pattern
- **Platform Abstraction:** JamiBridge interface
- **Dependency Injection:** Koin
- **UI Framework:** Compose Multiplatform
- **Event Handling:** Event-driven with SharedFlow

---

## Key Files Reference

### ViewModels
| File | Purpose |
|------|---------|
| `ChatViewModel.kt` | Messaging functionality |
| `CallViewModel.kt` | Call handling |
| `SettingsViewModel.kt` | Settings and account management |
| `ContactsViewModel.kt` | Contacts management |
| `ConversationsViewModel.kt` | Conversation list |

### Repositories
| File | Purpose |
|------|---------|
| `AccountRepository.kt` | Account lifecycle management |
| `ContactRepositoryImpl.kt` | Contacts and presence |
| `ConversationRepositoryImpl.kt` | Messages and conversations |

### Platform Bridge
| File | Purpose |
|------|---------|
| `JamiBridge.kt` | Interface definition (100+ methods) |
| `SwigJamiBridge.kt` | Android SWIG implementation |
| `JamiBridge.ios.kt` | iOS implementation (stubs) |

---

## Build Commands

```bash
# Debug build
./gradlew :androidApp:assembleDebug

# Install on device
./gradlew :androidApp:installDebug

# Run tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

---

## Native Library Rebuild

When changes are made to jami-daemon or native bindings:

```bash
# Update submodule
git submodule update --init --recursive

# Generate SWIG bindings
cd jami-daemon/bin/jni && ./make-swig.sh

# Build for arm64
./script/build-libjami.sh

# Build for x86_64 (emulator)
./script/build-complete-x86_64.sh

# Rebuild app
./gradlew clean :androidApp:assembleDebug
```

---

## Log Tags for Debugging

| Tag | Component |
|-----|-----------|
| `[ACCOUNT-RESTORE]` | Account loading/restore |
| `[ACCOUNT-CREATE]` | Account creation |
| `[ACCOUNT-IMPORT]` | Account import from backup |
| `[ACCOUNT-EXPORT]` | Account export/backup |
| `[ACCOUNT-LOGOUT]` | Logout flow |
| `[ACCOUNT-RELOGIN]` | Re-login flow |
| `SwigJamiBridge` | Native bridge operations |

### Log Filtering Examples
```bash
# Account operations
adb logcat -s "AccountRepository" | grep -E "\[ACCOUNT-"

# Native bridge
adb logcat -s "SwigJamiBridge"

# Combined debugging
adb logcat -s "SwigJamiBridge" -s "AccountRepository" -s "ConversationRepository"
```

---

## ADB Commands (Project-Specific)

```bash
# Launch app
adb shell am start -n com.gettogether.app/.MainActivity

# Clear app data
adb shell pm clear com.gettogether.app

# Force stop
adb shell am force-stop com.gettogether.app

# View filtered logs
adb logcat -s "SwigJamiBridge" -s "AccountRepository"
```

---

## Known Limitations

| Area | Status | Notes |
|------|--------|-------|
| iOS | ~30% complete | Stubs only, not functional |
| Audio input enumeration | Crashes | Native bug in jami-daemon |
| Presence detection | Varies | mDNS for same network, DHT for cross-network |

See `doc/CRITICAL-NATIVE-BUG.md` for details on native issues.

---

## Documentation Files

| File | Purpose |
|------|---------|
| `doc/TODO.md` | Task tracking |
| `doc/WORKING_FEATURES.md` | Implemented features inventory |
| `doc/SESSION.md` | Session continuity notes |
| `doc/ui-coordinates.yaml` | UI test coordinates |
| `doc/CRITICAL-NATIVE-BUG.md` | Native bug documentation |

---

## Testing Workflow

1. Build and install: `./gradlew :androidApp:installDebug`
2. Clear previous data if needed: `adb shell pm clear com.gettogether.app`
3. Launch app: `adb shell am start -n com.gettogether.app/.MainActivity`
4. Monitor logs: `adb logcat -s "SwigJamiBridge"`
5. Take screenshots: Follow global CLAUDE.md screenshot protocol

---

## Submodule Management

The `jami-daemon` directory is a git submodule:

```bash
# Check submodule status
git submodule status

# Update to tracked commit
git submodule update --init --recursive

# Update to latest (careful - may break things)
cd jami-daemon && git pull origin master
```
