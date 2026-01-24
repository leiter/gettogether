#!/bin/bash
# Setup script for building jami-daemon for iOS on macOS
# Source this file before building: source script/setup-jami-build-ios.sh

# Get the script directory and set paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
export JAMI_DAEMON_DIR=$PROJECT_ROOT/jami-daemon

# iOS build configuration
export BUILDFORIOS=1

# Target architecture (arm64 for iOS devices)
export ARCH=${ARCH:-arm64}

# iOS SDK configuration
export IOS_TARGET_PLATFORM=iPhoneOS
export SDK_VERSION=$(xcrun --sdk iphoneos --show-sdk-version)
export IOS_SDK=$(xcrun --sdk iphoneos --show-sdk-path)
export MIN_IOS_VERSION=${MIN_IOS_VERSION:-15.0}

# Host triple for iOS device
export HOST=${ARCH}-apple-darwin

# Build machine
export BUILD=$(uname -m)-apple-darwin

# Xcode toolchain
export DEVELOPER_DIR=$(xcode-select -p)
export CC="xcrun -sdk iphoneos clang"
export CXX="xcrun -sdk iphoneos clang++"
export AR="xcrun -sdk iphoneos ar"
export LD="xcrun -sdk iphoneos ld"
export RANLIB="xcrun -sdk iphoneos ranlib"
export STRIP="xcrun -sdk iphoneos strip"
export NM="xcrun -sdk iphoneos nm"
export LIPO="xcrun -sdk iphoneos lipo"
export LIBTOOL="xcrun -sdk iphoneos libtool"

# Compiler flags
export CFLAGS="-arch ${ARCH} -isysroot ${IOS_SDK} -miphoneos-version-min=${MIN_IOS_VERSION}"
export CXXFLAGS="-arch ${ARCH} -isysroot ${IOS_SDK} -miphoneos-version-min=${MIN_IOS_VERSION} -stdlib=libc++"
export LDFLAGS="-arch ${ARCH} -isysroot ${IOS_SDK} -miphoneos-version-min=${MIN_IOS_VERSION}"

# Add extras/tools to PATH if built
if [ -d "$JAMI_DAEMON_DIR/extras/tools/build/bin" ]; then
    export PATH=$JAMI_DAEMON_DIR/extras/tools/build/bin:$PATH
fi

echo "iOS build environment configured:"
echo "  PROJECT_ROOT:       $PROJECT_ROOT"
echo "  JAMI_DAEMON_DIR:    $JAMI_DAEMON_DIR"
echo "  ARCH:               $ARCH"
echo "  IOS_SDK:            $IOS_SDK"
echo "  SDK_VERSION:        $SDK_VERSION"
echo "  MIN_IOS_VERSION:    $MIN_IOS_VERSION"
echo "  HOST:               $HOST"
echo "  BUILD:              $BUILD"
