# zakdroid-code architecture

## Modules

```
:app ──► :editor ──► :core
  │ ──► :terminal ─► :core, :termux-terminal-view ─► :termux-terminal-emulator (JNI: libtermux.so)
  │ ──► :runtime ──► :core, :terminal
  └───► :core
```

| Module | Package | Responsibility |
|---|---|---|
| `:core` | `tech.zakomi.zakdroid.core` | Brand design system (colors, Montserrat/JetBrains Mono, both themes, logo), `FileSystem` abstraction (`LocalFileSystem`, `SafFileSystem`, `WorkspaceFileSystem` router), workspaces and templates, Room (`recent_workspaces`, `open_tabs`), DataStore settings, Hilt core bindings |
| `:editor` | `tech.zakomi.zakdroid.editor` | sora-editor in Compose (`CodeEditorPane`), `EditorController` (undo/redo, find/replace, cursor), `FindReplaceBar`, TextMate registry (`TextMateSetup`) with VS Code grammars and the two zakomi themes, `EditorDocument` + `DocumentLoader` (UTF-8, BOM, CRLF, binary detection, 8 MB limit) |
| `:terminal` | `tech.zakomi.zakdroid.terminal` | `TerminalSessionManager` (sessions outlive the UI), `TerminalPane` (Termux `TerminalView` in Compose), `ExtraKeysRow` (Esc, Tab, sticky Ctrl/Alt, arrows, Home/End, PgUp/PgDn, symbols), `TerminalPalette` |
| `:runtime` | `tech.zakomi.zakdroid.runtime` | `RuntimeManager` API, `RuntimeState`, `RunScript` (build + run + exit code + timing), `WorkspaceStager` (SAF → staging copy). Flavor `full`: PRoot + Alpine installer |
| `:app` | `tech.zakomi.zakdroid` | Activity, branded splash, workbench UI, ViewModels, Settings, About |
| `:termux-terminal-*` | `com.termux.*` | Vendored Apache-2.0 sources (see `third_party/termux/README.md`) |

The modules `:lsp`, `:dap`, `:git` and `:plugins` are added in the phase that needs them. No empty placeholder modules exist.

## Build setup

- **Convention plugins** (`build-logic/convention`):
  - `zakdroid.android.application` / `.library`: SDK levels, ABIs `arm64-v8a` and `x86_64`, Java 17.
  - `zakdroid.android.compose`
  - `zakdroid.hilt`: KSP and Hilt.
  - `zakdroid.distribution`: the distribution flavor dimension.
- **Versions:** everything is in `gradle/libs.versions.toml`.
- **Toolchain:**
  - AGP 9.5 with built-in Kotlin 2.4.21; Gradle 9.8.2.
  - compileSdk / targetSdk 37, minSdk 26.
  - NDK 29.0.14206865.

### Distribution flavors

Every module with channel-specific code (`:app`, `:runtime`) applies `zakdroid.distribution`, which declares the dimension `distribution`. Phase 1 has one flavor:

- `full`: GitHub Releases and F-Droid. Ships PRoot and installs Alpine on the device.

The app runs entirely on the device (see `docs/ROADMAP.md`), so no second flavor is planned. The dimension is kept because it costs nothing and keeps the local runtime (`runtime/src/full/`) separate from the flavor-neutral `RuntimeManager` API in `runtime/src/main`.

## MVVM and data flow

```
Compose screen ──intent──► ViewModel ──► store/repository/manager ──► FileSystem / Room / DataStore / PRoot
      ▲                         │
      └──── StateFlow ◄─────────┘   one-shot events: Channel → snackbar/dialogs
```

### Shared state (singletons, main thread)
- `WorkspaceSession` holds the open workspace.
- `OpenDocumentsStore` holds open documents in tab order, with dirty flags. Explorer renames and deletes reach open tabs through it.

### ViewModels
- **`AppViewModel`:** settings, theme propagation to TextMate and the terminal palette, splash readiness.
- **`ExplorerViewModel`:** lazy file tree, name search, file operations, workspaces.
- **`EditorViewModel`:** tabs, save, close confirmation, tab persistence, Run.
- **`TerminalViewModel`, `RuntimeViewModel`, `SettingsViewModel`**

### Run (one tap, F5)
1. Save all files.
2. `WorkspaceStager.prepare` maps the file to a guest path:
   - internal project: used in place, under `/home/user/projects/…`;
   - SAF folder: mirrored to `~/.zakdroid/staging/<name>-<hash>` (changed files only, 2000 files / 64 MB limit).
