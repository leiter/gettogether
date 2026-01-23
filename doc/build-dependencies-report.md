# GetTogether Build Dependencies Report

**Generated:** 2026-01-23
**Project Type:** Kotlin Multiplatform (KMP) with Compose Multiplatform

---

## Executive Summary

| Platform | Build Status | Missing Dependencies |
|----------|--------------|---------------------|
| **Android** | **SUCCESSFUL** | None (warnings only) |
| **iOS** | **PARTIAL** | Memory configuration issue, potential Jami native library |

---

## 1. System Environment Analysis

### 1.1 Installed Tools

| Tool | Version | Status |
|------|---------|--------|
| **Java (JDK)** | OpenJDK 17.0.17 | Required: 17+ |
| **Gradle** | 8.9 (via wrapper) | Required: 8.x |
| **Kotlin** | 2.1.21 | OK |
| **Xcode** | 26.2 (Build 17C52) | OK |
| **CocoaPods** | 1.16.2 | OK |
| **Ruby** | 4.0.0 | OK |
| **ADB** | 36.0.0 | OK |

### 1.2 Android SDK Components

| Component | Installed Version | Required |
|-----------|------------------|----------|
| **Platform** | 34, 35, 36 | compileSdk=36 |
| **Build Tools** | 34.0.0, 35.0.0, 36.1.0 | OK |
| **NDK** | 27.0.12077973 | OK |
| **Platform Tools** | Present | OK |
| **Emulator** | Present | OK |
| **CMake** | **NOT INSTALLED** | Not needed (pre-built libs) |
| **Command-line Tools** | **NOT INSTALLED** | Recommended |

### 1.3 iOS Development Environment

| Component | Status |
|-----------|--------|
| **Xcode Command Line Tools** | Installed at `/Applications/Xcode.app/Contents/Developer` |
| **iOS Simulators** | Available (iOS 18.0, 18.4) |
| **CocoaPods** | Installed |

---

## 2. Android Build Analysis

### 2.1 Build Result: SUCCESS

```
BUILD SUCCESSFUL in 52s
60 actionable tasks: 59 executed, 1 from cache
```

### 2.2 Warnings (Non-blocking)

| Warning | Description | Action |
|---------|-------------|--------|
| AGP Compatibility | AGP 8.7.3 tested up to compileSdk=35, using 36 | Add `android.suppressUnsupportedCompileSdk=36` to gradle.properties |
| Deprecated Property | `kotlin.mpp.androidGradlePluginCompatibility.nowarn` | Remove from gradle.properties |
| Deprecated APIs | `kotlinx.datetime.Instant` deprecated | Migrate to `kotlin.time.Instant` |
| Beta Features | expect/actual classes in Beta | Add `-Xexpect-actual-classes` compiler flag |
| Deprecated UI | `Divider` renamed to `HorizontalDivider` | Update imports in ContactsTab.kt, ConversationsTab.kt |
| Deprecated Class | `CallNotificationManager` deprecated | Migrate to `NotificationHelper` |

### 2.3 Native Libraries (Android)

Pre-built JNI libraries are present and correctly configured:

| Library | Architectures |
|---------|--------------|
| `libjami-core-jni.so` | armeabi-v7a, arm64-v8a, x86_64 |
| `libc++_shared.so` | armeabi-v7a, arm64-v8a, x86_64 |

**Location:** `androidApp/src/main/jniLibs/`

---

## 3. iOS Build Analysis

### 3.1 Shared Framework Build: SUCCESS

```
BUILD SUCCESSFUL in 1m 15s
```

The Kotlin shared framework compiles successfully for all iOS targets:
- `iosArm64` (device)
- `iosX64` (Intel simulator)
- `iosSimulatorArm64` (Apple Silicon simulator)

### 3.2 Full iOS App Build: FAILED

**Error:** Gradle Daemon memory exhaustion during Xcode build phase

```
The Daemon will expire immediately since the JVM garbage collector is thrashing.
The currently configured max heap space is '2 GiB'
```

### 3.3 Missing/Recommended iOS Dependencies

