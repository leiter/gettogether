#!/bin/bash
set -e

# Source environment
source /home/mandroid/Videos/letsJam/script/setup-jami-build.sh

JAMI_DIR=/home/mandroid/Videos/letsJam/jami-daemon
BUILD_DIR=$JAMI_DIR/build-android-arm64-v8a
CONTRIB_DIR=$JAMI_DIR/contrib/aarch64-linux-android

# Create build directory
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
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release

echo "Building..."
make -j$(nproc)

echo "Done! libjami.so location:"
ls -la $BUILD_DIR/libjami.so
