# Comparison with VS Code

What VS Code offers, what zakdroid-code has, and where each gap is planned. Written on 2026-11-11, against VS Code's desktop feature set.
Project decisions limit the scope: everything runs on the device, there is no AI assistant, and extensions are declarative only (see [`DEVELOPMENT_ROADMAP.md`](DEVELOPMENT_ROADMAP.md)).

Status: ✅ available · 🆕 added in milestone V1 (this change) · ◐ partial · ⏳ planned · ✖ not planned

## Workbench and navigation

| VS Code feature | zakdroid-code | Notes |
|---|---|---|
| Activity Bar, Side Bar, Panel, Status Bar | ✅ | Phone layout uses a drawer |
| Explorer: tree, new, rename, move, delete, name filter | ✅ | |
| Command Palette (Ctrl+Shift+P, F1) | 🆕 | 41 commands, fuzzy filter, shows key bindings |
| Quick Open (Ctrl+P) | 🆕 | Fuzzy file search; open editors first |
| Go to Line (Ctrl+G, `:`) | 🆕 | `42` or `42:7`; also from the Status Bar position |
| Go to Symbol in File (Ctrl+Shift+O, `@`) | 🆕 ◐ | Pattern-based for C, C++ and Python; replaced by clangd/pylsp in M1 |
| Search view: Find in Files (Ctrl+Shift+F) | 🆕 | Match case, whole word, regex; searches unsaved text of open editors; jumps to the match |
| Replace in Files | ⏳ M1 | |
| Tabs: close others / to the right / saved / all, copy path | 🆕 | Long-press a tab |
| Reopen Closed Editor (Ctrl+Shift+T) | 🆕 | Last 20 tabs |
| Split editor, editor groups | ⏳ M10 | Tablets only |
| Breadcrumbs | ◐ | The path is shown above the editor; not clickable yet |
| Zen mode, minimap | ⏳ M10 | Minimap on tablets |
| Go to Symbol in Workspace (Ctrl+T) | ⏳ M1 | Needs a language server |
| Customizable key bindings | ⏳ M1 | Fixed VS Code keymap today |
| Settings UI | ✅ | No `settings.json` |
| Settings Sync, accounts, Live Share, remote development | ✖ | On-device only |

## Editing

| VS Code feature | zakdroid-code | Notes |
|---|---|---|
| Syntax highlighting (TextMate), bracket pairs, auto-indent, auto-close | ✅ | C, C++, Python |
| Find and replace in file, regex, whole word | ✅ | Ctrl+H now opens with the replace row |
| Undo/redo, word wrap, pinch zoom, sticky scroll | ✅ | Sticky scroll is now a setting |
| Toggle Line Comment (Ctrl+/) | 🆕 | |
| Move line up/down (Alt+↑/↓), copy line up/down (Shift+Alt+↑/↓), delete line (Ctrl+Shift+K) | 🆕 | One undo step each |
| Auto Save | 🆕 | "After delay" (1 s after the last edit) or off |
| Trim trailing whitespace, insert final newline on save | 🆕 | |
| Render whitespace, line numbers on/off | 🆕 | |
| Font zoom (Ctrl+=, Ctrl+-, Ctrl+0) | 🆕 | |
| Selection count in the Status Bar | 🆕 | |
| Word-based completion | ✅ | |
| IntelliSense, diagnostics, hover, go to definition, rename, format | ⏳ M1 | clangd, pylsp/ruff |
| Problems panel | ⏳ M1 | |
| Snippets | 🆕 ◐ | Insert Snippet command with VS Code-format snippet files; completion while typing and Tab between stops come with M5 |
| Code folding | ⏳ M7 | Missing in the editor component; see `ARCHITECTURE.md` |
| Multi-cursor | ⏳ M7 ◐ | "Change all occurrences" first |
| Block comment, Emmet, column selection | ⏳ | After M7 |
| Encodings other than UTF-8; change line ending | ⏳ M10 | Line endings are kept as found |
| Diff editor, timeline / local history | ⏳ M6 | With Git |
| Markdown preview, notebooks | ⏳ M2 | With language packs |

## Run, debug, tools

| VS Code feature | zakdroid-code | Notes |
|---|---|---|
| Integrated terminal, several tabs | ✅ | Real Alpine Linux shell |
| Run a file (F5) | ✅ | C, C++, Python |
| Tasks, multi-file builds (Make, CMake) | ⏳ M7 | |
| Debugger (breakpoints, variables, call stack) | ⏳ M8 | lldb-dap, debugpy |
| Source control (Git, GitHub) | 🆕 ◐ | Init, changes, commit all, history, push, pull, clone over HTTPS with a token. Diff view, staging single files and branches come in M6 part 2 |
| Test explorer | ⏳ M7 | Exercise mode with tests |
| More languages | ⏳ M2 | Language packs |
| Extensions | 🆕 ◐ | Declarative only. Install from a .zip, enable, disable, uninstall; snippets today, themes/grammars/templates in M5. No marketplace |
| Themes, icon themes | ◐ | Two built-in themes, optional "follow the system"; more through M5 |
| Layout customization (side bar side, zoom, status bar) | 🆕 | Settings → Workbench |
| AI assistant (Copilot, chat) | ⏳ M9 | Not built in; the external Claude Code CLI in the terminal is planned |

## Key bindings (hardware keyboard)

| Keys | Action | | Keys | Action |
|---|---|---|---|---|
| Ctrl+Shift+P, F1 | Command Palette 🆕 | | Ctrl+/ | Toggle line comment 🆕 |
| Ctrl+P | Go to file 🆕 | | Alt+↑ / Alt+↓ | Move line 🆕 |
| Ctrl+G | Go to line 🆕 | | Shift+Alt+↑ / ↓ | Copy line up / down 🆕 |
| Ctrl+Shift+O | Go to symbol 🆕 | | Ctrl+Shift+K | Delete line 🆕 |
| Ctrl+Shift+F | Find in files 🆕 | | Ctrl+= / Ctrl+- / Ctrl+0 | Font zoom 🆕 |
| Ctrl+F / Ctrl+H | Find / replace (Ctrl+H 🆕) | | Ctrl+Shift+T | Reopen closed editor 🆕 |
| Ctrl+S / Ctrl+Shift+S | Save / save all | | Ctrl+W | Close editor |
| Ctrl+Tab / Ctrl+Shift+Tab | Next / previous editor | | Ctrl+B / Ctrl+Shift+E 🆕 | Explorer |
| Ctrl+` | Terminal | | Ctrl+, | Settings |
| F5 | Run | | | |

While the terminal has focus, Ctrl+P, Ctrl+G, Ctrl+H, Ctrl+- and Ctrl+0 go to the shell, and the line commands apply only while the code editor has focus.
On a touch screen the same commands are in the editor's ⋮ menu, in the tab's long-press menu and in the Command Palette (⋮ → Command Palette…).
