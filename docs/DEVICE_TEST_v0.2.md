# Device test for everything after v0.1.6

Covers what was built after `v0.1.6-alpha` and has never run on a real device: V1 (VS Code essentials), V2 (interface customization), V3 (extensions), M6 part 1 (Git and GitHub) and project export/import. The last section repeats the core checks, so a regression in the old features shows up too.

**Device:** Honor Pad 10 first. A phone as well, if one is at hand (the Explorer is a drawer there).

**Install:** the debug APK from the CI artifact `zakdroid-code-apks` of the commit under test (package `tech.zakomi.zakdroid.debug`). Uninstall the older build first: debug builds are signed with a different key each time (bug O1).

**How to record:** mark each line ✅ or ❌. For every ❌ write what happened, then use About → **Copy diagnostics** and open an issue. A line counts as tested only when it has ✅ or an issue number.

| | |
|---|---|
| Commit | |
| Device, Android version | |
| Date | |
| External keyboard used | yes / no |

## A. Project export and import (do this first; it protects the other tests' data)
- [ ] A1 Create two projects from templates (`hello` in C, `script` in Python). Edit a file in each and leave one edit **unsaved**.
- [ ] A2 Welcome page → the export icon on the `hello` row → the system picker proposes `hello.zip` → save to Downloads. Message: "Exported 1 project."
- [ ] A3 Welcome page → **Export all projects…** → `zakdroid-projects.zip`. Message: "Exported 2 projects."
- [ ] A4 Open both archives in the system Files app: one top-level folder per project, and the edit that was unsaved in A1 is inside.
- [ ] A5 **Import projects from a .zip file…** → pick `hello.zip` while `hello` still exists. A new project `hello-2` appears; `hello` is unchanged.
- [ ] A6 Import a file that is not a zip (a photo). Message "This file cannot be imported…"; no new project appears.
- [ ] A7 The real test: export all, **uninstall the app**, install it again, install the toolchain, import the archive. Both projects are back and **Run** works in each.
- [ ] A8 A project with a Git repository (after section E): export, uninstall, reinstall, import. Source Control still shows the history.
- [ ] A9 Command Palette lists **Export project…** (only while an in-app project is open), **Export all projects…** and **Import projects from a .zip file…**.

## B. V1: commands and navigation
With a hardware keyboard, use the shortcuts. Without one, use the Command Palette from the editor's ⋮ menu.
- [ ] B1 Command Palette (Ctrl+Shift+P or F1) opens, filters while typing, and runs a command.
- [ ] B2 Quick Open (Ctrl+P): typing part of a file name opens the file. Typing `:12` goes to line 12, `@` lists the symbols of the file, `>` switches to commands.
- [ ] B3 Go to Line (Ctrl+G) and Go to Symbol (Ctrl+Shift+O) move the cursor and scroll to it.
- [ ] B4 Search view: a word that occurs in several files is found; tapping a result opens the file at that line. Match case, whole word and regular expression each change the results. Text typed but not saved is found as well.
- [ ] B5 Line commands on a C file and on a Python file: toggle comment (Ctrl+/), move line (Alt+↑/↓), copy line (Shift+Alt+↑/↓), delete line (Ctrl+Shift+K). Each one is a single undo step.
- [ ] B6 The same line commands from the editor's ⋮ menu, with the touch keyboard only.
- [ ] B7 Long-press a tab: close others, close following, close saved, close all, copy path. **Reopen Closed Editor** (Ctrl+Shift+T) brings the last one back.
- [ ] B8 Status Bar: selecting text shows the selection count; tapping the position opens Go to Line.
- [ ] B9 Ctrl+=, Ctrl+- and Ctrl+0 change and reset the editor font size. Ctrl+H opens find with replace.

