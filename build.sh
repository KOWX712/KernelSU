#!/bin/bash

mkdir -p out

. out/sign.properties

export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_FILE"
export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"

export ANDROID_NDK_HOME=/opt/android-sdk/ndk/29.0.14206865
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$HOME/.cargo/bin:$PATH"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

DIR="$(pwd)"

if [[ $1 == "ksud" ]]; then
    rustup default stable
    CROSS_CONTAINER_OPTS="-v /opt/android-sdk:/opt/android-sdk" \
    cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml
fi

cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
cd manager && ./gradlew assembleRelease
cd $DIR

rm -f out/*.apk
cp -f manager/app/build/outputs/apk/release/*.apk out/
