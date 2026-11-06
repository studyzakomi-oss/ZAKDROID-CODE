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

## C. Open issues found in this audit

| ID | Problem | File | Severity | Root cause | Proposed fix | Verification |
|---|---|---|---|---|---|---|
| O1 | Each new release needs an uninstall, which deletes the toolchain and in-app projects | `.github/workflows/android.yml`, `app/build.gradle.kts` | High | No stable signing key: pre-releases use CI's random debug key | Owner creates a key (`docs/RELEASING.md`) and adds the 4 secrets; CI signs pre-releases and releases with it | Install vN, then vN+1 over it without uninstalling |
| O2 | A debuggable APK is the public download | release job in `android.yml` | High (security) | Pre-releases ship the *debug* build (`debuggable=true`): with adb, anyone can read the app's private files | Publish the R8 *release* build, signed with the stable key (after O1) | `aapt2 dump badging` shows no `application-debuggable` |
| O3 | No automated UI/instrumented tests: D3 (crash on opening the terminal) reached users | whole project | High (process) | Only JVM unit tests exist; nothing creates real Android views | CI job on an Android emulator (`reactivecircus/android-emulator-runner`, MIT) with a smoke test: open the app, open a terminal, open a file | The test fails on v0.1.3 code and passes on v0.1.4 |
| O4 | A stale copy of a file can be compiled from a SAF folder | `runtime/.../WorkspaceStager.kt:73` | Medium | A file is re-copied only if its size changed or the provider reports a newer `lastModified`; providers that report 0, plus a same-size edit, keep the old copy | Always copy the file being run; for other files also compare a content hash when `lastModified` is 0 | Unit test with a fake FS: same-size edit, `lastModified=0` → new content staged |
| O5 | Possible crash if the keep-alive service cannot go to the foreground | `terminal/.../TerminalKeepAliveService.kt:27-40` | Medium | After `startForegroundService()`, Android 12+ crashes the app ("did not then call startForeground") if `startForeground()` is never called; the `count <= 0` branch and the `catch` branch call `stopSelf()` without it | Start the service only while the app is visible (`ProcessLifecycleOwner`); always call `startForeground()` first, then stop | Device test: open a terminal, leave the app, let the session end |
| O6 | Reinstall/Repair while terminals are running | `RuntimeInstaller.kt:41-44`, `RuntimeSheet.kt`, `SettingsScreen.kt` | Medium | Reinstall deletes the rootfs under running programs; their output and state become undefined | Before Repair/Reinstall, ask to close open terminals and kill guest processes | Manual: open a shell, tap Reinstall → confirmation and clean stop |
| O7 | A file stays "unsaved" after undoing back to the saved text | `editor/.../document/EditorDocument.kt:31-56` | Low | Dirty state compares document *versions*, and undo creates a new version | Keep a hash of the saved text; recompute on change (debounced) | Unit test with sora `Content`: edit → undo → not dirty |
| O8 | Some messages are English-only in the Arabic UI | `app/.../ui/editor/EditorViewModel.kt:102,107,160,198`; installer messages | Low | Strings are built in the ViewModel/installer, not taken from resources | Return typed errors; map them to string resources in the UI | Switch the app to Arabic; trigger a save error |
| O9 | GitHub Actions use Node-20-based action versions (deprecated); AGP 9.5.1 is available (lint warning) | `.github/workflows/android.yml` | Low | Pinned to `@v4` | Move to the current major versions | CI log has no deprecation warning |
| O10 | The release job failed once while downloading an artifact | `android.yml` release job | Low | GitHub artifact-storage flake; the job re-downloads what the build job made | Build and publish in one job (no artifact round trip), or retry the download | Two releases in a row without manual re-runs |
| O11 | Repository hygiene for an open-source project | repo root | Low | Missing `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`, issue/PR templates; default branch is named `ccr-4bd770f7-cls19j` | Add the files; owner renames the branch to `main` in GitHub settings | Files present; GitHub shows `main` |

## D. Checked and found fine
- Native libraries (`libproot.so`, `libproot-loader.so`, `libtermux.so`) use 16 KB page alignment (`0x4000`), as Android 15+ devices require.
- Archive extraction rejects `..` and does not follow absolute symlinks (`RootfsExtractor.kt`).
- File names are validated before create/rename (`FileNames.validate`).
- No secrets in the repository; signing only through environment variables.
- The PRoot build is reproducible from the published source archive (byte-identical).
