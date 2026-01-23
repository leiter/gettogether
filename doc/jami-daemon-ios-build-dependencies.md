# Jami-Daemon iOS Build Dependencies

**Generated:** 2026-01-23
**Target Platforms:** iOS Device (arm64) + iOS Simulator (arm64, x86_64)

---

## Executive Summary

| Category | Status | Action Required |
|----------|--------|-----------------|
| **Submodule** | Empty | Clone jami-daemon |
| **Build Tools** | Mostly missing | Install via Homebrew |
| **SWIG** | Missing | Install for wrapper generation |
| **Carthage** | Missing | Install for iOS dependencies |

---

## 1. Missing Dependencies

### 1.1 Jami-Daemon Source Code

The submodule exists but is not cloned:

```bash
# Current state
/Users/user289697/Documents/gettogether/jami-daemon/ → EMPTY

# Clone the submodule
cd /Users/user289697/Documents/gettogether
git submodule update --init --recursive
```

**Submodule URL:** `https://github.com/leiter/jami-daemon.git`

---

### 1.2 Required Homebrew Packages

| Package | Status | Purpose |
|---------|--------|---------|
| `autoconf` | **MISSING** | GNU Autotools - configure script generation |
| `autoconf-archive` | **MISSING** | Macro archive for autoconf |
| `automake` | **MISSING** | Makefile generation |
| `libtool` | **MISSING** | GNU Libtool (not macOS libtool) |
| `pkg-config` | **MISSING** | Library discovery |
| `cmake` | **MISSING** | CMake build system |
| `gettext` | Installed | Internationalization |
| `yasm` | **MISSING** | x86 assembler (FFmpeg) |
| `nasm` | **MISSING** | Netwide Assembler (FFmpeg) |
| `swig` | **MISSING** | Wrapper/interface generator |
| `carthage` | **MISSING** | iOS dependency manager |

**Install all missing packages:**

```bash
brew install autoconf autoconf-archive automake libtool pkg-config cmake yasm nasm swig carthage
```

---

### 1.3 System Requirements

| Requirement | Current | Required |
|-------------|---------|----------|
| macOS | Darwin 25.2 | macOS 12+ |
| Xcode | 26.2 | Xcode 13+ |
| iOS Deployment Target | - | iOS 14.5+ |

---

## 2. Complete Installation Steps

### Step 1: Install Build Tools

```bash
# Install all required Homebrew packages
brew install \
    autoconf \
    autoconf-archive \
    automake \
    libtool \
    pkg-config \
    cmake \
    gettext \
    yasm \
    nasm \
    swig \
    carthage
```

### Step 2: Clone Jami-Daemon Submodule

```bash
cd /Users/user289697/Documents/gettogether

# Initialize and clone the submodule
git submodule update --init --recursive

# Verify
ls -la jami-daemon/
```

### Step 3: Verify Tools Installation

```bash
# Check all tools are available
autoconf --version
automake --version
libtool --version
cmake --version
swig -version
carthage version
```

---

## 3. Building Jami-Daemon for iOS

### 3.1 iOS Device (arm64)

```bash
cd /Users/user289697/Documents/gettogether/jami-daemon

# Bootstrap contrib dependencies for iOS device
cd contrib
mkdir -p native-iphoneos-arm64
cd native-iphoneos-arm64
../bootstrap --host=arm-apple-darwin --build=x86_64-apple-darwin

# Fetch and build all dependencies
make fetch-all
make -j$(sysctl -n hw.ncpu)

# Return to daemon root and build
cd ../..
mkdir -p build-ios-arm64
cd build-ios-arm64

# Configure for iOS device
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=../extras/tools/ios.toolchain.cmake \
    -DPLATFORM=OS64 \
    -DDEPLOYMENT_TARGET=14.5 \
    -DJAMI_JNI=OFF \
    -DJAMI_VIDEO=ON \
    -DJAMI_PLUGIN=OFF \
    -DJAMI_DBUS=OFF \
    -DJAMI_TESTS=OFF \
    -DBUILD_SHARED_LIBS=OFF

# Build
make -j$(sysctl -n hw.ncpu)
```

