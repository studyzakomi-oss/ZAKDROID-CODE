# Changelog

All notable changes to zakdroid-code. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions are the app's `versionName`; each is published as a GitHub pre-release `vX.Y.Z-alpha`.
Bug IDs (D1, O4, …) refer to [`docs/BUGS_AND_FIXES.md`](docs/BUGS_AND_FIXES.md).

## [Unreleased]
### Added
- VS Code essentials (milestone V1; full comparison in `docs/VSCODE_COMPARISON.md`):
  - **Command Palette** (Ctrl+Shift+P, F1), **Quick Open** (Ctrl+P), **Go to Line** (Ctrl+G) and **Go to Symbol in File** (Ctrl+Shift+O) in one picker; type `>`, `:` or `@` to switch.
  - **Search view**: find in all files of the folder, with match case, whole word and regular expressions. Unsaved text of open editors is searched as shown.
  - Line commands: toggle comment (Ctrl+/), move line (Alt+↑/↓), copy line (Shift+Alt+↑/↓), delete line (Ctrl+Shift+K). Also in the editor's new ⋮ menu.
  - Tab menu (long-press): close others, close following, close saved, close all, copy path. Reopen closed editor (Ctrl+Shift+T).
  - Settings: auto save, trim trailing whitespace, insert final newline, render whitespace, line numbers, sticky scroll.
  - Status Bar: selection count, tab size, and tapping the position opens Go to Line. Font zoom with Ctrl+=, Ctrl+- and Ctrl+0. Ctrl+H opens find with replace.
- Emulator smoke tests in CI (S3, O3). They cover launch, the editor and saving, the terminal, running C and Python, and English and Arabic messages. Crash and ANR detection, screenshots and logs on failure. See `docs/S3_EMULATOR_TESTS.md`.
- Open-source project files: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, this changelog, and issue and pull request templates (O11).

### Removed
- Two unused Python wheel files (16 MB) that had been committed by mistake.

## [0.1.6] - 2026-11-06
### Fixed
- A file no longer stays "unsaved" after undoing back to the saved text (O7).
- Editor, Explorer and toolchain error messages are now translated, so the Arabic UI shows them in Arabic. Each installer error kind now has a one-line explanation (O8).
### Changed
- Android Gradle Plugin 9.5.1; CI actions moved to their current Node 24 versions, and Gradle caching uses the open-source provider (O9).
- The release job retries a failed artifact download once (O10).

## [0.1.5] - 2026-11-06
### Fixed
- Running a file from a storage folder could compile a stale copy (O4).
- Possible crash when the terminal keep-alive service could not start in the foreground (O5).
- Repair/Reinstall now close running terminals first and ask for confirmation (O6).
### Added
- Phase 1 audit documents: audit report, bugs and fixes, development roadmap.

## [0.1.4] - 2026-10-30
### Fixed
- The app closed when tapping Run or opening the terminal (D3).

## [0.1.3] - 2026-10-30
### Fixed
- Toolchain install failed with "DNS: transient error". The mirror is now resolved once and pinned during the install, with retries (D2).

## [0.1.2] - 2026-10-30
### Added
- Terminal sessions stay alive in the background (foreground service), plus battery-optimization guidance.
- Download section in the README (EN + AR).
### Changed
- Direction: the app runs entirely on the device; remote server mode was dropped.

## [0.1.1] - 2026-10-29
### Fixed
- Toolchain install failed with "Unable to lock database": orphaned guest processes are now killed (D1). Closing a terminal tab kills its whole process tree (D4).
### Added
- Single-installer guarantee, a "Stopping" state while a cancelled install cleans up, and an installer log in Copy diagnostics.

## [0.1.0] - 2026-10-29
### Added
- First alpha (Phase 1):
  - workbench, Explorer, code editor with highlighting, tabs, terminal, and one-tap Run for C, C++ and Python;
  - Alpine + PRoot toolchain installer;
  - two themes;
  - English and Arabic.
- ABI-split APKs, release pipeline, diagnostics.

[Unreleased]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/compare/v0.1.6-alpha...HEAD
[0.1.6]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.6-alpha
[0.1.5]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.5-alpha
[0.1.4]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.4-alpha
[0.1.3]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.3-alpha
[0.1.2]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.2-alpha
[0.1.1]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.1-alpha
[0.1.0]: https://github.com/studyzakomi-oss/ZAKDROID-CODE/releases/tag/v0.1.0-alpha
