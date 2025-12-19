# CMakeLists.txt for Get-Together Android JNI Bridge
#
# This CMake configuration builds the JNI wrapper that bridges Kotlin/Java code
# with the native Jami daemon library.
#
# Prerequisites:
# 1. SWIG 4.0+ must be installed
# 2. jami-daemon must be built for the target Android ABI
# 3. All jami-daemon dependencies must be available
#
# Build steps (to be run before Gradle build):
# 1. cd jami-daemon
# 2. Build contrib: cd extras/tools && ./bootstrap && make
# 3. Build daemon: mkdir build-android && cd build-android
#    cmake .. -DJAMI_JNI=ON -DJAMI_JNI_PACKAGEDIR=com/gettogether/app/jami \
#             -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
#             -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24
# 4. make -j$(nproc)

cmake_minimum_required(VERSION 3.18.1)
project("gettogether_jni" VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android-specific settings
if(ANDROID)
    message(STATUS "Building for Android ABI: ${ANDROID_ABI}")
    message(STATUS "Android NDK: ${ANDROID_NDK}")
    message(STATUS "Android Platform: ${ANDROID_PLATFORM}")
endif()

# Path to jami-daemon
set(JAMI_DAEMON_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../jami-daemon")

# Check if SWIG-generated wrapper exists
set(JAMI_WRAPPER_CPP "${JAMI_DAEMON_DIR}/bin/jni/jami_wrapper.cpp")
if(EXISTS "${JAMI_WRAPPER_CPP}")
    message(STATUS "Found SWIG-generated wrapper: ${JAMI_WRAPPER_CPP}")
    set(USE_JAMI_WRAPPER ON)
else()
    message(WARNING "SWIG wrapper not found. Using stub implementation.")
    message(WARNING "To generate, run: cd ${JAMI_DAEMON_DIR}/bin/jni && ./make-swig.sh")
    set(USE_JAMI_WRAPPER OFF)
endif()

# Source files
set(JNI_SOURCES
    jami_jni_stub.cpp
)

if(USE_JAMI_WRAPPER)
    list(APPEND JNI_SOURCES "${JAMI_WRAPPER_CPP}")
endif()

# Create the shared library
add_library(jami_jni SHARED ${JNI_SOURCES})

# Include directories
target_include_directories(jami_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${JAMI_DAEMON_DIR}/src
    ${JAMI_DAEMON_DIR}/src/jami
)

# If jami-daemon is built, link against it
set(JAMI_LIB_DIR "${JAMI_DAEMON_DIR}/build-android-${ANDROID_ABI}")
if(EXISTS "${JAMI_LIB_DIR}/libjami.so")
    message(STATUS "Found pre-built jami library: ${JAMI_LIB_DIR}/libjami.so")

    # Add jami library
    add_library(jami SHARED IMPORTED)
    set_target_properties(jami PROPERTIES
        IMPORTED_LOCATION "${JAMI_LIB_DIR}/libjami.so"
    )

    target_link_libraries(jami_jni PRIVATE
        jami
        android
        log
    )
else()
    message(STATUS "jami library not found. Building stub-only version.")
    target_link_libraries(jami_jni PRIVATE
        android
        log
    )
    target_compile_definitions(jami_jni PRIVATE JAMI_STUB_ONLY)
endif()

# Compiler flags
target_compile_options(jami_jni PRIVATE
    -Wall
    -Wextra
    -fexceptions
    -frtti
)

# Strip symbols in release build
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(jami_jni PRIVATE -s)
endif()
