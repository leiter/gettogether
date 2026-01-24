#!/bin/bash
set -e

# Build jami-daemon for iOS devices
# Usage: ./script/build-libjami-ios.sh [arm64|x86_64]
#   arm64  - Build for iOS devices (default)
#   x86_64 - Build for iOS Simulator

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAMI_DIR="$PROJECT_ROOT/jami-daemon"

# Parse architecture argument
ARCH=${1:-arm64}

if [ "$ARCH" = "x86_64" ]; then
    echo "Building for iOS Simulator (x86_64)"
    export IOS_TARGET_PLATFORM=iPhoneSimulator
    SDK_NAME="iphonesimulator"
    HOST="x86_64-apple-darwin"
elif [ "$ARCH" = "sim-arm64" ]; then
    echo "Building for iOS Simulator (arm64)"
    export IOS_TARGET_PLATFORM=iPhoneSimulator
    SDK_NAME="iphonesimulator"
    HOST="aarch64-apple-darwin"
    ARCH="arm64"
else
    echo "Building for iOS Device (arm64)"
    export IOS_TARGET_PLATFORM=iPhoneOS
    SDK_NAME="iphoneos"
    # Use aarch64-apple-darwin to distinguish from build machine (arm64-apple-darwin)
    # This ensures configure scripts recognize cross-compilation
    # The get-arch.sh script will convert aarch64 to arm64 for Darwin
    HOST="aarch64-apple-darwin"
    ARCH="arm64"
fi

# iOS SDK configuration (not exported until after extras/tools build)
SDK_VERSION=$(xcrun --sdk $SDK_NAME --show-sdk-version)
IOS_SDK=$(xcrun --sdk $SDK_NAME --show-sdk-path)
MIN_IOS_VERSION=${MIN_IOS_VERSION:-15.0}

# Build machine
BUILD=$(uname -m)-apple-darwin

# Contrib and build directories
# Use a clear directory name for iOS builds
if [ "$SDK_NAME" = "iphonesimulator" ]; then
    CONTRIB_DIR="$JAMI_DIR/contrib/ios-simulator-${ARCH}"
    BUILD_DIR="$JAMI_DIR/build-ios-simulator-${ARCH}"
else
    CONTRIB_DIR="$JAMI_DIR/contrib/ios-device-${ARCH}"
    BUILD_DIR="$JAMI_DIR/build-ios-device-${ARCH}"
fi

echo "========================================"
echo "iOS Build Configuration"
echo "========================================"
echo "ARCH:               $ARCH"
echo "HOST:               $HOST"
echo "BUILD:              $BUILD"
echo "SDK:                $SDK_NAME"
echo "SDK_VERSION:        $SDK_VERSION"
echo "IOS_SDK:            $IOS_SDK"
echo "MIN_IOS_VERSION:    $MIN_IOS_VERSION"
echo "CONTRIB_DIR:        $CONTRIB_DIR"
echo "BUILD_DIR:          $BUILD_DIR"
echo "========================================"

# Step 1: Build extras/tools if needed (using native compiler, not iOS SDK)
echo ""
echo "Step 1: Checking extras/tools..."
EXTRAS_TOOLS_DIR="$JAMI_DIR/extras/tools"
if [ ! -d "$EXTRAS_TOOLS_DIR/build/bin" ]; then
    echo "Building extras/tools (for host machine)..."
    # Save and clear iOS-related environment variables
    SAVED_CC="$CC"
    SAVED_CXX="$CXX"
    SAVED_SDKROOT="$SDKROOT"
    SAVED_CFLAGS="$CFLAGS"
    SAVED_CXXFLAGS="$CXXFLAGS"
    SAVED_LDFLAGS="$LDFLAGS"
    unset CC CXX SDKROOT CFLAGS CXXFLAGS LDFLAGS

    pushd "$EXTRAS_TOOLS_DIR" > /dev/null
    ./bootstrap
    # Build sequentially to ensure proper dependencies (m4 -> autoconf -> automake -> libtool)
    make
    popd > /dev/null

    # Restore environment
    export CC="$SAVED_CC"
    export CXX="$SAVED_CXX"
    export SDKROOT="$SAVED_SDKROOT"
    export CFLAGS="$SAVED_CFLAGS"
    export CXXFLAGS="$SAVED_CXXFLAGS"
    export LDFLAGS="$SAVED_LDFLAGS"
else
    echo "extras/tools already built"
fi
export PATH="$EXTRAS_TOOLS_DIR/build/bin:$PATH"

# Step 2: Build contrib dependencies
echo ""
echo "Step 2: Building contrib dependencies..."

# Now export iOS-specific environment variables
export BUILDFORIOS=1
export IOS_TARGET_PLATFORM
export SDK_VERSION
export SDKROOT="$IOS_SDK"
export IOS_SDK

