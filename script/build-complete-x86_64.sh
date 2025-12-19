#!/bin/bash
set -e

echo "=================================="
echo "Complete x86_64 Jami Build Script"
echo "=================================="

# Set all required environment variables
export ANDROID_SDK_ROOT=/home/mandroid/Android/Sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/27.0.12077973
export ANDROID_NDK=$ANDROID_NDK_HOME
export ANDROID_ABI=x86_64
export ANDROID_API=24

JAMI_DIR=/home/mandroid/Videos/letsJam/jami-daemon
CONTRIB_DIR=$JAMI_DIR/contrib/native-x86_64-linux-android
INSTALL_PREFIX=$JAMI_DIR/contrib/x86_64-linux-android

# Add both NDK bin and toolchain wrappers to PATH
export PATH=$INSTALL_PREFIX/bin:$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

echo "Environment:"
echo "  NDK: $ANDROID_NDK_HOME"
echo "  ABI: $ANDROID_ABI"
echo "  API: $ANDROID_API"
echo ""

# Bootstrap contrib
echo "Step 1: Bootstrapping contrib system..."
cd $JAMI_DIR/contrib
mkdir -p native-x86_64-linux-android
cd native-x86_64-linux-android

if [ -f Makefile ]; then
    echo "Makefile exists, cleaning first..."
    make distclean || true
fi

../bootstrap --host=x86_64-linux-android

# Create toolchain wrappers AFTER bootstrap
echo ""
echo "Step 2: Creating toolchain wrappers..."
/home/mandroid/Videos/letsJam/script/create-x86_64-toolchain-wrappers.sh

# Fetch all tarballs
echo ""
echo "Step 3: Fetching source tarballs..."
make fetch-all

# Build all dependencies
echo ""
echo "Step 4: Building dependencies (this will take 30-60 minutes)..."
make -j8

echo ""
echo "=================================="
echo "Contrib dependencies build complete!"
echo "=================================="
echo ""
echo "Next: Building Jami daemon itself..."

# Now build the Jami daemon
BUILD_DIR=$JAMI_DIR/build-android-x86_64
mkdir -p $BUILD_DIR
cd $BUILD_DIR

export PKG_CONFIG_PATH=$INSTALL_PREFIX/lib/pkgconfig

echo ""
echo "Step 5: Configuring Jami daemon with CMake..."
cmake $JAMI_DIR \
    -DJAMI_JNI=ON \
    -DJAMI_JNI_PACKAGEDIR=/home/mandroid/Videos/letsJam/shared/src/androidMain/java \
    -DJAMI_VIDEO=ON \
    -DJAMI_PLUGIN=OFF \
    -DJAMI_DBUS=OFF \
    -DJAMI_TESTS=OFF \
    -DBUILD_TESTING=OFF \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=x86_64 \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release

echo ""
echo "Step 6: Building Jami daemon..."
make -j8

echo ""
echo "Step 7: Installing libraries to Android app..."
JNILIB_DIR=/home/mandroid/Videos/letsJam/androidApp/src/main/jniLibs/x86_64
mkdir -p $JNILIB_DIR

cp $BUILD_DIR/libjami.so $JNILIB_DIR/
echo "Copied: libjami.so"

# Copy C++ shared library
if [ -f $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so ]; then
    cp $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so $JNILIB_DIR/
    echo "Copied: libc++_shared.so"
fi

echo ""
echo "=================================="
echo "BUILD COMPLETE!"
echo "=================================="
echo ""
echo "Libraries installed at:"
ls -lh $JNILIB_DIR/
echo ""
echo "Next steps:"
echo "1. Rebuild the Android app: ./gradlew assembleDebug"
echo "2. Install on emulator: adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk"
echo "3. Test the app on x86_64 emulators"