3. `RunScript` builds a POSIX script:
   - C: `clang -g -Wall -Wextra -std=c17 … -lm`
   - C++: `clang++ -g -Wall -Wextra -std=c++20`
   - Python: `python3 -u`
   - No AddressSanitizer: it is poorly supported on musl.
4. A new terminal tab runs the script through PRoot. It prints build time, run time and the exit code.

## The local runtime (flavor `full`)

### Why PRoot runs from `nativeLibraryDir`
Apps targeting API 29+ cannot `exec()` files in their writable data directory (W^X). PRoot (`libproot.so`) and its loader (`libproot-loader.so`) are executables packaged as JNI libraries:
- `useLegacyPackaging = true` makes Android extract them to `nativeLibraryDir`, the only app-owned directory that allows `exec`.
- The loader's symbols are kept (`keepDebugSymbols`).
- Every program inside Alpine is started by PRoot's `execve` emulation through the loader, so no file in the data directory is ever `exec`'d directly.

### Guest layout
| Host (app storage) | Guest |
|---|---|
| `files/runtime/rootfs` | `/` (Alpine 3.24.3) |
| `files/home` | `/home/user` (bind mount) |
| `files/home/projects/<name>` | `/home/user/projects/<name>` (internal projects) |
| `/dev`, `/proc`, `/sys` | bind mounts |

The guest runs as fake root (`-0`), so `apk add` works for extra packages. It starts with a clean environment (`env -i`, `TERM=xterm-256color`, `LANG=C.UTF-8`).

### Installer
`RuntimeInstallWorker` (WorkManager, unique work, foreground notification with a Cancel action) runs `RuntimeInstaller`:

| # | Step | Resumable | Notes |
|---|---|---|---|
| 1 | Check free space | always re-run | Needs 400 MB (`StatFs`); clear error otherwise |
| 2 | Download | byte-level (`Range`) | `alpine-minirootfs-3.24.3-<arch>.tar.gz` from `dl-cdn.alpinelinux.org/alpine/v3.24/releases` |
| 3 | Verify | yes | SHA-256 pinned in `AlpineRelease.kt`; mismatch deletes the file |
| 4 | Extract | restarts the step | Lexical path checks; symlinks are recreated, never followed |
| 5 | Configure | idempotent | `resolv.conf`, `hosts`, repositories pinned to `v3.24/main` + `community`, shell profile |
| 6 | Install packages | idempotent (`apk add`) | `build-base clang python3 py3-pip`; progress parsed from `(n/N)` lines |
| 7 | Verify toolchain | yes | `clang --version`, `python3 --version` |

- **Resume:** completed steps are recorded in `install-journal.properties`; a cancelled or failed run resumes from the first unfinished step.
- **Single installer:** unique work (`KEEP`), plus an in-process mutex and an OS file lock (`install.lock`, holding the PIDs) in `InstallGuard`. While a cancelled run is still cleaning up, the state is *Stopping* and every install button is disabled.
- **No orphans:** PRoot ignores SIGTERM, and after SIGKILL its guests are reparented to init and keep running. Every PRoot process and guest therefore carries `ZAKDROID_GUEST=1` (install jobs also `ZAKDROID_JOB=install`) and is killed through `/proc` (`ProcessTree`): before each run and each `apk` call, on cancel, when a terminal tab closes, and at app start.
- **apk lock:** `apk --wait 60`. A lock still held after cleanup is retried once, then reported as *Another installation is still running* with Repair offered.
- **DNS:** Alpine's resolver (musl) has no cache, so `apk` resolves the mirror once per package, and on flaky Wi-Fi some UDP lookups fail with "DNS: transient error". The installer therefore resolves `dl-cdn.alpinelinux.org` with Android's resolver and pins it in the rootfs `/etc/hosts` for the duration of the package step, then removes it. `resolv.conf` lists the active network's DNS servers first, then 1.1.1.1 and 8.8.8.8. Network errors are retried up to 3 times; packages already installed are kept.
- **Log:** `runtime/install.log` (plus the previous run) records every step and every line of `apk` output; **Copy diagnostics** includes it.
- **Ready marker:** `.zakdroid-ready` (holding the Alpine version) is written last; it alone means *Ready*.
- **Repair** re-runs steps 5–7 with `apk fix`. **Reinstall** deletes the rootfs and starts over; `/home/user` is kept.
- **Status Bar** shows the state: *Not installed* / *Installing x%* / *Ready* / *Error*.

