# Phase 1 device test checklist

**Device:** a real arm64 phone (Android 8.0+, 3–4 GB RAM, 400 MB free). If you can, also a tablet or foldable.

**Install the APK:**
- Debug: `adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk` (package `tech.zakomi.zakdroid.debug`)
- Or the CI artifact `zakdroid-code-apks`.

Mark each line ✅ / ❌ and note the device and Android version.

## A. Brand and first launch
- [ ] A1 Launcher icon: navy adaptive icon with the white interlocking Z. On Android 13+ with themed icons enabled, the monochrome Z is used.
- [ ] A2 Cold start: the system splash shows the logo on a dark background, then the branded splash shows the logo, **zakdroid-code** and **B Y  Z A K O M I  T E C H** in spaced caps.
- [ ] A3 Workbench:
  - Activity Bar on the left with the logo at the top, then Explorer / Run / Terminal, and Settings at the bottom.
  - Status Bar (navy) shows the toolchain state: **Not installed**.
  - Welcome page in the editor area.

## B. Themes (WCAG AA)
- [ ] B1 Settings → Color theme → **Zakomi Light**: the workbench, syntax colors and terminal all switch. Text stays readable everywhere.
- [ ] B2 Back to **Zakomi Navy Dark**. Accent text and icons are light blue (#7DA2FF); the selection background is #1E3A8A.

## C. Explorer
- [ ] C1 Welcome → **New project…** → name `hello`, template **C — Hello world** → `main.c` opens in a tab.
- [ ] C2 Header buttons create a file and a folder. Long-pressing a folder offers New file / New folder / Rename / Move… / Delete.
- [ ] C3 **Rename** a file that is open in a tab: the tab title updates.
- [ ] C4 **Move…** a file, then long-press a target folder → **Move here**. The file moves and its open tab stays valid. "Move to project root" also works.
- [ ] C5 **Delete** asks for confirmation; deleting a folder closes the open (saved) files inside it. Deleting or moving a folder that contains an *unsaved* open file is refused with a message.
- [ ] C6 **Open folder…** (SAF): pick e.g. `Documents/code`. The tree loads; create, rename and delete work there too.
- [ ] C7 Kill and restart the app: **Recent** lists the SAF folder; reopening it restores the open tabs.
- [ ] C8 Search box: typing `main` finds matching files in sub-folders and shows their relative paths.
- [ ] C9 Phone: Explorer opens as a drawer (from the right in Arabic). Tablet: Explorer is a side bar; the Explorer icon toggles it.

## D. Editor
- [ ] D1 Highlighting for `.c`, `.cpp`, `.h`, `.py`.
- [ ] D2 Bracket pair highlight, auto-closing `()` `{}` `""`, auto-indent after `{` and after `:` in Python.
- [ ] D3 Find / Replace (toolbar 🔍 or Ctrl+F):
  - regex `(\w+)\s*=` replaced with `$1 =` works;
  - the "n of m" counter updates;
  - Aa / ab / .* toggles work;
  - an invalid regex shows an error.
- [ ] D4 Undo and Redo buttons (and Ctrl+Z / Ctrl+Y on a keyboard).
- [ ] D5 Settings: font size, ligatures (`!=`, `->`, `>=` merge when on), word wrap and tab size apply immediately.
- [ ] D6 Pinch-to-zoom in the editor; sticky scroll shows the enclosing function header while scrolling.
- [ ] D7 An unsaved change shows a dot on the tab. Closing the tab asks **Save / Don't save / Cancel**.
- [ ] D8 Phone: swiping on the path bar under the tabs switches tabs.
- [ ] D9 Rotate the device: tabs, cursor position and undo history are kept.
- [ ] D10 **Performance (10k lines):** create a large file in the terminal:
  `python3 -c "print('\n'.join(f'int f{i}(int x) {{ return x * {i}; }} // line {i}' for i in range(10000)))" > /home/user/projects/hello/big.c`

  Refresh the Explorer and open `big.c`:
  - it opens within ~1–2 s;
  - scrolling and flinging are smooth;
  - typing at the end has no lag;
  - no "app not responding" dialog.
- [ ] D11 A binary file (e.g. a compiled program) shows "not UTF-8 text" instead of opening.

## E. Toolchain installation
- [ ] E1 Tap **Not installed** in the Status Bar → the sheet shows Alpine Linux 3.24.3 (aarch64) · PRoot 5.1.107.101, the 400 MB note and the package list → **Install** (allow notifications on Android 13+).
- [ ] E2 The steps advance: Checking free space → Downloading → Verifying → Extracting → Configuring → Installing clang, build tools and Python → Checking the toolchain. The Status Bar shows **Installing x%**.
- [ ] E3 Leave the app during installation: the notification shows progress, and its **Cancel** action works.
- [ ] E4 **Cancel** during the download → the state becomes **Not installed** and the button reads **Resume install**. Resuming continues the download instead of restarting it.
- [ ] E5 Airplane mode while packages install → **Error** with a readable message → turn the network on → **Retry** succeeds.
- [ ] E6 With less than 400 MB free: a clear "Not enough storage: 400 MB … needed, N MB available" error.
- [ ] E7 Finished: the Status Bar shows **Ready** (green dot).
- [ ] E8 Settings → Toolchain → **Repair** finishes and returns to Ready. **Reinstall** asks for confirmation, reinstalls, and keeps `/home/user/projects`.

## F. Run (one tap / F5)
- [ ] F1 Open `main.c` → **Run** (▶): a terminal tab `▶ main.c` opens and shows `▶ clang main.c`, "built in x s", the output, then a green **■ main.c exited with code 0 · x.xx s**.
- [ ] F2 Add a syntax error → Run → compiler errors, then a red **✖ Build failed with exit code 1**.
- [ ] F3 `return 3;` → red footer with **exit code 3**.
- [ ] F4 C++ template (`std::vector`, `std::cout`) builds with `-std=c++20` and runs.
- [ ] F5 Python template: `input()` prompt; typing a name in the terminal works.
- [ ] F6 Infinite loop `while (1) {}` → tap **CTRL** in the key row, then `c` → the program stops with exit code 130.
- [ ] F7 Run from a SAF folder project (C file that `#include`s a local header) builds and runs; output files are not written back into the SAF folder.
- [ ] F8 Run before the toolchain is installed → the toolchain sheet opens with "needed to build and run code".
- [ ] F9 Running a `.h` or `.txt` file shows "cannot be run".

## G. Terminal
- [ ] G1 Terminal icon / **+** opens `sh 1`, `sh 2`; tabs switch and close. The shell starts in the project folder.
- [ ] G2 `clang --version`, `python3 --version`, `pip --version`, `apk add htop` all work. `ls --color` shows colors in both themes.
- [ ] G3 Extra keys: ESC, TAB (completion), CTRL/ALT (sticky, highlighted while armed), arrows (shell history), HOME/END, PGUP/PGDN, `- / | ~ _ &`.
- [ ] G4 Long-press to select text → Copy; long-press → Paste.
- [ ] G5 Pinch zoom changes the terminal font size (also saved in Settings).
- [ ] G6 Rotate or switch theme: sessions keep running.

## H. Hardware keyboard (optional)
- [ ] H1 Ctrl+S save, Ctrl+Shift+S save all, Ctrl+F find, Ctrl+W close tab, Ctrl+Tab / Ctrl+Shift+Tab switch tabs, Ctrl+` terminal, Ctrl+B explorer, Ctrl+, settings, F5 run.

## I. Arabic / RTL
- [ ] I1 Android 13+: Settings → **App language** → العربية (older Android: switch the device language).
- [ ] I2 The UI mirrors (Activity Bar on the right, drawer from the right) and all strings are Arabic.
- [ ] I3 The **code editor, terminal and extra-keys row stay LTR**; code is not mirrored.

## J. Accessibility and layouts
- [ ] J1 TalkBack: Activity Bar items, tabs (with "Unsaved changes"), key-row keys and settings switches are announced with their names.
- [ ] J2 Phone portrait and landscape, tablet, split-screen and (if available) DeX or desktop windowing: the layout adapts at 600 dp; nothing is clipped.

## Known Android behavior
- **Android 12–13:** the "phantom process killer" may stop large parallel builds. Build with `make -j2`. On Android 14+ you can turn on *Developer options → Disable child process restrictions*.
- **Phase 1 limits (see README):** no code folding, multi-cursor or minimap yet.
