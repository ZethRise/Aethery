#!/usr/bin/env bash
# Builds Psiphon's console client for an Android ABI, to ship in the APK as libpsiphon.so.
# The client is a program, not a library: the core runs it from the app's native library folder.
#
# usage: psiphon-build-android.sh <arm64-v8a|armeabi-v7a|x86_64> <outdir> [source-dir]
# Needs a Go toolchain on PATH (go.mod asks for the version named in it) and the Android NDK
# (ANDROID_HOME or ANDROID_SDK_ROOT). With no source-dir the pinned commit is cloned.
set -euo pipefail

abi="${1:-}"
out="${2:-}"
src="${3:-}"
if [ -z "$abi" ] || [ -z "$out" ]; then
    echo "usage: psiphon-build-android.sh <abi> <outdir> [source-dir]" >&2
    exit 2
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ndk="$(find "$sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
api=26

case "$abi" in
    arm64-v8a)   goarch=arm64; goarm=;  cc="$bin/aarch64-linux-android$api-clang" ;;
    armeabi-v7a) goarch=arm;   goarm=7; cc="$bin/armv7a-linux-androideabi$api-clang" ;;
    x86_64)      goarch=amd64; goarm=;  cc="$bin/x86_64-linux-android$api-clang" ;;
    *) echo "unknown abi $abi" >&2; exit 2 ;;
esac

work=""
if [ -z "$src" ]; then
    work="$(mktemp -d)"
    trap 'rm -rf "$work"' EXIT
    src="$work/psiphon"
    git clone --quiet --depth 50 --branch shirokhorshid https://github.com/CluvexStudio/psiphon-tunnel-core.git "$src"
    git -C "$src" checkout --quiet 83aa73b9b982e7421e00117f5b0c5aceb5dda452
fi

mkdir -p "$out/$abi"
# -checklinkname=0: a vendored network library reaches into net's internals, which Go 1.23+ refuses
# by default. cgo is on so name lookups go through Android's own resolver.
(
    cd "$src"
    GOTOOLCHAIN=local GOOS=android GOARCH="$goarch" GOARM="$goarm" CGO_ENABLED=1 CC="$cc" GOFLAGS=-mod=vendor \
        go build -trimpath -ldflags "-s -w -checklinkname=0" -o "$out/$abi/libpsiphon.so" ./ConsoleClient
)
ls -l "$out/$abi/libpsiphon.so"