| Item | Status | Priority | Resolution |
|------|--------|----------|------------|
| **Gradle JVM Memory** | Insufficient | **CRITICAL** | Increase to 4GB+ |
| **KLIB: savedstate** | Warning | Low | Transitive dependency, can be ignored |
| **KLIB: lifecycle-viewmodel-savedstate** | Warning | Low | Transitive dependency, can be ignored |
| **Bundle ID** | Warning | Low | Specify `-Xbinary=bundleId=<id>` |
| **Jami Native Library (iOS)** | **MISSING** | **HIGH** | Requires xcframework/static library |

---

## 4. Required Actions

### 4.1 CRITICAL: Fix iOS Build Memory Issue

Edit `gradle.properties`:

```properties
# Current (insufficient)
org.gradle.jvmargs=-Xmx2048M -Dfile.encoding=UTF-8 -Dkotlin.daemon.jvm.options\="-Xmx2048M"

# Recommended
org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8 -Dkotlin.daemon.jvm.options\="-Xmx4096M"
```

### 4.2 HIGH: iOS Native Library (Jami)

The iOS app references a `JamiBridgeWrapper` Objective-C interface but there's no corresponding native Jami library (`.xcframework` or `.a`). The Android build has `libjami-core-jni.so`, but iOS equivalent is missing.

**Required:**
- Pre-built Jami library for iOS (`libjami.xcframework` or static `.a` files)
- Add to Xcode project under "Link Binary With Libraries"

**Current JamiBridge files (wrapper only, no implementation):**
- `iosApp/iosApp/JamiBridge/JamiBridgeWrapper.h`
- `iosApp/iosApp/JamiBridge/JamiBridgeWrapper.m`

### 4.3 RECOMMENDED: Install Android Command-line Tools

```bash
# Via Android Studio SDK Manager, or:
# Download from https://developer.android.com/studio#command-tools
# Extract to $ANDROID_HOME/cmdline-tools/latest/
```

### 4.4 RECOMMENDED: Suppress Warnings

Add to `gradle.properties`:

```properties
android.suppressUnsupportedCompileSdk=35,36
```

Add to Kotlin compiler options in `shared/build.gradle.kts`:

```kotlin
compilerOptions {
    freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    freeCompilerArgs.add("-Xexpect-actual-classes")
}
```

---

## 5. Dependency Summary

### 5.1 Project Dependencies (libs.versions.toml)

| Category | Libraries |
|----------|-----------|
| **UI Framework** | Compose Multiplatform 1.9.3 |
| **Networking** | Ktor Client 3.0.2 |
| **Database** | Room 2.7.0-alpha11, SQLite Bundled 2.5.0-alpha11 |
| **DI** | Koin 4.0.0 |
| **Serialization** | kotlinx.serialization 1.7.3 |
| **Navigation** | AndroidX Navigation Compose 2.9.1 |
| **Lifecycle** | AndroidX Lifecycle 2.8.7 |
| **Image Loading** | Coil Compose 3.0.0 |
| **Logging** | Kermit 2.0.4 |
| **Date/Time** | kotlinx.datetime 0.7.1 |

### 5.2 Native Dependencies

| Platform | Library | Status |
|----------|---------|--------|
| Android | libjami-core-jni.so | Present |
| Android | libc++_shared.so | Present |
| iOS | libjami (xcframework) | **MISSING** |

---

## 6. Quick Fix Commands

### Rebuild Android (should succeed):
```bash
./gradlew :androidApp:assembleDebug
```

### Rebuild iOS Framework (should succeed):
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Full iOS App Build (after fixing memory):
```bash
xcodebuild -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build
```

### Clean Build (if issues persist):
```bash
./gradlew clean
rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*
./gradlew :shared:assembleXCFramework
```

---

## 7. Conclusion

**Android:** Fully functional. All dependencies present. Build succeeds with warnings.

**iOS:** Requires two fixes:
1. **Memory configuration** (easy fix in gradle.properties)
2. **Jami native library** (requires obtaining pre-built iOS library or building from source)

The project structure is well-organized as a standard KMP application. Once the iOS memory issue is resolved and the Jami native library is provided, iOS builds should succeed.
