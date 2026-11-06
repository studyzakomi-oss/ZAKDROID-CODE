# Development roadmap

**Direction:** zakdroid-code runs **entirely on the device** (decided 2026-10-30). The only network use is downloading a toolchain or resource pack once; everything after that works offline.
**Targets:** Honor Pad 10 (tablet) and Android phones.
**Rule:** one small milestone at a time; each ends with tests, a published pre-release APK (arm64 + universal, SHA-256, PRoot source) and the owner's review. Nothing is pushed or published without approval.

Status: ✅ done · 🔧 in progress · ⏳ planned · ❓ needs a decision

## Done
| Milestone | Scope | Status |
|---|---|---|
| Phase 1 | Brand, themes, Explorer, editor, tabs, terminal, one-tap Run, toolchain installer | ✅ v0.1.0 |
| M0 (part) | Device fixes D1–D4 (lock, DNS, terminal crash, orphans), keep-alive service, battery guidance, diagnostics | ✅ v0.1.1–v0.1.4 |

## Next: stabilization first (Phase 2 of the request)
| Step | Task | Fixes | Status |
|---|---|---|---|
| S1 | Confirm v0.1.4 on the tablet (Run, terminal, `scanf` input) | D3 | 🔧 waiting for device test |
| S2 | Stable signing key; publish signed **release** builds instead of debug | O1, O2 | ❓ owner creates the key |
| S3 | Emulator smoke test in CI (start app, open terminal, open file) | O3 | ⏳ |
| S4 | Stale SAF copy; keep-alive crash path; Reinstall with open terminals | O4, O5, O6 | ✅ v0.1.5 (device check pending) |
| S5 | Undo-to-saved dirty state; localized error messages; CI action upgrades and one-job release | O7–O10 | ⏳ |
| S6 | Open-source files: CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CHANGELOG, issue/PR templates | O11 | ⏳ |

## Then: features (the request's phases, re-ordered by dependency)
| Milestone | Scope | Depends on | Status |
|---|---|---|---|
| M1 | Language intelligence for C/C++/Python: clangd, pylsp/ruff, Problems panel, Command Palette, Quick Open, keymap | S1–S3 | ⏳ |
| M2 | **Language packs** (request phase 3): a language = a manifest + grammar + templates + docs + an optional Alpine toolchain installed on demand. First packs: Java, JavaScript/TypeScript (Node), HTML/CSS (in-app preview), SQL (SQLite), Bash, PHP, Go, Rust, Kotlin. **Swift: no on-device toolchain on Alpine aarch64**, so editing and highlighting only | M1 | ❓ confirm language list |
| M3 | Onboarding (choose languages → categories → resources) + categories linked to languages (request phases 4, 9) | M2 | ⏳ |
| M4 | Resource library (request phase 5): catalog files (JSON) listing redistributable docs/tutorials/examples; SHA-256 check; no executables without explicit approval; offline storage | M3 | ⏳ |
| M5 | **Declarative extensions only** (decided 2026-11-06): themes, snippets, grammars, templates, resource catalogs, language configs. Manifest, install/uninstall/enable/disable/update, permissions, validation, packaging, extension-project generator, GitHub-ready export. No extension code runs in the app | M2 | ⏳ |
| M6 | Local Git (init, status, diff, stage, commit, history, branches; optional push/pull) | M1 | ⏳ |
| M7 | Multi-file C/C++ (Make, CMake), Tasks, exercise mode with tests | M1 | ⏳ |
| M8 | Debugger (lldb-dap, debugpy); feasibility spike under PRoot first | M7 | ⏳ |
| M10 | Polish: folding, multi-cursor (partial), minimap on tablets, workspace backup/restore | M1 | ⏳ |
| M11 | Release readiness: full test matrix, signed release, F-Droid metadata, user guide (EN + AR), and the documents of request phase 10 | all | ⏳ |

Removed: remote mode with a Debian server and the server-based Claude assistant (2026-10-30); any AI coding assistant, on-device or cloud (2026-11-06).
