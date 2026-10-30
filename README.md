<p align="center"><img src="branding/logo-512.png" width="112" alt="zakomi TECH logo"></p>

<h1 align="center">zakdroid-code</h1>
<p align="center"><b>B Y &nbsp; Z A K O M I &nbsp; T E C H</b></p>

A VS Code–style IDE for Android phones and tablets, for **C, C++ and Python**.
It runs a real toolchain (clang, gcc, make, Python 3, pip) on the device, inside
an Alpine Linux userland under PRoot. No root is needed.

> **Status: Phase 1 done, M0 (device fixes) in progress.** Brand and themes, Explorer,
> the sora-editor code editor with TextMate highlighting, tabs, an integrated terminal,
> and one-tap Run for single C/C++/Python files.
>
> **Fully on-device:** after a one-time toolchain download, everything works offline,
> with no server needed. Roadmap: [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Phase 1 features

- **Workbench:**
  - Activity Bar (logo, Explorer, Run, Terminal, Settings), tab strip, Panel (terminal) and Status Bar.
  - Status Bar shows the toolchain state, Ln/Col, encoding, line endings and language.
  - Tablets get a Side Bar; phones get one pane, an Explorer drawer, and swipe on the path bar to switch tabs.
- **Explorer:**
  - Internal projects (templates: *C Hello*, *C++ Hello*, *Python script*) and any folder opened with the Storage Access Framework.
  - Create, rename, move and delete; recursive file-name search; recent folders; open tabs restored per folder.
- **Editor** (sora-editor 0.24.7):
  - VS Code TextMate grammars for C, C++ and Python.
  - Bracket-pair highlight, auto-indent, auto-closing pairs, identifier completion.
  - Regex find/replace with case and whole-word options.
  - Undo/redo, word wrap, pinch zoom, sticky scroll, JetBrains Mono with a ligature toggle.
  - UTF-8 with BOM and CRLF preserved on save; unsaved-change prompts.
- **Terminal** (Termux terminal-emulator):
  - Multiple tabs, 256 colors and truecolor, copy/paste, pinch zoom.
  - Touch key row: Esc, Tab, sticky Ctrl/Alt, arrows, Home/End, PgUp/PgDn, symbols.
- **Run:**
  - `clang -g -Wall -Wextra -std=c17` (C), `clang++ … -std=c++20` (C++), `python3 -u` (Python).
  - Each run ends by printing the **exit code and execution time**. AddressSanitizer is intentionally off (poor musl support).
- **Toolchain installer:**
  - Pinned Alpine **3.24.3** (SHA-256 checked); packages `build-base clang python3 py3-pip`.
  - Runs as a foreground job that is **cancellable and resumable** and shows the current step. Free space is checked first (400 MB).
  - **Repair** and **Reinstall** are in Settings.
- **Themes:** *Zakomi Navy Dark* (default) and *Zakomi Light*. All 80 checked color pairs pass WCAG AA (`docs/CONTRAST.md`).
- **Localization:** English and Arabic (full RTL UI; the editor and terminal stay LTR).
- **Hardware keyboard:** Ctrl+S, Ctrl+Shift+S, Ctrl+F, Ctrl+W, Ctrl+(Shift+)Tab, Ctrl+`, Ctrl+B, Ctrl+, and F5.

**Not yet in Phase 1:**
- Code folding: sora-editor has no folding UI yet.
- Multi-cursor and minimap.
- Snippets, LSP features, Problems panel and the Command Palette (M1).

## Build

### Requirements
| Tool | Version |
|---|---|
| JDK | 17 or newer (CI and local builds use 21) |
| Android SDK | platform `android-37.0`, build-tools 37.0.0 |
| Android NDK | `29.0.14206865` (`sdkmanager "ndk;29.0.14206865"`) |
| bash, git, make, **gawk**, python3 | PRoot build and the contrast check |

Gradle 9.8.2 comes with the wrapper; AGP 9.5.0 and Kotlin 2.4.21 come from `gradle/libs.versions.toml`.

### Commands
```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or export ANDROID_HOME

./gradlew :app:assembleFullDebug        # debug APK  → app/build/outputs/apk/full/debug/
./gradlew :app:assembleFullRelease      # release APK (R8); signed only if ZAKDROID_KEYSTORE* env vars are set
./gradlew testDebugUnitTest testFullDebugUnitTest   # unit tests
./gradlew :app:lintFullDebug            # lint
python3 scripts/check_contrast.py       # WCAG AA check of both themes
```

**PRoot binaries:**
- The first build runs `:runtime:buildProot`, which calls `scripts/build-proot.sh` to fetch the pinned PRoot and talloc sources and cross-compile them with the NDK for `arm64-v8a` and `x86_64`.
- The output goes to `runtime/src/full/jniLibs/` (git-ignored); the binaries are never committed.
- **Offline or F-Droid builds:** set `PROOT_SRC` and `TALLOC_SRC` to pre-fetched checkouts.

**APK outputs:** ABI splits produce `app-full-arm64-v8a-<type>.apk`, `app-full-x86_64-<type>.apk` and `app-full-universal-<type>.apk`.

**Release signing (CI):** see [`docs/RELEASING.md`](docs/RELEASING.md). Set the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`; without them, release APKs are built unsigned and CI still passes. Pushing a `v*` tag publishes a GitHub Release: tags containing `-` (e.g. `v0.1.0-alpha`) become pre-releases with debug APKs, plus the PRoot source archive and `SHA256SUMS.txt`.

### Install and first run
```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```
1. Open **zakdroid-code**.
2. Tap **Not installed** in the Status Bar (or press ▶ Run) → **Install**. This downloads about 4 MB of Alpine and then about 300 MB of packages.
3. **New project… → C — Hello world → ▶ Run.**

Full device test plan: [`docs/PHASE1_TEST_CHECKLIST.md`](docs/PHASE1_TEST_CHECKLIST.md).

**Bug reports:** Settings → About → **Copy diagnostics** copies the app version, Android version, device model, ABIs, runtime state and the last crash trace. Crash traces are also saved in app storage as `files/crashes/last-crash.txt` (plus the 5 most recent `crash-*.txt`); read them with `adb shell run-as tech.zakomi.zakdroid.debug cat files/crashes/last-crash.txt`.

## Project layout

```
zakdroid-code/
├── app/                      :app       Activity, splash, workbench UI, ViewModels, Settings, About
│   └── src/main/{java,res,assets/licenses}
├── core/                     :core      design system, fonts, logo, FileSystem (local + SAF), Room, DataStore
├── editor/                   :editor    sora-editor wrapper, find/replace, TextMate grammars + themes
├── terminal/                 :terminal  session manager, Termux view wrapper, extra-keys row
├── runtime/                  :runtime   RuntimeManager API, RunScript, SAF staging
│   └── src/full/             flavor full: PRoot + Alpine installer (WorkManager), jniLibs (built)
├── third_party/termux/       vendored Apache-2.0 terminal-emulator / terminal-view
├── build-logic/convention/   Gradle convention plugins (SDK levels, Compose, Hilt, distribution flavors)
├── scripts/                  build-proot.sh, talloc shim, check_contrast.py
├── branding/                 logo.svg, logo-mono.svg, logo-512.png, generator script
├── docs/                     ARCHITECTURE.md, CONTRAST.md, PHASE1_TEST_CHECKLIST.md
└── .github/workflows/        CI: contrast, PRoot, unit tests, debug + release APKs, lint, releases
```

Design details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Branding assets

`branding/logo.svg` (color), `branding/logo-mono.svg` (single color with the Z
knocked out, using `currentColor`) and `branding/logo-512.png` are generated
from one geometric definition by `branding/generate_brand_assets.py`: two
identical Z pieces, one rotated 180°, in a navy (#0A1F52) square with an 18%
corner radius. The same geometry drives the launcher icon, the splash screen and
the in-app logo.

## License

- **Code:** Apache-2.0 for zakdroid-code's own code (`LICENSE`).
- **Third-party components:** each keeps its own license; see [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) for the table and the GPL, LGPL and MIT impact analysis.
- **Trademarks:** the names "zakdroid-code", "zakomi TECH", "ZAKOMI"/"ZKM" and the Z logo are **not** covered by the license. See [`TRADEMARKS.md`](TRADEMARKS.md).
