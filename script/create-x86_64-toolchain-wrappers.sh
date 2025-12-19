#!/bin/bash
# Create toolchain wrapper scripts for x86_64 Android builds
# Maps GCC-style names to Clang names for NDK r27

set -e

NDK_BIN=/home/mandroid/Android/Sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/bin
WRAPPER_DIR=/home/mandroid/Videos/letsJam/jami-daemon/contrib/x86_64-linux-android/bin
API_LEVEL=24

echo "Creating toolchain wrappers in $WRAPPER_DIR"
mkdir -p $WRAPPER_DIR

# Create gcc wrapper -> clang
cat > $WRAPPER_DIR/x86_64-linux-android-gcc << EOF
#!/bin/bash
exec $NDK_BIN/x86_64-linux-android${API_LEVEL}-clang "\$@"
EOF
chmod +x $WRAPPER_DIR/x86_64-linux-android-gcc

# Create g++ wrapper -> clang++
cat > $WRAPPER_DIR/x86_64-linux-android-g++ << EOF
#!/bin/bash
exec $NDK_BIN/x86_64-linux-android${API_LEVEL}-clang++ "\$@"
EOF
chmod +x $WRAPPER_DIR/x86_64-linux-android-g++

# Create ar, ld, ranlib, strip wrappers (use llvm- versions)
for tool in ar ld ranlib strip objdump; do
    cat > $WRAPPER_DIR/x86_64-linux-android-$tool << EOF
#!/bin/bash
exec $NDK_BIN/llvm-$tool "\$@"
EOF
    chmod +x $WRAPPER_DIR/x86_64-linux-android-$tool
done

# Create as wrapper -> clang with -c
cat > $WRAPPER_DIR/x86_64-linux-android-as << EOF
#!/bin/bash
exec $NDK_BIN/x86_64-linux-android${API_LEVEL}-clang -c "\$@"
EOF
chmod +x $WRAPPER_DIR/x86_64-linux-android-as

echo "Toolchain wrappers created successfully!"
ls -l $WRAPPER_DIR/