### 3.2 iOS Simulator (arm64 - Apple Silicon)

```bash
cd /Users/user289697/Documents/gettogether/jami-daemon

# Bootstrap contrib for simulator arm64
cd contrib
mkdir -p native-iphonesimulator-arm64
cd native-iphonesimulator-arm64
../bootstrap --host=arm-apple-darwin --build=x86_64-apple-darwin

make fetch-all
make -j$(sysctl -n hw.ncpu)

# Build daemon for simulator
cd ../..
mkdir -p build-ios-simulator-arm64
cd build-ios-simulator-arm64

cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=../extras/tools/ios.toolchain.cmake \
    -DPLATFORM=SIMULATORARM64 \
    -DDEPLOYMENT_TARGET=14.5 \
    -DJAMI_JNI=OFF \
    -DJAMI_VIDEO=ON \
    -DJAMI_PLUGIN=OFF \
    -DJAMI_DBUS=OFF \
    -DJAMI_TESTS=OFF \
    -DBUILD_SHARED_LIBS=OFF

make -j$(sysctl -n hw.ncpu)
```

### 3.3 iOS Simulator (x86_64 - Intel Mac)

```bash
cd /Users/user289697/Documents/gettogether/jami-daemon

# Bootstrap contrib for simulator x86_64
cd contrib
mkdir -p native-iphonesimulator-x86_64
cd native-iphonesimulator-x86_64
../bootstrap --host=x86_64-apple-darwin --build=x86_64-apple-darwin

make fetch-all
make -j$(sysctl -n hw.ncpu)

# Build daemon for simulator
cd ../..
mkdir -p build-ios-simulator-x86_64
cd build-ios-simulator-x86_64

cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=../extras/tools/ios.toolchain.cmake \
    -DPLATFORM=SIMULATOR64 \
    -DDEPLOYMENT_TARGET=14.5 \
    -DJAMI_JNI=OFF \
    -DJAMI_VIDEO=ON \
    -DJAMI_PLUGIN=OFF \
    -DJAMI_DBUS=OFF \
    -DJAMI_TESTS=OFF \
    -DBUILD_SHARED_LIBS=OFF

make -j$(sysctl -n hw.ncpu)
```

---

## 4. Creating XCFramework

After building for all platforms, create a universal XCFramework:

```bash
cd /Users/user289697/Documents/gettogether/jami-daemon

# Create XCFramework from all architectures
xcodebuild -create-xcframework \
    -library build-ios-arm64/libjami.a \
    -headers include \
    -library build-ios-simulator-arm64/libjami.a \
    -headers include \
    -library build-ios-simulator-x86_64/libjami.a \
    -headers include \
    -output ../iosApp/Frameworks/libjami.xcframework
```

---

## 5. Alternative: Using Official Jami Build Script

If the jami-daemon contains the official `compile-ios.sh` script:

```bash
cd /Users/user289697/Documents/gettogether/jami-daemon

# Build for iOS device only
./compile-ios.sh --platform=iPhoneOS

# Build for simulator only
./compile-ios.sh --platform=iPhoneSimulator

# Build for both (creates universal XCFramework)
./compile-ios.sh --platform=all

# Optional flags:
# --release        Build with optimizations
# --arch=arm64     Specific simulator architecture
```

---

## 6. Integrating with Xcode Project

### 6.1 Add XCFramework to Project

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select the `iosApp` target → General → Frameworks, Libraries, and Embedded Content
3. Click `+` → Add Other → Add Files
4. Navigate to `iosApp/Frameworks/libjami.xcframework`
5. Set Embed to "Do Not Embed" (static library)

### 6.2 Update Build Settings

