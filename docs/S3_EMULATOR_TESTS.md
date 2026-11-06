# S3: Emulator smoke tests in CI — implementation report

Date: 2026-11-06 · First green run: commit `952a65f`, GitHub Actions run 42258050449 (job "Emulator smoke tests", 4 min 4 s).

## 1. Approach and why
| Option | Verdict |
|---|---|
| **Compose UI test + AndroidX Test, in `:app` `androidTest`** (chosen) | Runs inside the real app process: real `MainActivity`, real Hilt graph, real storage, real PRoot. Finds Compose nodes by the app's own string resources, so the same test checks English and Arabic. |
| UI Automator black-box tests | Cannot read Compose state or the terminal buffer, and needs more waiting heuristics |
| Robolectric | Does not run PRoot, native code or a real `TerminalView`, so it would have missed D3 |
| `gradle connectedCheck` in the emulator job | Needs a second full Gradle build on the emulator runner; instead the build job builds the APKs once and the emulator job uses plain `adb` + `am instrument` |

What it adds:
- **Test dependencies (not in the APK):**
  - `androidx.test:runner` and `rules` 1.7.0
  - `androidx.test.ext:junit` 1.3.0
  - `androidx.compose.ui:ui-test-junit4` (version from the Compose BOM)
- **CI action:** `ReactiveCircus/android-emulator-runner` v2.38.0 (Apache-2.0, Node 24), used only in CI.
- **App code:** none, apart from one **debug-only** file, `app/src/debug/.../DebugTestHooks.kt`. It is a Hilt entry point that lets the tests read the terminal buffer and app state. It is **not in release APKs**: the release dex has 0 references to it, the debug dex has 7.

## 2. What runs where
| Piece | Where |
|---|---|
| `SmokeTest` (3 tests, no network) | `app/src/androidTest/.../smoke/SmokeTest.kt` |
| `ToolchainTest` (1 test; downloads the toolchain, about 300 MB) | `.../smoke/ToolchainTest.kt`; **skipped for pull requests** |
| Shared helpers | `SmokeSupport.kt` (hooks, locale, waiting, focus-safe taps, screenshots), `Ui.kt` (on-screen-only node lookup, dialogs, project creation) |
| Runner script | `scripts/emulator-tests.sh` installs both APKs and runs each class with `am instrument`. It collects:<ul><li>a per-test result table;</li><li>a crash and ANR scan of logcat;</li><li>the app's own crash log;</li><li>the installer log;</li><li>screenshots and diagnostics on failure.</li></ul> |
| CI | `.github/workflows/android.yml`:<ul><li>the `build` job also builds `app-full-debug-androidTest.apk`;</li><li>the new `emulator` job uses Android 14 (API 34), `google_apis`, x86_64, `pixel_6` profile, KVM;</li><li>results go to the job summary and the `emulator-results` artifact (14 days).</li></ul>The `release` job is **unchanged**: it still depends on `build` only. |

## 3. Results (run 42258050449)
| Required test | Test | Result |
|---|---|---|
| Install and launch the app | script `adb install` + `SmokeTest.launchShowsWorkbench` | ✅ Passed |
| Main interface | `launchShowsWorkbench`: Activity Bar (Explorer, Run, Terminal, Settings) and Welcome pane visible | ✅ Passed |
| Create and open a source file | `createProjectEditSaveAndRunWithoutToolchain` (C project → `main.c` opens); `newFileAndLocalizedErrorMessages` (Explorer → New file → `notes.txt` opens) | ✅ Passed |
| Code editor | type into the sora editor → tab marked unsaved → **Ctrl+S** → text checked **on disk** → tab marked saved | ✅ Passed |
| Integrated terminal | `ToolchainTest`: open a shell, type `echo zak$((6*7))` through the `TerminalView`, read `zak42` from the screen buffer | ✅ Passed |
| Run a C or Python program | `ToolchainTest`: `main.c` → `Hello from zakdroid-code!` + `exited with code 0`; `main.py` → prompt, typed input `Zakomi` → `Hello, Zakomi! …` + `exited with code 0` | ✅ Passed (C and Python) |
| Error handling, Arabic/English | Run on a `.txt` file shows the "cannot be run" message in **English**, then, after switching the app to **Arabic** (per-app locale), in Arabic. Run without a toolchain opens the toolchain sheet instead of failing | ✅ Passed |
| Crashes and unexpected exits | Per test: no `files/crashes/last-crash.txt`. Per class: `am instrument` "Process crashed" is treated as a failure. After the run: logcat scan for `FATAL EXCEPTION` / `ANR in` the app | ✅ In place, no crash or ANR seen. ⚠️ Not yet demonstrated against a real crash (that would mean pushing broken code to the default branch) |

Timing: SmokeTest 42 s; ToolchainTest 71 s (install about 40–60 s on GitHub's network); whole emulator job about 4 min.

## 4. Not covered (unsupported or not yet tested)
- **arm64:** the emulator is x86_64 (arm64 emulation is too slow in CI). The arm64 PRoot build and the Honor Pad 10 remain covered only by the device check S1.
- **Tablet layout:** only the phone profile runs (the Explorer is a drawer). A second profile, such as a 10-inch tablet, would add about 4 minutes per run.
- **Only API 34** is tested (minSdk is 26).
- **Storage Access Framework folders** (the system folder picker) are not tested.
- **The Arabic RTL layout** is not checked visually; only the strings are.
- **Network dependence:** ToolchainTest needs the Alpine mirror, so a mirror outage would turn the job red.
- **Stability:** two green runs in a row so far (runs 10 and 11); more runs will show whether it stays green.

## 5. How the tests were made reliable (CI runs 1–10)
Each failing run was diagnosed from the CI log. Nothing was skipped or weakened.

| Run | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | "found 2 nodes: New project…" | Phone layout: the Explorer in the closed drawer just off-screen repeats labels | Act only on on-screen nodes (`Ui.kt`) |
| 2 | New-file name went into the search box | The Explorer search field was the first text field on screen | Type into the field inside the dialog |
| 3 | Espresso: "root has-window-focus=false" | Focus had not returned after a dialog closed | Tap at the view's screen position, then wait for window focus |
| 4–5 | "No TerminalView on screen" | The test looked too early; the Terminal button toggles the panel | Wait for the view; open the panel only if it is closed |
| 6 | First 7 typed characters lost | The window had focus but the TerminalView did not yet | Also wait for the view's own focus |
| 7 | "No compose hierarchies found" | Activity recreated for the Arabic switch | Treat as "not found yet" |
| 2, 7–9 | Run tab never started (no emulator, view still on the previous session) | **Test bug, not an app bug:** the Compose test rule advances the app's frame clock only inside its own APIs, and the polling helper only slept, so the app never recomposed after Run | `waitFor` advances the Compose clock on each poll. An app change made on a wrong first diagnosis (commit `d6b2e28`) was reverted (`c442bf5`); `TerminalPane` is unchanged |
| 10 | — | — | **All 4 passed** |

## 6. Next steps (owner's decision)
1. **Gate releases on the emulator job** (`release: needs: [build, emulator]`) after a few more green runs, so a crash like D3 can never be published again.
2. Optional: add a tablet profile.
3. Stability check: run 11 (commit `5ad3e7c`, run 42259276226) passed 4/4 again, emulator job 3 min 6 s.
