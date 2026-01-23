# GetTogether Project - Required Dependencies Installation

**Project:** GetTogether (Kotlin Multiplatform iOS/Android)
**Purpose:** Build jami-daemon for iOS device and simulator

---

## Homebrew Packages to Install

```bash
brew install \
    autoconf \
    autoconf-archive \
    automake \
    libtool \
    pkg-config \
    cmake \
    yasm \
    nasm \
    swig \
    carthage
```

### Package Details

| Package | Version | Purpose |
|---------|---------|---------|
| `autoconf` | latest | GNU Autotools - configure script generation |
| `autoconf-archive` | latest | Macro archive for autoconf |
| `automake` | latest | Makefile generation |
| `libtool` | latest | GNU Libtool for library building |
| `pkg-config` | latest | Library discovery tool |
| `cmake` | latest | CMake build system |
| `yasm` | latest | x86 assembler (required by FFmpeg) |
| `nasm` | latest | Netwide Assembler (required by FFmpeg) |
| `swig` | 4.0+ | Interface wrapper generator |
| `carthage` | latest | iOS dependency manager |

---

## Already Installed (No Action Needed)

- gettext
- Xcode 26.2
- Xcode Command Line Tools
- CocoaPods 1.16.2
- Ruby 4.0.0
- Java/JDK 17.0.17
- Android SDK (platforms 34-36, NDK 27)

---

## Optional but Recommended

```bash
# Android SDK command-line tools (for SDK management)
# Install via Android Studio SDK Manager or download from:
# https://developer.android.com/studio#command-tools
```

---

## Verification Commands

After installation, verify with:

```bash
autoconf --version
automake --version
cmake --version
swig -version
carthage version
```

---

## One-Line Install

```bash
brew install autoconf autoconf-archive automake libtool pkg-config cmake yasm nasm swig carthage
```
