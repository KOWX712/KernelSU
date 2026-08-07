#!/bin/bash

# Minimal: ./build.sh ksud
# Full: ./build.sh ksuinit lkm all
# Specific: ./build.sh ksuinit lkm <kmi-version>

set -e

# Intercept failures and interrupts to kill all background jobs
_cleanup() {
    local exit_code=$?
    trap - EXIT SIGINT SIGTERM
    if [ $exit_code -ne 0 ]; then
        echo -e "\n\033[31mBuild failed or interrupted, cleaning up background tasks...\033[0m"
        local pids=$(jobs -p)
        if [ -n "$pids" ]; then
            kill $pids 2>/dev/null || true
        fi
        pkill -P $$ 2>/dev/null || true
    fi
    exit $exit_code
}
trap _cleanup EXIT SIGINT SIGTERM

if [ ! -d "out" ]; then
    mkdir out
    echo "*" > out/.gitignore
    echo "\033[32mTips: copy this script to out/build.sh for clean workspace, run with bash out/build.sh\033[0m"
fi

# Container
CONTAINER="${CONTAINER:-}"
if [ -z "$CONTAINER" ]; then
    if command -v podman &> /dev/null; then
        CONTAINER="podman"
    elif command -v docker &> /dev/null; then
        CONTAINER="docker"
    fi
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
    local logfile="out/${kmi}.log"

    {
        echo "=== Building kernelsu.ko for KMI: $kmi (DDK: $DDK_RELEASE) ==="

        [ -z "$CONTAINER" ] && echo "Error: No container found." && return 1
        $CONTAINER run --rm --privileged -v "$DIR:/workspace:z" -w /workspace \
            ghcr.io/ylarod/ddk-min:$kmi-$DDK_RELEASE /bin/bash -c "
                set -e
                git config --global --add safe.directory /workspace
                cd kernel
                CONFIG_KSU=m CC=clang make
                cp -f kernelsu.ko ../out/${kmi}_kernelsu.ko
                cp -f kernelsu.ko ../userspace/ksud/bin/aarch64/${kmi}_kernelsu.ko
                echo 'Built: ../out/${kmi}_kernelsu.ko'
            "
    } > "$logfile" 2>&1 || {
        echo "FAILED: build_lkm $kmi" >&2
        cat "$logfile" >&2
        return 1
    }
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        clean)
            rm -rf out/*.apk out/*.ko dist/
            cd manager && ./gradlew clean
            cd "$DIR"
            find kernel -type f \( -name '*.o' -o -name '*.cmd' -o -name '*.ko' -o -name '*.mod' -o -name '*.mod.c' -o -name 'modules.order' -o -name 'Module.symvers' \) -delete 2>/dev/null || true
            [ -n "$CONTAINER" ] && DDK_IMAGES=$($CONTAINER images --format "{{.Repository}}:{{.Tag}}" | grep "^ghcr.io/ylarod/ddk-min:" || echo "")
            if [ -n "$DDK_IMAGES" ]; then
                echo "$DDK_IMAGES" | xargs $CONTAINER rmi
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
            echo "Usage: $0 [ksuinit] [lkm <kmi-version|all>] [ksud]"
            echo "Arguments:"
            echo "  clean               Clean build artifacts and remove DDK Docker images"
            echo "  ksuinit             Build ksuinit static binary"
            echo "  ksud                Build ksud userspace daemon"
            echo "  lkm <kmi-version>   Build kernel module for specific KMI version or 'all'"
            echo ""
            echo "Valid KMI versions:"
            for kmi in $VALID_KMIS; do
                echo "  $kmi"
            done
            exit 0
            ;;
    esac
done

declare -A PIDS
declare -A LOGS

wait_for_jobs() {
    local fail=0
    local name pid logfile

    for name in "$@"; do
        pid="${PIDS[$name]}"
        logfile="${LOGS[$name]}"
        [[ -z "$pid" ]] && continue

        wait "$pid" || {
            echo "FAILED: $name (PID $pid)"
            [[ -n "$logfile" && -f "$logfile" ]] && cat "$logfile"
            fail=1
        }
        unset 'PIDS[$name]'
    done

    if [[ "$fail" -ne 0 ]]; then
        echo "Error: One or more parallel builds failed"
        exit 1
    fi
}

# ksuinit
if [[ "$BUILD_KSUINIT" == "1" ]]; then
    echo "=== Building ksuinit ==="
    (
        exec > out/ksuinit.log 2>&1
        rustup target add aarch64-unknown-linux-musl
        export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER="$CLANG_PATH"
        RUSTFLAGS="-C link-arg=-no-pie" cargo build --package ksuinit --target=aarch64-unknown-linux-musl --release --manifest-path ./userspace/ksuinit/Cargo.toml
        cp target/aarch64-unknown-linux-musl/release/ksuinit userspace/ksud/bin/aarch64/
    ) &
    PIDS[ksuinit]=$!
    LOGS[ksuinit]="out/ksuinit.log"
fi

# lkm
if [[ "$BUILD_LKM" == "all" ]]; then
    (
        echo "=== Building all KMIs ==="
        build_lkm_all() {
            export -f build_lkm
            export DIR DDK_RELEASE VALID_KMIS
            echo "$VALID_KMIS" | xargs -n1 -P1 -I{} bash -c 'build_lkm "$@"' _ {}
        }
        build_lkm_all
    ) &
    PIDS[lkm]=$!
elif [[ -n "$BUILD_LKM" ]]; then
    (
        echo "=== Building LKM: $BUILD_LKM ==="
        build_lkm "$BUILD_LKM"
    ) &
    PIDS[lkm]=$!
    LOGS[lkm]="out/${BUILD_LKM}.log"
fi

# manager
(
    echo "=== Building manager ==="
    cd "$DIR/manager"
    ./gradlew aRelease
) &
PIDS[manager]=$!

# ksud
if [[ "$BUILD_KSUD" == "1" || "$BUILD_KSUINIT" == "1" || -n "$BUILD_LKM" ]]; then
    wait_for_jobs ksuinit lkm
    echo "=== Building ksud ==="
    {
        rustup update stable
        rustup target add $TARGET
        cargo build --target $TARGET --release --manifest-path ./userspace/ksud/Cargo.toml
    } > out/ksud.log 2>&1 || {
        echo "FAILED: ksud"
        cat out/ksud.log
        exit 1
    }
fi

# repack
wait_for_jobs manager
echo "=== Repacking manager APK ==="
rm -f out/*.apk dist/*.apk
python3 repack_apk.py repack
cp -f dist/*.apk out/