## C. V1 and V2: saving and settings
- [ ] C1 Settings → **Auto save** on. Type, wait two seconds: the unsaved dot on the tab disappears.
- [ ] C2 Auto save on, type, and at once rotate the screen. No text is lost.
- [ ] C3 Auto save on, type, wait two seconds, press Home, then swipe the app away from Recents. Reopen: the text is on disk.
- [ ] C4 Auto save **off**, type, press Home, come back after a minute. The tab is still open and still marked unsaved, with the text intact. (If the system kills the app meanwhile, unsaved text is lost: session restore is not built yet. Note it if it happens, with the time away.)
- [ ] C5 **Trim trailing whitespace** and **Insert final newline**: save a file with spaces at line ends and no last newline; check the result with `cat -A file` in the terminal.
- [ ] C6 **Follow the system theme**: switching the device between light and dark switches the app, including the editor and the terminal.
- [ ] C7 **Side Bar position** → Opposite side: the Activity Bar and Side Bar move. On a phone the drawer opens from the other edge.
- [ ] C8 **Interface size** at 85% and at 130%: no clipped text and no control off screen, in the workbench, the Settings screen and the dialogs.
- [ ] C9 **Status Bar** off, then on. **Line height** changes the editor only. The terminal's touch key row can be hidden and shown.
- [ ] C10 Switch the app to **Arabic** and repeat C7 and C8. The interface is right-to-left; the editor and the terminal stay left-to-right. No English text is left in the screens added after v0.1.6 (Search, Extensions, Source Control, Command Palette).

## D. V3: extensions
- [ ] D1 Extensions screen lists the two built-in packs (C/C++ and Python). Disabling one removes its snippets from **Insert Snippet…**; enabling restores them.
- [ ] D2 **Insert Snippet…** in a C file and in a Python file inserts the snippet's text at the cursor with the right indentation. (Tab stops are inserted as plain text; moving between them is not built yet.)
- [ ] D3 Build a valid extension zip (an `extension.json` plus one snippet file, as described under "Make your own") and install it. It appears in the list and its snippet can be inserted.
- [ ] D4 A zip without `extension.json` is refused with a message, and nothing is added to the list.
- [ ] D5 A zip that holds an entry named `../evil.txt` is refused. (Make one on a computer: `python3 -c "import zipfile; z=zipfile.ZipFile('bad.zip','w'); z.writestr('extension.json','{}'); z.writestr('../evil.txt','x')"`.)
- [ ] D6 Uninstall the extension from D3: it leaves the list, and it is still gone after restarting the app.

## E. M6: Git and GitHub (first time against github.com)
Prepare on github.com: one empty **public** repository, one empty **private** repository, and a fine-grained token with *Contents: Read and write* for both.
- [ ] E1 Source Control in an in-app project → **Initialize repository**. The files are listed under CHANGES.
- [ ] E2 Committing before entering a name and email asks for them. After that, **Commit all** with a message works and the commit is in HISTORY.
- [ ] E3 **Link to GitHub…** with the public repository's HTTPS address → **Push**. The commit is visible on github.com with the right author.
- [ ] E4 Edit a file on github.com → **Pull** in the app. The open editor shows the new text.
- [ ] E5 The same as E3 and E4 with the private repository.
- [ ] E6 Push without a token, then with a wrong token: a clear message each time, no crash.
- [ ] E7 Change the same line on github.com and in the app, commit, then Pull: the message names the conflicted file, and the file shows conflict markers.
- [ ] E8 Push while github.com has newer commits: "GitHub has newer commits. Pull first, then push again."
- [ ] E9 **Clone repository…** with a small public repository, then with the private one. Each becomes a project that opens and runs.
- [ ] E10 Clone with airplane mode on: a network message, and no half-made project is left on the Welcome page.
- [ ] E11 Git account → **Remove the saved token** → Push is refused again.
- [ ] E12 In a folder opened from device storage, Source Control explains that Git works for in-app projects only.

## F. Regression: the core from v0.1.6
- [ ] F1 Fresh install → install the toolchain to the end. Cancel it once in the middle and resume.
- [ ] F2 Run a C file, a C++ file, and a Python file that reads from input. Each Run tab shows the output, the build and run times, and exit code 0.
- [ ] F3 Press Run twice in a row and switch between the terminal tabs: every tab shows its own session. (This is the scenario of the withdrawn fix `d6b2e28`; the emulator tests found no app fault here, and a device has not confirmed it.)
- [ ] F4 Start a long loop in the terminal, press Home, wait two minutes, come back: the session is still running.
- [ ] F5 Open a folder from device storage, edit a file and Run it: the build uses the new text.
- [ ] F6 `make -j4` on a project with several C files on Android 12 or newer. If the build dies without an error, save `adb logcat | grep -i phantom` (risk R1: the system's phantom process killer).

## Result

| Section | Passed | Failed | Issues |
|---|---|---|---|
| A Export and import | | | |
| B Commands and navigation | | | |
| C Saving and settings | | | |
| D Extensions | | | |
| E Git and GitHub | | | |
| F Regression | | | |