mkdir -p "$CONTRIB_DIR"
pushd "$CONTRIB_DIR" > /dev/null

if [ ! -f "config.mak" ] || [ ! -f ".stamp-install" ]; then
    echo "Bootstrapping contrib for iOS..."

    # Set compiler environment for iOS cross-compilation
    export CC="xcrun -sdk $SDK_NAME clang"
    export CXX="xcrun -sdk $SDK_NAME clang++"
    export AR="xcrun -sdk $SDK_NAME ar"
    export LD="xcrun -sdk $SDK_NAME ld"
    export RANLIB="xcrun -sdk $SDK_NAME ranlib"
    export STRIP="xcrun -sdk $SDK_NAME strip"

    # IMPORTANT: Set native compilers for build-time tools (needed by GMP, etc.)
    # These run on the build machine, not the target
    export CC_FOR_BUILD="/usr/bin/clang"
    export CXX_FOR_BUILD="/usr/bin/clang++"
    export BUILD_CC="/usr/bin/clang"
    export BUILD_CXX="/usr/bin/clang++"

    ../bootstrap --build=$BUILD --host=$HOST

    echo "Building contrib (this may take a while)..."
    make -j$(sysctl -n hw.ncpu)
    touch .stamp-install
else
    echo "contrib already built"
fi
popd > /dev/null

# Step 3: Build libjami
echo ""
echo "Step 3: Building libjami..."
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Set PKG_CONFIG_PATH for cmake to find contrib
export PKG_CONFIG_PATH="$CONTRIB_DIR/lib/pkgconfig:$PKG_CONFIG_PATH"

# iOS cross-compilation flags
DEPLOYMENT_TARGET_FLAG="-miphoneos-version-min=$MIN_IOS_VERSION"
if [ "$SDK_NAME" = "iphonesimulator" ]; then
    DEPLOYMENT_TARGET_FLAG="-mios-simulator-version-min=$MIN_IOS_VERSION"
fi

echo "Running cmake..."
cmake "$JAMI_DIR" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=$MIN_IOS_VERSION \
    -DCMAKE_OSX_ARCHITECTURES=$ARCH \
    -DCMAKE_OSX_SYSROOT=$SDKROOT \
    -DCMAKE_C_COMPILER=$(xcrun --sdk $SDK_NAME -f clang) \
    -DCMAKE_CXX_COMPILER=$(xcrun --sdk $SDK_NAME -f clang++) \
    -DCMAKE_C_FLAGS="${DEPLOYMENT_TARGET_FLAG}" \
    -DCMAKE_CXX_FLAGS="${DEPLOYMENT_TARGET_FLAG} -stdlib=libc++" \
    -DCMAKE_PREFIX_PATH="$CONTRIB_DIR" \
    -DJAMI_VIDEO=ON \
    -DJAMI_PLUGIN=OFF \
    -DJAMI_DBUS=OFF \
    -DJAMI_TESTS=OFF \
    -DBUILD_TESTING=OFF \
    -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_BUILD_TYPE=Release

echo "Building libjami..."
make -j$(sysctl -n hw.ncpu)

# Step 4: Copy to output location
echo ""
echo "Step 4: Copying to output location..."
OUTPUT_DIR="$PROJECT_ROOT/iosApp/Frameworks/libjami-${ARCH}"
mkdir -p "$OUTPUT_DIR"

if [ -f "$BUILD_DIR/libjami.a" ]; then
    cp "$BUILD_DIR/libjami.a" "$OUTPUT_DIR/"
    echo "Static library copied to: $OUTPUT_DIR/libjami.a"
elif [ -f "$BUILD_DIR/libjami-core.a" ]; then
    cp "$BUILD_DIR/libjami-core.a" "$OUTPUT_DIR/libjami.a"
    echo "Static library copied to: $OUTPUT_DIR/libjami.a"
elif [ -f "$BUILD_DIR/libring.a" ]; then
    cp "$BUILD_DIR/libring.a" "$OUTPUT_DIR/libjami.a"
    echo "Static library copied to: $OUTPUT_DIR/libjami.a"
fi

# Copy headers
HEADERS_DIR="$OUTPUT_DIR/include"
mkdir -p "$HEADERS_DIR"
cp -r "$JAMI_DIR/src/jami"/*.h "$HEADERS_DIR/" 2>/dev/null || true
echo "Headers copied to: $HEADERS_DIR"

echo ""
echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo "Output: $OUTPUT_DIR"
echo ""
echo "To create a universal (fat) library for device + simulator:"
echo "  1. Run: ./script/build-libjami-ios.sh arm64"
echo "  2. Run: ./script/build-libjami-ios.sh x86_64"
echo "  3. Run: lipo -create -output libjami-universal.a \\"
echo "          iosApp/Frameworks/libjami-arm64/libjami.a \\"
echo "          iosApp/Frameworks/libjami-x86_64/libjami.a"
echo "========================================"