In Xcode project settings:

```
HEADER_SEARCH_PATHS = $(PROJECT_DIR)/Frameworks/libjami.xcframework/Headers
LIBRARY_SEARCH_PATHS = $(PROJECT_DIR)/Frameworks/libjami.xcframework/ios-arm64
OTHER_LDFLAGS = -ljami -lc++ -framework Security -framework CoreFoundation
```

### 6.3 Link Required System Frameworks

Add these frameworks in Build Phases → Link Binary With Libraries:

- `Security.framework`
- `CoreFoundation.framework`
- `SystemConfiguration.framework`
- `AudioToolbox.framework`
- `AVFoundation.framework`
- `VideoToolbox.framework`
- `CoreMedia.framework`
- `CoreVideo.framework`

---

## 7. Quick Start Script

Create this script to automate the entire process:

```bash
#!/bin/bash
# File: script/build-jami-ios.sh

set -e

PROJECT_ROOT="/Users/user289697/Documents/gettogether"
DAEMON_DIR="$PROJECT_ROOT/jami-daemon"

echo "=== Step 1: Installing dependencies ==="
brew install autoconf autoconf-archive automake libtool pkg-config cmake gettext yasm nasm swig carthage || true

echo "=== Step 2: Cloning jami-daemon ==="
cd "$PROJECT_ROOT"
git submodule update --init --recursive

echo "=== Step 3: Building for iOS ==="
cd "$DAEMON_DIR"

if [ -f "./compile-ios.sh" ]; then
    ./compile-ios.sh --platform=all
else
    echo "compile-ios.sh not found, manual build required"
    echo "See doc/jami-daemon-ios-build-dependencies.md for instructions"
fi

echo "=== Done ==="
```

---

## 8. Dependency Libraries Built by Jami

The jami-daemon contrib system builds these libraries automatically:

| Library | Purpose |
|---------|---------|
| **OpenDHT** | Distributed Hash Table |
| **PJSIP** | SIP protocol stack |
| **GnuTLS** | TLS/SSL cryptography |
| **FFmpeg** | Audio/video processing |
| **Opus** | Audio codec |
| **vpx** | VP8/VP9 video codec |
| **x264** | H.264 video codec |
| **OpenSSL** | Cryptography |
| **libsecp256k1** | Elliptic curve crypto |
| **Argon2** | Password hashing |
| **msgpack** | Serialization |
| **fmt** | Formatting library |
| **yaml-cpp** | YAML parsing |

---

## 9. Troubleshooting

### Build fails with "xcrun: error"
```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

### Missing SDK
```bash
xcodebuild -showsdks
# Ensure iphoneos and iphonesimulator SDKs are listed
```

### Contrib build fails
```bash
# Clean and retry
cd jami-daemon/contrib/native-*
make clean
make distclean
make fetch-all
make -j4  # Use fewer jobs if memory issues
```

### libtool conflicts (macOS vs GNU)
```bash
# Use Homebrew's GNU libtool
export PATH="/opt/homebrew/opt/libtool/libexec/gnubin:$PATH"
```

---

## 10. Summary: Installation Commands

```bash
# 1. Install all Homebrew dependencies
brew install autoconf autoconf-archive automake libtool pkg-config cmake gettext yasm nasm swig carthage

# 2. Clone the submodule
cd /Users/user289697/Documents/gettogether
git submodule update --init --recursive

# 3. Build for iOS (if compile-ios.sh exists)
cd jami-daemon
./compile-ios.sh --platform=all

# 4. Output will be in:
#    - jami-daemon/xcframework/libjami.xcframework
```

---

## Sources

- [Jami Dependencies Documentation](https://docs.jami.net/en_US/build/dependencies.html)
- [Jami Client iOS Repository](https://github.com/savoirfairelinux/jami-client-ios)
- [Jami Daemon Repository](https://github.com/savoirfairelinux/jami-daemon)
