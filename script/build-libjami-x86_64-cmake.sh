#!/bin/bash
set -e

# Source environment
source /home/mandroid/Videos/letsJam/script/setup-jami-build.sh

JAMI_DIR=/home/mandroid/Videos/letsJam/jami-daemon
BUILD_DIR=$JAMI_DIR/build-android-x86_64
CONTRIB_DIR=$JAMI_DIR/contrib/x86_64-linux-android

# Create build directory
mkdir -p $BUILD_DIR
cd $BUILD_DIR

# Set PKG_CONFIG_PATH
export PKG_CONFIG_PATH=$CONTRIB_DIR/lib/pkgconfig

echo "Running cmake for x86_64..."
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

echo "Building..."
make -j$(nproc)

echo "Done! Libraries location:"
ls -la $BUILD_DIR/*.so

# Copy to jniLibs
JNILIBS_DIR=/home/mandroid/Videos/letsJam/androidApp/src/main/jniLibs/x86_64
echo "Copying to $JNILIBS_DIR..."
cp $BUILD_DIR/libjami-core.so $JNILIBS_DIR/
cp $BUILD_DIR/libjami-core-jni.so $JNILIBS_DIR/
cp $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so $JNILIBS_DIR/

echo "Final libraries:"
ls -lh $JNILIBS_DIR/