### Background execution
- **Terminal keep-alive:** `TerminalKeepAliveService` (`:terminal`) is a foreground service, type `specialUse`. It runs while at least one terminal session or program is running and stops when the last one ends. It holds no state; sessions live in `TerminalSessionManager`.
- **Battery optimization:** Settings → Background activity shows whether the app is exempt. It offers the system exemption dialog and, on Honor/Huawei, opens the vendor's *App launch* screen if found (otherwise the app's settings page). The toolchain sheet shows the same card while the app is still restricted.

### Pinned versions
- Alpine 3.24.3 SHA-256 (cross-checked against the official `alpinelinux/docker-alpine` `v3.24` branch):
  - aarch64: `4d3f80afaebd447c5591a63a73bde380a149fe8b2d85488cafabb2ff97551f50`
  - x86_64: `f8e1cafba9b1b283b5d30d06f9885c8d2bc161ffe9083ade8e2e157813a53407`
- PRoot: termux/proot `v5.1.107.101` (`b27d81a`).
- talloc: `talloc-2.5.0` (`655f8a6`).

## Editor

- **One `CodeEditor` view for all tabs.** Each `EditorDocument` keeps its own sora `Content` (text, cursor, undo history), so switching tabs swaps the content and keeps all three.
- **Fresh language per switch.** A new TextMate language instance is created on every tab switch, because sora destroys the previous one; grammars stay cached.
- **Large files.** Decoding and line indexing run on `Dispatchers.Default`. sora renders large files incrementally, and the 10k-line target is in the test checklist.
- **Features from sora-editor:**
  - highlighting, bracket-pair highlight, auto-indent and auto-closing pairs (VS Code language configs);
  - identifier completion;
  - regex find/replace (`EditorSearcher`);
  - undo/redo, word wrap, pinch zoom;
  - sticky scroll (3 lines), pinned line numbers;
  - ligature toggle (`liga`/`calt`).
- **Not in Phase 1:** code folding, multi-cursor (both checked below), minimap (tablet-only, later phase).

### Verified gaps in sora-editor 0.24.7 (tag `0.24.7`, commit `ab9f2cb`)

| Feature | Evidence in the source | Plan |
|---|---|---|
| Code folding | `lang/folding/FoldingRegion.java` is a data class (start, end, `collapsed` flag) with **no references** in `widget/`, `EditorRenderer` or the layout classes. `language-textmate` computes indent ranges (`IndentRange.computeRanges`) only to draw code-block guide lines. The README feature list has no folding. | **Implement ourselves in M7**, as a contained patch on a sora fork: hide folded line ranges in the layout and renderer, add gutter toggles, feed ranges from clangd `foldingRange` (M1 LSP) with the TextMate indent ranges as fallback. Offer it upstream; drop the fork if upstream ships folding first. |
| Multi-cursor | `Content` owns exactly one `Cursor` (`Content.getCursor()`), which holds one `leftSel`/`rightSel` pair. Editing, IME and undo all go through that single cursor; there is no cursor collection anywhere. The README feature list has no multi-cursor. | **Partial support, M7:** "Select all occurrences" and "Change all occurrences" (Ctrl+Shift+L / Ctrl+F2) built on `EditorSearcher` plus a batch edit, which covers the common rename-in-file case. True multi-caret editing (Alt+Click, Ctrl+Alt+↓) needs a deep rewrite of sora's cursor, IME and undo, so **wait for upstream**; LSP rename (M1) covers symbol renames. |

## Theming and accessibility

- **Themes:**
  - "Zakomi Navy Dark" (default) and "Zakomi Light".
  - Navy `#0A1F52` for the Activity Bar and Status Bar in both themes.
  - `#1E3A8A` only as a background (selection, active states) on dark surfaces; `#7DA2FF` for accent text and icons there.
- **Contrast:** `scripts/check_contrast.py` checks 80 text/UI pairs against WCAG AA; CI fails on any regression. The report is in `docs/CONTRAST.md`.
- **Localization:**
  - English and Arabic; RTL for the whole UI.
  - The editor, terminal and extra-keys row are forced LTR.
  - Per-app language selection via `locales_config.xml` (Android 13+).
- **Accessibility:** interactive elements have content descriptions and roles (tabs, switches, radio buttons), with 48 dp touch targets in settings and dialogs.
