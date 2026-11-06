# Audit report

Date: 2026-11-06 · Commit audited: `6276045` (v0.1.4-alpha) · Branch: `ccr-4bd770f7-cls19j` (the only branch; no uncommitted changes).

## 1. What the project actually is
**zakdroid-code** is a native **Android** IDE (Kotlin + Jetpack Compose) for C, C++ and Python. It runs on the device and compiles with a real toolchain (clang, gcc, Python 3) inside Alpine Linux under PRoot, with no root needed.

The request calls the project an "AGI Coding Assistant" and asks to preserve its "original AI coding capabilities", but **this repository contains no AI features, no AI API integration, no web app and no server.** I am not inventing them: they would be new work (see section 6).

## 2. Architecture as found
| Module | Role | Size (Kotlin/Java/C files) |
|---|---|---|
| `:app` | Activity, workbench UI (Activity Bar, Explorer, tabs, terminal panel, Status Bar), ViewModels, Settings, About, diagnostics | 30 |
| `:core` | Design system (2 themes), file systems (local + SAF), workspaces, Room, DataStore, `ProcessTree` | 24 |
| `:editor` | sora-editor wrapper, TextMate grammars (C/C++/Python), find/replace | 9 |
| `:terminal` | Terminal sessions, Termux view wrapper, extra keys, keep-alive service | 8 |
| `:runtime` | Toolchain installer (PRoot + Alpine 3.24.3), run scripts, SAF staging | 26 |
| `third_party/termux` | Vendored terminal emulator (Apache-2.0) | 22 |
| `build-logic` | Gradle convention plugins | 6 |

About 8,000 lines of Kotlin in total. Details: `docs/ARCHITECTURE.md`.

## 3. Implemented features (verified in code)
- Workbench responsive for phone and tablet; themes Zakomi Navy Dark and Light (WCAG AA, 80 pairs checked); English + Arabic with RTL.
- Explorer: internal projects + SAF folders, create/rename/move/delete, file-name search, templates.
- Editor: highlighting, find/replace (regex), undo/redo, word wrap, zoom, sticky scroll, unsaved-changes handling.
- Terminal: multiple tabs, extra keys, copy/paste, keep-alive foreground service.
- One-tap Run for C/C++/Python, with exit code and timing.
- Toolchain installer: resumable, cancellable, single-instance, DNS-pinned, logged; Repair/Reinstall.
- Diagnostics (crash log + installer log), battery-optimization guidance.
- CI: contrast check, PRoot from source, unit tests, debug + release APKs, lint, GitHub pre-releases with checksums and GPL source.

## 4. Not implemented (planned in `docs/DEVELOPMENT_ROADMAP.md`)
LSP (completion, diagnostics), Problems panel, Command Palette, Git, debugger, multi-file builds (Make/CMake), extensions/plugins, code folding, multi-cursor, minimap.

## 5. Validation run for this audit
| Command | Exit code | Result |
|---|---|---|
| `python3 scripts/check_contrast.py` | 0 | 80 pairs, 0 failing |
| `./gradlew testDebugUnitTest testFullDebugUnitTest :app:assembleFullDebug :app:lintFullDebug` | 0 | 29 unit tests pass, lint: no issues |
| `llvm-readelf -lW` on the native libraries | 0 | 16 KB aligned |
| **Not run** | — | Instrumented/UI tests (none exist); real-device test of v0.1.4 |

**Problems found:** 5 device bugs (4 fixed, 1 open) and 11 open issues, of which 3 are High. Full list with root causes and fixes: `docs/BUGS_AND_FIXES.md`.

## 6. The new request against earlier decisions (needs the owner's decision)
| Request | Earlier decision / fact | Consequence |
|---|---|---|
| AI coding assistant (Phase 8) | 2026-10-30: "independent of servers"; the Claude assistant was removed | An AI needs either a cloud API (server dependency, cost) or an on-device model (1–2 GB download, slow on a tablet, weaker results). One must be chosen, or the feature dropped |
| 15 languages (Phase 3) | "C, C++, Python only unless I ask": the request is that ask | Toolchains differ a lot in size and feasibility on Android/Alpine. **Swift is not packaged for Alpine aarch64** (no on-device toolchain); Kotlin and Java need a JVM (~200 MB+); Rust ~500 MB. Proposal: language *packs* installed on demand |
| Downloadable resources (Phase 5) | "Download once, then offline" | Fits, provided resources are stored on the device and only redistributable sources are used (cppreference CC-BY-SA, Python docs PSF, MDN CC-BY-SA) |
| Extensions with isolation (Phase 6) | — | Android cannot sandbox arbitrary code loaded into the app. Safe approach: **declarative** extensions (themes, snippets, grammars, templates, resource catalogs, language configs) plus optional scripts that run inside the Alpine sandbox. No loading of foreign app code |
| UI mock-up "zakomi IDE v2.0.0" | App name is "zakdroid-code", Android only | The mock-up looks like a desktop window (minimize/maximize, Debian prompt). I treat it as a visual target for the tablet layout; a desktop version would be a separate project |
| "Do not push without permission" | Until now I pushed after each fix | From now on I commit locally and push only after approval |

**Decisions (2026-11-06):** extensions are declarative only; no AI assistant.

## 7. Risks
- **Data loss on update** until a stable signing key exists (O1).
- **Device-only bugs:** without an emulator test in CI, crashes like D3 are found only by users (O3).
- **Scope:** the full request (15 languages, resources, extension marketplace, AI) is many months of work; it must be cut into milestones, each with a published, testable APK.
- **Storage:** every extra language pack adds 100–500 MB on the device.
