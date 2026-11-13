# Bugs and fixes

Severity scale: **Critical** (crash or data loss on a main path) · **High** (main feature broken or unsafe) · **Medium** (wrong behavior in a real but narrower case) · **Low** (polish, hygiene).

Status: **Fixed** (verified as stated) · **Open** · **Fixed, device check pending**.

## A. Bugs found on the real device (Honor Pad 10) and already fixed

| ID | Problem | File | Severity | Root cause | Fix | Verification | Status |
|---|---|---|---|---|---|---|---|
| D1 | Toolchain install: `apk` exit 99, "Unable to lock database" | `runtime/.../install/RuntimeInstaller.kt` (v0.1.0) | Critical | PRoot ignores SIGTERM; after SIGKILL its guests keep running as orphans and hold apk's lock | Tag all PRoot processes and kill them through `/proc` (`core/.../process/ProcessTree.kt`); `InstallGuard` (mutex + file lock); Stopping state; `apk --wait 60` | Host repro with real PRoot + Alpine; 10 installer tests; device: install got past this step in v0.1.2 | Fixed |
| D2 | Toolchain install: "DNS: transient error", exit 9 | `RuntimeInstaller.kt` | High | musl has no DNS cache, so apk resolves the mirror once per package and some UDP lookups time out; the error text wasn't recognised as a network failure | Resolve the mirror with Android's resolver and pin it in `/etc/hosts` during the install; network DNS first; 3 retries | Unit tests; host `getent` check; device: install completed with v0.1.3 | Fixed |
| D3 | App closes when tapping Run or opening the terminal | `terminal/.../TerminalPane.kt` (v0.1.3) | Critical | `setTypeface()` was called before `setTextSize()`; Termux's `TerminalView` creates its renderer only in `setTextSize()` → NullPointerException | Call `setTextSize()` first | Code reading of Termux's `TerminalView` (lines 498–505); build + lint | Fixed, device check pending (v0.1.4) |
| D4 | Closing a terminal tab left its program running | `terminal/.../TerminalSessionManager.kt` | High | Termux kills only the PRoot process | Kill the whole process tree on close | `ProcessTreeTest` (real processes) | Fixed |
| D5 | "App not installed / package conflicts" when updating | CI signing | High | Every CI build is signed with a new temporary debug key | — (needs a stable key from the owner, see O1) | — | Open |

## B. Fixed in S4 (v0.1.5)

| ID | Fix | Verification | Status |
|---|---|---|---|
| O4 | `WorkspaceStager`: the file being run, and files whose provider reports no time, are compared by content and rewritten when they differ | `WorkspaceStagerTest` (3 tests); with the old logic 2 of them fail | Fixed |
| O5 | `TerminalKeepAliveService`: started only while the app is visible; `startForeground()` is always called before stopping; it uses the live session count, so a short program that already ended does not leave a notification | Build + lint; no automated test (needs a device or emulator, see O3) | Fixed, device check pending |
| O6 | Repair/Reinstall close running terminal sessions (whole process trees) first; Settings asks for confirmation when terminals are open | Build + lint; no automated test (UI) | Fixed, device check pending |

## B2. Fixed in S5 (v0.1.6)

