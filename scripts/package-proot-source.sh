#!/usr/bin/env bash
# Packages the complete corresponding source of the PRoot binaries shipped in the APK
# (GPL-2.0-or-later / LGPL-3.0-or-later compliance): pinned PRoot and talloc sources,
# the talloc shim, and the build script. Run after scripts/build-proot.sh.
#
# Usage: scripts/package-proot-source.sh <output.tar.gz>
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:?usage: package-proot-source.sh <output.tar.gz>}"
WORK="${PROOT_WORK_DIR:-$ROOT/build/proot}"
PROOT_SRC="${PROOT_SRC:-$WORK/src/proot}"
TALLOC_SRC="${TALLOC_SRC:-$WORK/src/samba}"
[[ -f "$PROOT_SRC/src/GNUmakefile" && -f "$TALLOC_SRC/lib/talloc/talloc.c" ]] || {
  echo "error: sources not found; run scripts/build-proot.sh first" >&2; exit 1; }

NAME="zakdroid-proot-source"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/$NAME/scripts/proot" "$STAGE/$NAME/talloc/lib/talloc"
mkdir -p "$STAGE/$NAME/proot"
tar -C "$PROOT_SRC" --exclude=.git -cf - . | tar -C "$STAGE/$NAME/proot" -xf -
cp "$TALLOC_SRC/lib/talloc/talloc.c" "$TALLOC_SRC/lib/talloc/talloc.h" "$TALLOC_SRC/lib/talloc/LICENSE" "$STAGE/$NAME/talloc/lib/talloc/"
cp "$ROOT/scripts/build-proot.sh" "$STAGE/$NAME/scripts/"
cp "$ROOT/scripts/proot/talloc-replace.h" "$STAGE/$NAME/scripts/proot/"
PROOT_REV="$(sed -n 's/^PROOT_REV="\([0-9a-f]*\)".*/\1/p' "$ROOT/scripts/build-proot.sh")"
TALLOC_REV="$(sed -n 's/^TALLOC_REV="\([0-9a-f]*\)".*/\1/p' "$ROOT/scripts/build-proot.sh")"
cat > "$STAGE/$NAME/README.md" <<README
# Source of the PRoot binaries in zakdroid-code

The APK ships \`lib/<abi>/libproot.so\` (PRoot, GPL-2.0-or-later, statically linked with
talloc, LGPL-3.0-or-later) and \`lib/<abi>/libproot-loader.so\` (the PRoot loader).
This archive is their complete corresponding source:

| Directory | Upstream | Revision |
|---|---|---|
| \`proot/\` | https://github.com/termux/proot | \`$PROOT_REV\` (v5.1.107.101) |
| \`talloc/\` | https://github.com/samba-team/samba \`lib/talloc\` | \`$TALLOC_REV\` (talloc-2.5.0) |
| \`scripts/\` | zakdroid-code: build script and the replacement for Samba's \`lib/replace\` | |

Rebuild (needs an Android NDK, bash, make and gawk):

    PROOT_SRC=\$PWD/proot TALLOC_SRC=\$PWD/talloc scripts/build-proot.sh

The script writes to
\`runtime/src/full/jniLibs/\` relative to its parent directory. The only source change made
at build time is adding \`#include <string.h>\` to \`proot/src/extension/ashmem_memfd/ashmem_memfd.c\`.
README
tar -C "$STAGE" -czf "$OUT" "$NAME"
echo "wrote $OUT"
