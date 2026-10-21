#!/bin/bash
set -euo pipefail

# Find NDK
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "$HOME/Android/android-ndk-r26d" ]; then
        export ANDROID_NDK_HOME="$HOME/Android/android-ndk-r26d"
    elif [ -d "$HOME/Android/Sdk/ndk-bundle" ]; then
        export ANDROID_NDK_HOME="$HOME/Android/Sdk/ndk-bundle"
    else
        echo "Error: ANDROID_NDK_HOME is not set and no NDK directory was found."
        exit 1
    fi
fi

export PATH="$HOME/.cargo/bin:$PATH"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JNILIBS_DIR="$PROJECT_DIR/apps/pdfcraft-android/app/src/main/jniLibs"

echo "=== Building PdfCraft Native JNI libraries ==="
echo "Project directory: $PROJECT_DIR"
echo "NDK path: $ANDROID_NDK_HOME"
echo "Target jniLibs: $JNILIBS_DIR"

mkdir -p "$JNILIBS_DIR"

cd "$PROJECT_DIR"
cargo ndk \
    -t arm64-v8a \
    -t x86_64 \
    -o "$JNILIBS_DIR" \
    build --release -p pdfcraft-jni

echo "=== Native build complete! Shared libraries in $JNILIBS_DIR ==="
ls -lh "$JNILIBS_DIR"/*/*.so
