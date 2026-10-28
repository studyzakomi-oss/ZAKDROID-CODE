# Third-party components (Phase 1)

zakdroid-code's own code is **Apache-2.0**. The table lists every third-party
component in the Phase 1 build: how each one is used, and what its license
requires. The same list appears in the app under **Settings → About**. License
texts ship in the APK under `assets/licenses/`.

## Component table

| Component | Version | License | Purpose | How it is used |
|---|---|---|---|---|
| sora-editor (`editor`, `language-textmate`) | 0.24.7 | LGPL-2.1 | Code editor view | Unmodified Maven library (separate AAR) |
| Eclipse tm4e (inside `language-textmate`) | as shipped by sora 0.24.7 | EPL-2.0 | TextMate grammar engine | Unmodified, via sora |
| Eclipse JDT annotations | 2.4.100 | EPL-2.0 | Annotations used by tm4e | Unmodified, transitive |
| Joni / JCodings | 2.2.7 / 1.0.64 | MIT | Oniguruma-compatible regex for grammars | Unmodified, transitive |
| Gson | 2.14.0 | Apache-2.0 | JSON parsing (grammars, themes) | Unmodified, transitive |
| SnakeYAML Engine | 3.2 | Apache-2.0 | YAML grammar support in tm4e | Unmodified, transitive |
| VS Code grammars + language configurations (c, cpp, python) | vscode @ `a06ee7a` | MIT | Syntax highlighting, brackets, comments, indentation rules | Data files copied unmodified (`editor/src/main/assets/textmate`) |
| ↳ better-c-syntax, better-cpp-syntax (Jeff Hykin) | via VS Code | MIT | C / C++ grammars | as above |
| ↳ MagicPython (MagicStack) | via VS Code | MIT | Python grammar | as above |
| Termux `terminal-emulator`, `terminal-view` | v0.118.3 | Apache-2.0 | Terminal emulation (VT100/xterm), terminal view, PTY JNI | Source vendored in `third_party/termux` (build files and manifests adapted; Java/C unchanged) |
| PRoot (Termux fork) | 5.1.107.101 (`b27d81a`) | GPL-2.0-or-later | Runs the Alpine userland without root (ptrace) | Built from source by `scripts/build-proot.sh`; shipped as the **separate executable** `libproot.so` + `libproot-loader.so` |
| talloc | 2.5.0 (`655f8a6`) | LGPL-3.0-or-later | Memory allocator required by PRoot | Statically linked **into PRoot only**, never into the app |
| Alpine Linux minirootfs | 3.24.3 | mixed (see below) | Linux userland (musl, BusyBox, apk) | **Downloaded on the device** from dl-cdn.alpinelinux.org, SHA-256 pinned; not in the APK |
| Alpine packages: `build-base` (gcc, g++, make, binutils, musl-dev), `clang`, `python3`, `py3-pip` | Alpine v3.24 | GPL-2.0/3.0 (GCC, binutils, make), Apache-2.0 WITH LLVM-exception (clang/LLVM), PSF-2.0 (Python), MIT (pip, musl), GPL-2.0 (BusyBox) and others | Compilers and interpreters | Installed on the device by `apk` from Alpine's servers; not in the APK |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | Extracts the rootfs `.tar.gz` | Unmodified library |
| OkHttp / Okio | 5.5.1 / 3.17.0 | Apache-2.0 | Resumable HTTPS download | Unmodified library |
| AndroidX: Compose (UI, Foundation, Material 3, Material Icons), Activity, Lifecycle, Core, SplashScreen, Room, DataStore, WorkManager, DocumentFile, Hilt-Work | Compose BOM 2026.09.00 and others (`gradle/libs.versions.toml`) | Apache-2.0 | App framework, UI, storage, background work | Unmodified libraries |
| Dagger Hilt | 2.60.1 | Apache-2.0 | Dependency injection | Unmodified library + annotation processor |
| Kotlin stdlib, kotlinx.coroutines | 2.4.21 / 1.11.0 | Apache-2.0 | Language runtime, concurrency | Unmodified libraries |
| Montserrat | `JulietaUla/Montserrat` @ `555facf` | OFL-1.1 | UI typeface | Font files bundled unmodified |
| JetBrains Mono | `JetBrains/JetBrainsMono` @ `1937130` | OFL-1.1 | Code typeface (editor, terminal) | Font files bundled unmodified |

