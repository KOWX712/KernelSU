#!/bin/bash

# Minimal: ./build.sh ksud
# Full: ./build.sh ksuinit lkm all
# Specific: ./build.sh ksuinit lkm <kmi-version>

if [ ! -d "out" ]; then
    mkdir -p out
    echo "*" > out/.gitignore
fi

if [ ! -f "out/sign.properties" ]; then
    echo "Error: out/sign.properties not found"
    exit 1
fi

. out/sign.properties

export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_FILE"
export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"

export ANDROID_NDK_HOME=/opt/android-sdk/ndk/29.0.14206865
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$HOME/.cargo/bin:$PATH"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

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
            GRADLE_FLAG="clean"
            DDK_IMAGES=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^ghcr.io/ylarod/ddk-min:")
            if [ -n "$DDK_IMAGES" ]; then
                echo "$DDK_IMAGES" | xargs docker rmi
            else
                echo "No DDK Docker images found."
            fi
            shift
            ;;
        ksud)
            BUILD_KSUD=1
            shift
            ;;

        ksuinit)
            BUILD_KSUINIT=1
            shift
            ;;

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
    CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER="aarch64-linux-android26-clang" RUSTFLAGS="-C link-arg=-no-pie" \
        cargo build --target=aarch64-unknown-linux-musl --release --manifest-path ./userspace/ksuinit/Cargo.toml
    cp userspace/ksuinit/target/aarch64-unknown-linux-musl/release/ksuinit userspace/ksud/bin/aarch64/
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
    rustup default stable
    CROSS_CONTAINER_OPTS="-v /opt/android-sdk:/opt/android-sdk" \
    CROSS_NO_WARNINGS=0 cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml
fi


cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
cd manager && ./gradlew $GRADLE_FLAG aRelease
cd $DIR

rm -f out/*.apk
cp -f manager/app/build/outputs/apk/release/*.apk out/
