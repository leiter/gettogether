# x86_64 Emulator Build Status - 2025-12-19

## Summary
Attempted to build Jami C libraries for x86_64 architecture (Android emulator support). The build process completed successfully for all dependencies but encountered a TLS (Thread-Local Storage) linker error when building the final Jami daemon library.

## Current Status: BLOCKED

### ✅ Completed Successfully
1. **Build script fixes**
   - Fixed toolchain wrapper creation order (must run AFTER bootstrap)
   - Fixed libarchive build (disabled ENABLE_UNZIP to prevent executable build failures)
   - Created complete build script: `script/build-complete-x86_64.sh`

2. **All dependencies built** (~40 packages)
   - argon2, asio, dhtnet, ffmpeg, fmt, freetype, gmp, gnutls, gsm
   - http_parser, iconv, jack, jsoncpp, libarchive, libgit2, libressl
   - liburcu, llhttp, lttng-ust, minizip, msgpack, natpmp, nettle
   - onnxruntime (took 18+ min to compress tarball with xz)
   - opencv_contrib, opencv, opendht, opus, pjproject, portaudio
   - restinio, sdbus-cpp, secp256k1, simdutf, speex, speexdsp
   - upnp, vpx, webrtc-audio-processing, x264, yaml-cpp, zlib
   - All installed to: `jami-daemon/contrib/x86_64-linux-android/`

3. **Jami daemon compilation**
   - CMake configuration: ✅ Successful
   - C++ compilation: ✅ All 97% of files compiled successfully
   - Only failed at final linking step

### ❌ Blocking Issue: TLS Linker Error

**Error:**
```
ld.lld: error: undefined symbol: __tls_get_addr
>>> referenced by opendht headers (dht::Hash, dht::Dht, etc.)
>>> referenced 44+ times
```

**Root Cause:**
- Android NDK's LLVM linker doesn't provide `__tls_get_addr` for emulated TLS
- OpenDHT library (and possibly others) use Thread-Local Storage
- Need to rebuild ALL dependencies with `-fno-emulated-tls` compiler flag

**Attempted Fixes:**
1. ❌ Added `-fno-emulated-tls` to Jami daemon CMake flags only
   - Doesn't work: dependencies already compiled without flag
2. ❌ Attempted to rebuild just opendht with TLS flag
   - Would need to rebuild all its dependencies too

## Options Going Forward

### Option 1: Use arm64-v8a (Physical Devices) ✅ **RECOMMENDED**
**Status:** Already working!
- Libraries exist at: `androidApp/src/main/jniLibs/arm64-v8a/`
- Tested and functional on physical devices
- **Action:** Continue development with physical device testing

### Option 2: Full Rebuild with TLS Fix (Time-Intensive)
**Estimated time:** 2-3 hours
**Steps:**
1. Clean all x86_64 dependencies
2. Modify contrib build system to add `-fno-emulated-tls` globally
3. Rebuild all ~40 dependencies from scratch
4. Rebuild Jami daemon

**Implementation:**
```bash
# Add to jami-daemon/contrib/src/main.mak after line 100:
export CFLAGS += -fno-emulated-tls
export CXXFLAGS += -fno-emulated-tls

# Then rebuild everything:
cd jami-daemon/contrib/native-x86_64-linux-android
make distclean
../bootstrap --host=x86_64-linux-android
make -j8
```

### Option 3: Use Prebuilt Libraries (If Available)
- Check if Jami project provides prebuilt x86_64 libraries
- Download and integrate if available

### Option 4: Defer x86_64 Support
- Document as "future enhancement"
- Focus on arm64-v8a (works) and other priorities
- Revisit when needed or when NDK/Jami updates address TLS

## Build Environment Details
- **NDK Version:** 27.0.12077973
- **Android API Level:** 24
- **Build Host:** x86_64-linux-gnu
- **Toolchain:** Clang 18.0.1
- **Build Started:** 2025-12-19 18:40
- **Dependencies Completed:** 2025-12-19 ~19:30
- **Final Status:** TLS linker error at 19:45+

## Files Modified
1. `jami-daemon/contrib/src/libarchive/rules.mak` - Added `-DENABLE_UNZIP=OFF`
2. `script/build-complete-x86_64.sh` - Fixed toolchain wrapper order
3. `script/create-x86_64-toolchain-wrappers.sh` - Created wrapper scripts

## Key Learnings
1. **onnxruntime tarball creation** takes 18+ minutes (1.9GB → 1.5GB xz compressed)
2. **Toolchain wrappers must be created AFTER bootstrap**, not before
3. **TLS is a known issue** with Android NDK x86_64 builds
4. **libarchive executables fail to build** for Android - must disable UNZIP/TAR/CPIO

## Recommendation
**Use Option 1** (arm64-v8a for physical devices) and defer x86_64 emulator support. The TLS fix requires significant time investment for a feature that's primarily used for development/testing, while physical device testing is already working.

If emulator testing becomes critical, pursue Option 2 with a dedicated time allocation for the full rebuild.
