#!/usr/bin/env bash
# Builds PRoot (GPL-2.0) and its loader for Android from pinned upstream sources,
# with libtalloc (LGPL-3.0) linked statically.
#
# Output (consumed by the :runtime module, "full" flavor):
#   runtime/src/full/jniLibs/<abi>/libproot.so         PRoot executable
#   runtime/src/full/jniLibs/<abi>/libproot-loader.so  PRoot loader executable
# Both are executables named lib*.so so that Android extracts them into the
# app's nativeLibraryDir, the only app-owned directory that allows exec().
#
# Requirements: bash, git, make, gawk, an Android NDK (ANDROID_NDK_HOME, or
# ANDROID_HOME/ndk/<NDK_VERSION>).
#
# Offline / reproducible builds (e.g. F-Droid): set PROOT_SRC and TALLOC_SRC
# to pre-fetched checkouts of the pinned revisions below.
set -euo pipefail

PROOT_REPO="https://github.com/termux/proot"
PROOT_REV="b27d81a68d765f3462e807f4a14e01a03f8e89ea"   # tag v5.1.107.101
PROOT_VERSION="5.1.107.101"
TALLOC_REPO="https://github.com/samba-team/samba"
TALLOC_REV="655f8a62d4d16d6492cfc5572e536e1835ee7505"  # tag talloc-2.5.0
ANDROID_API=26
NDK_VERSION="${NDK_VERSION:-29.0.14206865}"
ABIS="${ABIS:-arm64-v8a x86_64}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${PROOT_WORK_DIR:-$ROOT/build/proot}"
OUT="$ROOT/runtime/src/full/jniLibs"
SHIM_DIR="$ROOT/scripts/proot"

log() { printf '\033[1;34m[build-proot]\033[0m %s\n' "$*"; }

find_ndk() {
  if [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then echo "$ANDROID_NDK_HOME"; return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" && -f "$ROOT/local.properties" ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties")"
  fi
  if [[ -n "$sdk" && -d "$sdk/ndk/$NDK_VERSION" ]]; then echo "$sdk/ndk/$NDK_VERSION"; return; fi
  echo "error: Android NDK $NDK_VERSION not found. Set ANDROID_NDK_HOME or install it with: sdkmanager \"ndk;$NDK_VERSION\"" >&2
  exit 1
}

checkout() { # <repo> <rev> <dir> [sparse paths...]
  local repo="$1" rev="$2" dir="$3"; shift 3
  if [[ -d "$dir/.git" ]] && [[ "$(git -C "$dir" rev-parse HEAD)" == "$rev" ]]; then return; fi
  rm -rf "$dir"
  git init -q "$dir"
  git -C "$dir" remote add origin "$repo"
  if (($#)); then
    git -C "$dir" sparse-checkout set --no-cone "$@"
    git -C "$dir" fetch -q --depth 1 --filter=blob:none origin "$rev"
  else
    git -C "$dir" fetch -q --depth 1 origin "$rev"
  fi
  git -C "$dir" checkout -q FETCH_HEAD
}

NDK="$(find_ndk)"
HOST_TAG="linux-x86_64"
[[ "$(uname -s)" == "Darwin" ]] && HOST_TAG="darwin-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
[[ -d "$TOOLCHAIN" ]] || { echo "error: NDK toolchain not found at $TOOLCHAIN" >&2; exit 1; }

mkdir -p "$WORK"
# PRoot's loader-info.awk uses gawk's strtonum(); expose gawk as "awk" for make.
command -v gawk >/dev/null || { echo "error: gawk is required (apt-get install gawk / brew install gawk)" >&2; exit 1; }
mkdir -p "$WORK/bin"
ln -sf "$(command -v gawk)" "$WORK/bin/awk"
export PATH="$WORK/bin:$PATH"
PROOT_SRC="${PROOT_SRC:-$WORK/src/proot}"
TALLOC_SRC="${TALLOC_SRC:-$WORK/src/samba}"
if [[ ! -f "$PROOT_SRC/src/GNUmakefile" ]]; then
  log "fetching PRoot $PROOT_VERSION ($PROOT_REV)"
  checkout "$PROOT_REPO" "$PROOT_REV" "$PROOT_SRC"
fi
if [[ ! -f "$TALLOC_SRC/lib/talloc/talloc.c" ]]; then
  log "fetching talloc 2.5.0 ($TALLOC_REV)"
  checkout "$TALLOC_REPO" "$TALLOC_REV" "$TALLOC_SRC" /lib/talloc/talloc.c /lib/talloc/talloc.h /lib/talloc/LICENSE
fi

for ABI in $ABIS; do
  case "$ABI" in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    x86_64)    TRIPLE=x86_64-linux-android ;;
    *) echo "error: unsupported ABI $ABI" >&2; exit 1 ;;
  esac
  CC="$TOOLCHAIN/bin/${TRIPLE}${ANDROID_API}-clang"
  STRIP="$TOOLCHAIN/bin/llvm-strip"
  OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
  OBJDUMP="$TOOLCHAIN/bin/llvm-objdump"
  BUILD="$WORK/$ABI"
  log "building for $ABI"
  rm -rf "$BUILD"; mkdir -p "$BUILD/include" "$BUILD/lib" "$BUILD/proot"

  # libtalloc as a static archive, with a minimal replacement for Samba's lib/replace.
  cp "$TALLOC_SRC/lib/talloc/talloc.h" "$BUILD/include/"
  cp "$SHIM_DIR/talloc-replace.h" "$BUILD/include/replace.h"
  "$CC" -c -O2 -fPIC -Wall -I"$BUILD/include" \
    -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=5 -DTALLOC_BUILD_VERSION_RELEASE=0 \
    "$TALLOC_SRC/lib/talloc/talloc.c" -o "$BUILD/talloc.o"
  "$TOOLCHAIN/bin/llvm-ar" rcs "$BUILD/lib/libtalloc.a" "$BUILD/talloc.o"

  # PRoot, with the loader unbundled so it can live in nativeLibraryDir.
  cp -R "$PROOT_SRC/src/." "$BUILD/proot/"
  # Upstream ashmem_memfd.c relies on an implicit <string.h>, which current NDK
  # clang rejects; add the include to the build copy (sources stay untouched).
  sed -i.orig '1i\
#include <string.h>
' "$BUILD/proot/extension/ashmem_memfd/ashmem_memfd.c"
  make -C "$BUILD/proot" -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)" \
    CC="$CC" LD="$CC" STRIP="$STRIP" OBJCOPY="$OBJCOPY" OBJDUMP="$OBJDUMP" \
    CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -I$BUILD/include -DARG_MAX=131072 -DVERSION=\\\"$PROOT_VERSION\\\"" \
    LDFLAGS="-L$BUILD/lib -l:libtalloc.a -Wl,-z,noexecstack" \
    PROOT_UNBUNDLE_LOADER=/nonexistent \
    HAS_LOADER_32BIT= \
    proot loader/loader >"$BUILD/make.log" 2>&1 || { tail -40 "$BUILD/make.log"; exit 1; }

  mkdir -p "$OUT/$ABI"
  install -m 0755 "$BUILD/proot/proot" "$OUT/$ABI/libproot.so"
  install -m 0755 "$BUILD/proot/loader/loader" "$OUT/$ABI/libproot-loader.so"
  "$STRIP" "$OUT/$ABI/libproot.so"
  log "  -> $OUT/$ABI/libproot.so, libproot-loader.so"
done
log "done"
