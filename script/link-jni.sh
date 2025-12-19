#!/bin/bash
set -e
source /home/mandroid/Videos/letsJam/script/setup-jami-build.sh

BUILD_DIR=/home/mandroid/Videos/letsJam/jami-daemon/build-android-arm64-v8a
CONTRIB=/home/mandroid/Videos/letsJam/jami-daemon/contrib/aarch64-linux-android

cd $BUILD_DIR

echo "Linking libjami-core-jni.so with FFmpeg libraries..."

aarch64-linux-android24-clang++ -shared \
  -o libjami-core-jni.so \
  CMakeFiles/jami-core-jni.dir/bin/jni/jami_wrapper.cpp.o \
  -L. -ljami-core \
  -L$CONTRIB/lib \
  -lavcodec -lavutil -lavformat -lswresample -lswscale -lavfilter -lavdevice \
  -llog -landroid \
  -Wl,-rpath,'$ORIGIN'

echo "Done!"
ls -la libjami-core-jni.so
