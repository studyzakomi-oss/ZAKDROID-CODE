# Termux terminal libraries (vendored)

| Module | Upstream path | License |
|---|---|---|
| `:termux-terminal-emulator` | `terminal-emulator/` | Apache-2.0 |
| `:termux-terminal-view` | `terminal-view/` | Apache-2.0 |

- Upstream: https://github.com/termux/termux-app
- Revision: tag `v0.118.3` (commit `5b657c6adf4304e5198951ce815fe0205dcac29c`)
- License: these two libraries are derived from Jack Palevich's
  *Terminal Emulator for Android* and are released under the Apache License 2.0
  (see `LICENSE` in this directory and termux-app's `LICENSE.md`, "Exceptions").
  The rest of termux-app is GPLv3 and is **not** used by zakdroid-code.

## Why vendored

F-Droid builds every dependency from source, and JitPack (the only binary
distribution of these libraries) is not a reliable build input. Vendoring the
exact upstream sources keeps builds reproducible.

## Local modifications

- Gradle build files rewritten in Kotlin DSL for this project.
- `AndroidManifest.xml`: the `package` attribute was removed (AGP 9 requires
  the Gradle `namespace` instead; the namespaces match upstream).
- `-Werror` removed from the JNI C flags (newer NDK clang adds warnings that
  upstream's pinned NDK 22 did not have). The C source is unchanged.

The Java and C sources are byte-for-byte identical to upstream.
