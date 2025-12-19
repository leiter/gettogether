#!/bin/bash
# Setup script for building jami-daemon for Android
# Source this file before building: source script/setup-jami-build.sh

export ANDROID_SDK_ROOT=/home/mandroid/Android/Sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/27.0.12077973
export ANDROID_NDK=$ANDROID_NDK_HOME
export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME

# Android build configuration
export ANDROID_ABI=arm64-v8a
export ANDROID_API=24

# Add NDK toolchain to PATH
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

# Add extras/tools to PATH
export PATH=/home/mandroid/Videos/letsJam/jami-daemon/extras/tools/build/bin:$PATH

export JAMI_DAEMON_DIR=/home/mandroid/Videos/letsJam/jami-daemon

echo "Environment configured:"
echo "  ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "  ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo "  ANDROID_ABI:      $ANDROID_ABI"
echo "  ANDROID_API:      $ANDROID_API"
echo "  JAMI_DAEMON_DIR:  $JAMI_DAEMON_DIR"
