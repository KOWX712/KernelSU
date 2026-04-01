#!/bin/bash
set -e

# Minimal: ./build.sh ksud
# Full: ./build.sh ksuinit lkm all
# Specific: ./build.sh ksuinit lkm <kmi-version>

if [ ! -d "out" ]; then
    mkdir out
    echo "*" > out/.gitignore
    echo "\033[32mTips: copy this script to out/build.sh for clean workspace, run with bash out/build.sh\033[0m"
fi

# Signing key for manager
if [ ! -f "out/sign.properties" ]; then
    echo "Error: out/sign.properties not found, please fill it with your signing information"
    cat "manager/sign.example.properties" > "out/sign.properties"
    exit 1
fi
. out/sign.properties
export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_FILE"
export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"

# Find ndk
if [ -z "$ANDROID_NDK_HOME" ]; then
    SDK_PATH="$ANDROID_HOME"
    [ -z "$SDK_PATH" ] && [ -d "$HOME/Library/Android/sdk" ] && SDK_PATH="$HOME/Library/Android/sdk"
    [ -z "$SDK_PATH" ] && [ -d "$HOME/Android/Sdk" ] && SDK_PATH="$HOME/Android/Sdk"
    [ -z "$SDK_PATH" ] && echo "Error: ANDROID_HOME is not set, please set it to your Android SDK path" && exit 1

    [ ! -d "$SDK_PATH/ndk" ] && echo "Error: NDK not found in $SDK_PATH" && exit 1
    LATEST_NDK="$(ls -1 "$SDK_PATH/ndk" | sort -V | tail -n 1)"
    [ -z "$LATEST_NDK" ] && echo "Error: No NDK found in $SDK_PATH" && exit 1
    export ANDROID_NDK_HOME="$SDK_PATH/ndk/$LATEST_NDK"
fi

TARGET="aarch64-linux-android"
source .github/scripts/setup-rust-build.sh "$TARGET" 26
export PATH="$LLVM_BIN:$HOME/.cargo/bin:$PATH"

DIR="$(pwd)"
GRADLE_FLAG=""
DDK_RELEASE="$(grep -oP 'ddk_release.*?\K[0-9]+' .github/workflows/build-lkm.yml)"
VALID_KMIS="$(grep android .github/workflows/build-lkm.yml | sed 's/.*- android/android/g')"

BUILD_KSUD=0
BUILD_KSUINIT=0
BUILD_LKM=""

check_kmi() {
    local kmi="$1"
    for valid in $VALID_KMIS; do
        if [[ "$kmi" == "$valid" ]]; then
            return 0
        fi
    done
    return 1
}

build_lkm() {
    local kmi="$1"

    echo "=== Building kernelsu.ko for KMI: $kmi (DDK: $DDK_RELEASE) ==="

    docker run --rm --privileged -v "$DIR:/workspace" -w /workspace \
        ghcr.io/ylarod/ddk-min:$kmi-$DDK_RELEASE /bin/bash -c "
            set -e
            git config --global --add safe.directory /workspace
            cd kernel
            CONFIG_KSU=m CC=clang make
            cp kernelsu.ko ../out/${kmi}_kernelsu.ko
            cp kernelsu.ko ../userspace/ksud/bin/aarch64/${kmi}_kernelsu.ko
            echo 'Built: ../out/${kmi}_kernelsu.ko'
        "
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        clean)
            rm -rf out/*.apk out/*.ko
            # GRADLE_FLAG="clean"
            DDK_IMAGES=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^ghcr.io/ylarod/ddk-min:")
            if [ -n "$DDK_IMAGES" ]; then
                echo "$DDK_IMAGES" | xargs docker rmi
            fi
            exit 0
            ;;
        ksud) BUILD_KSUD=1; shift;;
        ksuinit) BUILD_KSUINIT=1; shift;;
        lkm)
            if [[ -z "$2" ]]; then
                echo "Error: lkm requires a KMI version or 'all'"
                echo "Usage: $0 lkm <kmi-version|all>"
                echo "Valid KMI versions: $VALID_KMIS"
                exit 1
            fi
            if [[ "$2" == "all" ]]; then
                BUILD_LKM="all"
            else
                if ! check_kmi "$2"; then
                    echo "Error: Invalid KMI version '$2'"
                    echo "Valid KMI versions: $VALID_KMIS"
                    exit 1
                fi
                BUILD_LKM="$2"
            fi
            shift 2
            ;;

        -h|--help)
            echo "Usage: $0 {ksud|lkm <kmi-version>}..."
            echo ""
            echo "Arguments:"
            echo "  clean               Clean build artifacts and remove DDK Docker images"
            echo "  ksuinit             Build ksuinit static binary"
            echo "  ksud                Build ksud userspace daemon"
            echo "  lkm <kmi-version>   Build kernel module for specific KMI version or use 'all' to build all KMIs"
            echo ""
            echo "Valid KMI versions:"
            for kmi in $VALID_KMIS; do
                echo "  $kmi"
            done
            exit 0
            ;;
    esac
done

# ksuinit
if [[ "$BUILD_KSUINIT" == "1" ]]; then
    rustup target add aarch64-unknown-linux-musl
    export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER="$CLANG_PATH"
    RUSTFLAGS="-C link-arg=-no-pie" cargo build --package ksuinit --target=aarch64-unknown-linux-musl --release --manifest-path ./userspace/ksuinit/Cargo.toml
    cp target/aarch64-unknown-linux-musl/release/ksuinit userspace/ksud/bin/aarch64/
fi
# lkm
if [[ "$BUILD_LKM" == "all" ]]; then
    export -f build_lkm
    export DIR DDK_RELEASE VALID_KMIS
    echo "=== Building all KMIs ==="
    echo "$VALID_KMIS" | xargs -P0 -I{} bash -c 'build_lkm "$@"' _ {}
    echo "=== All KMIs done ==="
elif [[ -n "$BUILD_LKM" ]]; then
    build_lkm "$BUILD_LKM"
fi
# ksud
if [[ "$BUILD_KSUD" == "1" || "$BUILD_KSUINIT" == "1"  || -n "$BUILD_LKM" ]]; then
    rustup update stable
    rustup target add $TARGET
    cargo build --target $TARGET --release --manifest-path ./userspace/ksud/Cargo.toml
fi

cp target/$TARGET/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
cd manager && ./gradlew $GRADLE_FLAG aRelease
cd $DIR

rm -f out/*.apk
cp -f manager/app/build/outputs/apk/release/*.apk out/
