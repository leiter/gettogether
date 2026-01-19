# Native Build Analysis

**Date:** 2026-01-19

## Current Build Health Status

Libraries are correctly built and functional.

### Library Inventory

| Architecture | libjami-core.so | libjami-core-jni.so | libc++_shared.so |
|--------------|-----------------|---------------------|------------------|
| arm64-v8a    | 235MB (26MB stripped) | 19MB (1.3MB stripped) | 1.8MB |
| x86_64       | 244MB (29MB stripped) | 17MB (1.3MB stripped) | 1.6MB |
| armeabi-v7a  | **Missing** | **Missing** | **Missing** |

Libraries are ELF shared objects with proper dependencies:
- libOpenSLES.so, liblog.so, libGLESv2.so, libEGL.so, libandroid.so
- libc++_shared.so (bundled)
- Standard libc, libm, libdl

Gradle automatically strips debug symbols for APK packaging.

### Build Environment

| Component | Current Version | Location |
|-----------|-----------------|----------|
| NDK | 27.0.12077973 | ~/Android/Sdk/ndk/ |
| CMake | 3.22.1 (system) | /usr/bin/cmake |
| SWIG | 4.2.0 | /usr/bin/swig |
| Daemon | ddfc17622896 (Jan 17) | jami-daemon submodule |

### Contrib Dependencies

Built for both architectures:
- aarch64-linux-android: 310MB
- x86_64-linux-android: 333MB

Key libraries: libavcodec, libgnutls, libdhtnet, libgit2, libcrypto, etc.

---

## Comparison: letsJam vs jami-client-android

| Aspect | letsJam | jami-client-android | Notes |
|--------|---------|---------------------|-------|
| **NDK** | 27.0.12077973 | 29.0.14206865 | Official uses newer |
| **CMake** | 3.22.1 | 4.1.2 | Official uses SDK cmake |
| **SWIG** | 4.2.0 | 4.2.1 | Minor difference |
| **API Level** | 24 | 26 | Official dropped older devices |
| **compileSdk** | 35 | 36 | Official targets newer |
| **targetSdk** | 35 | 36 | Official targets newer |
| **Daemon commit** | ddfc1762 (Jan 17) | 0a48dd32 (Jan 14) | **Ours is newer** |
| **Build approach** | Manual scripts | Gradle-integrated | Different trade-offs |
| **LTO** | OFF | ON | Official has smaller binaries |
| **armeabi-v7a** | Missing | Included | We should build or remove from abiFilters |

### Official Build Configuration (from app/build.gradle.kts)

```kotlin
externalNativeBuild {
    cmake {
        version = "4.1.2"
        arguments += listOf(
            "-DANDROID_STL=c++_shared",
            "-DBUILD_CONTRIB=ON",
            "-DBUILD_EXTRA_TOOLS=OFF",
            "-DBUILD_TESTING=OFF",
            "-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON",  // LTO
            "-DJAMI_JNI=ON",
            "-DJAMI_JNI_PACKAGEDIR=...",
            "-DJAMI_DATADIR=/data/data/$namespace/files",
        )
    }
    ndk {
        debugSymbolLevel = "FULL"
        abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
    }
}
```

---

## Improvement Recommendations

### High Priority

1. **Enable LTO (Link-Time Optimization)**

   Add to cmake arguments in build scripts:
   ```bash
   -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON
   ```
   Expected benefit: 10-20% smaller binaries, potential performance improvement.

2. **Fix armeabi-v7a inconsistency**

   Either build the library:
   ```bash
   # Create build script for armeabi-v7a
   ```
   Or remove from abiFilters in androidApp/build.gradle.kts:
   ```kotlin
   abiFilters += listOf("arm64-v8a", "x86_64")  // Remove armeabi-v7a
   ```

### Medium Priority

3. **Update NDK to 29**
   ```bash
   sdkmanager 'ndk;29.0.14206865'
   ```
   Then update script/setup-jami-build.sh:
   ```bash
   export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/29.0.14206865
   ```

4. **Update CMake to 4.1.2**
   ```bash
   sdkmanager 'cmake;4.1.2'
   ```
   Better Android cross-compilation support.

### Low Priority / Future

5. **Consider Gradle-integrated builds**

   Benefits:
   - Automatic rebuild on source changes
   - Multi-ABI parallel builds
   - Cleaner CI/CD integration

   Trade-offs:
   - More complex initial setup
   - Longer initial build times
   - Less control over build process

6. **Bump API level to 26**

   Official dropped support for API 24-25. Consider if you need to support Android 7.0.

---

## Build Scripts Reference

| Script | Purpose |
|--------|---------|
| `script/setup-jami-build.sh` | Environment setup (source before building) |
| `script/build-libjami.sh` | Build arm64-v8a libraries |
| `script/build-complete-x86_64.sh` | Full x86_64 build (contrib + daemon) |
| `script/build-libjami-x86_64.sh` | Build x86_64 daemon only |
| `script/link-jni.sh` | Manual JNI linking (if needed) |
| `script/create-x86_64-toolchain-wrappers.sh` | Toolchain setup for x86_64 |

---

## Daemon Version Notes

Our daemon (ddfc17622896) includes:
- **SELinux fix for libgit2** (Jan 17): Fixes hard link denial issues on Android

Official client's daemon (0a48dd32c583) includes:
- jamiaccount: check for peer certificate (Jan 14)

Our fork is 3 days newer with the SELinux fix which may be important for Android compatibility.
