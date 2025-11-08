alias bk := build_ksud
alias bm := build_manager

build_ksud:
    CROSS_CONTAINER_OPTS="-v /opt/android-sdk:/opt/android-sdk" \
    cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml

build_manager: build_ksud
    cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
    cd manager && ./gradlew assembleRelease

clippy:
    cargo fmt --manifest-path ./userspace/ksud/Cargo.toml
    cross clippy --target x86_64-pc-windows-gnu --release --manifest-path ./userspace/ksud/Cargo.toml
    cross clippy --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml
