# Roadmap

**Direction (decided 2026-10-30):** zakdroid-code runs **entirely on the device**.
The only network use is the one-time toolchain download (Alpine packages, about 350 MB).
After that, editing, building, running, the terminal, Git, the debugger and language
intelligence all work offline. There is no remote-server mode and no cloud AI feature.

Targets: Honor Pad 10 (tablet) and an Android phone. Languages: C, C++, Python.

| Milestone | Scope | Status |
|---|---|---|
| Phase 1 | Brand, themes, Explorer, editor, tabs, terminal, one-tap Run, toolchain installer | done (v0.1.0-alpha) |
| **M0** | Device feedback loop: fix every critical/high device bug; installer lock fix (v0.1.1); foreground service for terminal sessions; battery-optimization guidance for Honor/MagicOS; stable alpha signing | in progress |
| M1 | Language intelligence on the device: clangd (+ clang-format, clang-tidy) and python-lsp-server or basedpyright + ruff inside Alpine; `:lsp` module; Problems panel; Command Palette, Quick Open, VS Code keymap; compile_commands.json | planned |
| M2 | Student workflow: multi-file C/C++ (headers, Makefile, CMake), Tasks, exercise mode with test cases and diffs, timeouts and output limits, valgrind if available, offline docs (cppreference, Python), more templates | planned |
| M3 | Local Git: init, status, diff, stage, commit, history, branches. Optional push/pull to a Git host when the user chooses to | planned |
| M4 | Debugger: lldb-dap (C/C++) and debugpy (Python) on the device; feasibility spike first (ptrace under PRoot) | planned |
| ~~M5~~ | ~~Remote mode with a Debian server~~ | **removed**: the app does not depend on servers |
| ~~M6~~ | ~~Claude AI assistant (server CLI or API)~~ | **removed**: needs a server or cloud service |
| M7 | Extensions and polish: themes, snippets, language configs; minimap on tablets; folding and multi-cursor (see ARCHITECTURE.md); workspace backup/restore as zip | planned |
| M8 | Release readiness: full test matrix, signed release, F-Droid metadata, changelog, user guide (EN + AR) | planned |

Consequences of the decision:
- The `distribution` flavor dimension stays with the single `full` flavor; the planned
  remote-only `play` flavor is dropped.
- No SSH library is planned.