Build-only tools are not shipped in the APK: Gradle, AGP, KSP, the NDK, Room
and Hilt compilers. Their licenses place no obligations on the app.

## Licensing impact

### Permissive (Apache-2.0, MIT, OFL-1.1)
No effect on the app's license.
- **Apache-2.0 and MIT:** keep the copyright and license notices (`NOTICE`, `assets/licenses/`, About screen).
- **OFL-1.1:** the fonts may be bundled and used freely. They may not be sold on their own, and a modified font may not keep the reserved font name.

### Weak copyleft, file level: EPL-2.0 (tm4e, JDT annotations)
Only modified EPL files must be published under the EPL. We use tm4e unmodified as a library, so nothing else is affected; the license text is included.

### Weak copyleft, library level: LGPL-2.1 (sora-editor)
- **Allowed:** an Apache-2.0 (or even proprietary) app may link sora-editor as a library.
- **Required:**
  1. Keep sora-editor a separate, replaceable component: we use the unmodified Maven AAR.
  2. Include the LGPL text.
  3. Allow users to relink against a modified sora-editor.

  Item 3 is met because zakdroid-code is open source and anyone can rebuild it with a different sora version. If sora itself is ever modified, those changes must be published under LGPL-2.1.

### LGPL-3.0 (talloc)
- **How it is linked:** statically, but only into the PRoot executable, which is GPL. talloc is not part of the app's code.
- **Effect:** none on the app's license.
- **Required:** the talloc source is provided. `build-proot.sh` fetches the exact revision, and the small `scripts/proot/talloc-replace.h` shim that replaces Samba's `lib/replace` ships in this repository.

### Strong copyleft: GPL-2.0-or-later (PRoot)
- **Separate program:** PRoot is a standalone executable. The app runs it as a child process (`ProcessBuilder` or the terminal PTY) and never links it, so under the GPL's "mere aggregation" rule the app keeps its Apache-2.0 license.
- **Obligations for every APK we distribute:**
  1. Provide the complete corresponding source of the PRoot binary in the APK. `scripts/build-proot.sh` pins the upstream commit, and the only build-time change (adding `#include <string.h>` to one file) is applied by the script and documented there.
  2. Include the GPL text (`assets/licenses/PRoot-COPYING.txt`).
  3. Give recipients the same rights: no restrictions on the PRoot binary.
- **Release checklist:** publish the source together with every GitHub or F-Droid release. Tagging this repository at the released commit is enough.

### GPL and other licenses of Alpine packages
- **Not distributed by us:** the user's device downloads these packages directly from Alpine's mirrors, so zakomi TECH distributes none of them and takes on none of their obligations.
- **Programs users compile:** GCC's runtime library exception, and the musl and LLVM licenses, let users license the programs they build however they like.

### Vendored Termux code
- **Apache-2.0 only:** termux-app as a whole is GPLv3, but `terminal-emulator` and `terminal-view` are covered by the Apache-2.0 exception in termux-app's `LICENSE.md`.
- **Nothing GPL from Termux:** no other Termux code (termux-shared, the app module) is used.
- **Changes:** listed in `third_party/termux/README.md`.

### Trademarks
The Apache-2.0 license does not cover the zakomi TECH names and logo. See `TRADEMARKS.md`.

## Phase 2+ additions (planned, not in this build)

| Component | License | Phase |
|---|---|---|
| sora `editor-lsp` (+ lsp4j, EPL-2.0) | LGPL-2.1 / EPL-2.0 | 2 |
| clangd, clang-format, clang-tidy (`clang-extra-tools`, downloaded on the device) | Apache-2.0 WITH LLVM-exception | 2 |
| python-lsp-server, ruff (pip/apk on the device) | MIT | 2 |
| JGit or libgit2 | EDL-1.0 (BSD) / GPL-2.0 with linking exception | 3 |
| lldb-dap, debugpy (on the device) | Apache-2.0 WITH LLVM-exception / MIT | 4 |
| SSH library (sshj or Apache MINA SSHD) | Apache-2.0 | 4 |