| ID | Fix | Verification | Status |
|---|---|---|---|
| O7 | `EditorDocument`: when the version differs from the saved one, the text is compared with the saved text by length, then by a 64-bit FNV-1a hash (only when the lengths match; files over 1M characters keep the version check). Undo back to the saved text is now "saved" | `EditorDocumentTest` (4 tests with sora `Content`); with the old version-only logic 2 of them fail | Fixed |
| O8 | Editor, Explorer and toolchain errors use typed events mapped to string resources (EN + AR): file missing, too large, binary, not UTF-8, open/save/run failed, and one line per installer error kind. The system's own error text stays after the colon, untranslated | Build + lint; no automated UI test (see O3) | Fixed, device check pending |
| O9 | AGP 9.5.0 → 9.5.1; CI actions on Node 24 majors: `checkout@v7`, `setup-java@v6`, `setup-gradle@v6` (with the MIT `basic` cache provider instead of v6's proprietary default), `upload-artifact@v7`, `download-artifact@v8`, `action-gh-release@v3`. Each action's inputs used here were checked against its `action.yml` | Local build, tests, lint: exit 0, lint "No issues found" | Fixed; CI run pending |
| O10 | Release job downloads both artifacts in one step and retries once after 60 s if it fails. Kept as a separate job so the build job (which also runs for pull requests) never gets write permission | YAML parsed; next release run | Fixed; next release shows whether it holds |

## B3. Fixed in S6

| ID | Fix | Verification | Status |
|---|---|---|---|
| O11 (files) | Added `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1 by reference), `SECURITY.md`, `CHANGELOG.md`, issue forms (bug, feature) and a pull request template | YAML of the issue forms parsed; GitHub shows the forms under New issue | Fixed |

## B4. Fixed in S3

| ID | Fix | Verification | Status |
|---|---|---|---|
| O3 | Emulator smoke tests in CI. `SmokeTest` covers launch, main UI, creating and opening files, typing and Ctrl+S save (checked on disk), and the "cannot run" message in English and Arabic. `ToolchainTest` installs the toolchain through the UI, types in the terminal, and runs C and Python (stdin) with exit code 0. Every test checks for crashes; logcat is scanned for crashes and ANRs. Report: `docs/S3_EMULATOR_TESTS.md` | CI run 42258050449: 4/4 passed. Not yet shown against a real crash; arm64, tablet layout and SAF are not covered | Fixed (release gating pending the owner's decision) |

## C. Still open (O3–O10 moved to B–B4)

| ID | Problem | File | Severity | Root cause | Proposed fix | Verification |
|---|---|---|---|---|---|---|
| O1 | Each new release needs an uninstall, which deletes the toolchain and in-app projects (since project export/import, the projects can be carried over by hand; the toolchain is downloaded again) | `.github/workflows/android.yml`, `app/build.gradle.kts` | High | No stable signing key: pre-releases use CI's random debug key | Owner creates a key (`docs/RELEASING.md`) and adds the 4 secrets; CI signs pre-releases and releases with it | Install vN, then vN+1 over it without uninstalling |
| O2 | A debuggable APK is the public download | release job in `android.yml` | High (security) | Pre-releases ship the *debug* build (`debuggable=true`): with adb, anyone can read the app's private files | Publish the R8 *release* build, signed with the stable key (after O1) | `aapt2 dump badging` shows no `application-debuggable` |
| O11 | Default branch is named `ccr-4bd770f7-cls19j` | GitHub settings | Low | Created by the build environment | Owner renames it to `main` (Settings → Branches); GitHub redirects old links. The files part was fixed in S6 | GitHub shows `main` |

## D. Checked and found fine
- Run tab and terminal tab switching: the fault seen in emulator runs 2 and 7–9 was in the test, not in the app (`docs/S3_EMULATOR_TESTS.md`, section 5). The app change made for it (`d6b2e28`) was reverted (`c442bf5`). Device confirmation is item F3 of `docs/DEVICE_TEST_v0.2.md`.
- Project archives: import rejects entries that resolve outside the archive, limits the unpacked size (1 GB) and file count (50,000), and never overwrites an existing project. Export skips symbolic links (`ProjectArchiveTest`, 6 tests).
- Native libraries (`libproot.so`, `libproot-loader.so`, `libtermux.so`) use 16 KB page alignment (`0x4000`), as Android 15+ devices require.
- Archive extraction rejects `..` and does not follow absolute symlinks (`RootfsExtractor.kt`).
- File names are validated before create/rename (`FileNames.validate`).
- No secrets in the repository; signing only through environment variables.
- The PRoot build is reproducible from the published source archive (byte-identical).
