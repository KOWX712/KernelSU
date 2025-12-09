#!/bin/sh

rustup default stable

. manager/sign.properties

export ORG_GRADLE_PROJECT_KEYSTORE_FILE="$KEYSTORE_FILE"
export ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export ORG_GRADLE_PROJECT_KEY_ALIAS="$KEY_ALIAS"
export ORG_GRADLE_PROJECT_KEY_PASSWORD="$KEY_PASSWORD"

export ANDROID_NDK_HOME=/opt/android-sdk/ndk/29.0.14206865
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$HOME/.cargo/bin:$PATH"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"

just bm

rm -f ./*.apk
cp -f manager/app/build/outputs/apk/release/*.apk .
