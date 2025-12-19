#!/bin/bash
set -e

# Build script for Jami daemon for Android x86_64 (emulators)

# Android configuration for x86_64
export ANDROID_SDK_ROOT=/home/mandroid/Android/Sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/27.0.12077973
export ANDROID_NDK=$ANDROID_NDK_HOME
export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME
export ANDROID_ABI=x86_64
export ANDROID_API=24

# Add NDK toolchain to PATH
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

# Jami daemon paths
JAMI_DIR=/home/mandroid/Videos/letsJam/jami-daemon
BUILD_DIR=$JAMI_DIR/build-android-x86_64
CONTRIB_DIR=$JAMI_DIR/contrib/x86_64-linux-android

echo "========================================="
echo "Building Jami Daemon for x86_64"
echo "========================================="
echo "ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo "ANDROID_ABI:      $ANDROID_ABI"
echo "ANDROID_API:      $ANDROID_API"
echo "BUILD_DIR:        $BUILD_DIR"
echo "CONTRIB_DIR:      $CONTRIB_DIR"
echo "========================================="

# Step 1: Build contrib dependencies
echo ""
echo "Step 1/3: Building contrib dependencies..."
cd $JAMI_DIR/contrib
mkdir -p native-x86_64-linux-android
cd native-x86_64-linux-android

# Run bootstrap if Makefile doesn't exist
if [ ! -f Makefile ]; then
    echo "Running bootstrap..."
    ../bootstrap --host=x86_64-linux-android --disable-downloads
fi

echo "Building dependencies (this may take a while)..."
make -j$(nproc)

# Step 2: Build jami-daemon
echo ""
echo "Step 2/3: Building jami-daemon..."
mkdir -p $BUILD_DIR
cd $BUILD_DIR

# Set PKG_CONFIG_PATH
export PKG_CONFIG_PATH=$CONTRIB_DIR/lib/pkgconfig

echo "Running cmake..."
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

echo "Building jami-daemon..."
make -j$(nproc)

# Step 3: Copy libraries to Android app
echo ""
echo "Step 3/3: Copying libraries to Android app..."
JNILIB_DIR=/home/mandroid/Videos/letsJam/androidApp/src/main/jniLibs/x86_64
mkdir -p $JNILIB_DIR

echo "Copying libjami.so..."
cp $BUILD_DIR/libjami.so $JNILIB_DIR/

# Also copy C++ shared library if needed
if [ -f $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so ]; then
    echo "Copying libc++_shared.so..."
    cp $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so $JNILIB_DIR/
fi

echo ""
echo "========================================="
echo "Build complete!"
echo "========================================="
echo "Libraries installed at:"
ls -lh $JNILIB_DIR/
echo "========================================="
