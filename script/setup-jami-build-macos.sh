#!/bin/bash
# Setup script for building jami-daemon for Android on macOS
# Source this file before building: source script/setup-jami-build-macos.sh

export ANDROID_SDK_ROOT=~/Library/Android/sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/27.0.12077973
export ANDROID_NDK=$ANDROID_NDK_HOME
export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME

# Android build configuration
export ANDROID_ABI=arm64-v8a
export ANDROID_API=24

# macOS-specific: use darwin-x86_64 toolchain (fat binary, works on Apple Silicon)
export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin:$PATH

# Get the script directory and set JAMI_DAEMON_DIR relative to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
export JAMI_DAEMON_DIR=$PROJECT_ROOT/jami-daemon

# Add extras/tools to PATH if built
if [ -d "$JAMI_DAEMON_DIR/extras/tools/build/bin" ]; then
    export PATH=$JAMI_DAEMON_DIR/extras/tools/build/bin:$PATH
fi

echo "Environment configured (macOS):"
echo "  ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "  ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo "  ANDROID_ABI:      $ANDROID_ABI"
echo "  ANDROID_API:      $ANDROID_API"
echo "  JAMI_DAEMON_DIR:  $JAMI_DAEMON_DIR"
echo "  PROJECT_ROOT:     $PROJECT_ROOT"
