# Contributing to zakdroid-code

Thank you for helping. zakdroid-code is an Android IDE for C, C++ and Python that runs entirely on the device.
Bug reports, translations, documentation and code are all welcome.

## Before you start
- Read [`docs/DEVELOPMENT_ROADMAP.md`](docs/DEVELOPMENT_ROADMAP.md) and [`docs/BUGS_AND_FIXES.md`](docs/BUGS_AND_FIXES.md).
- For anything larger than a small fix, open an issue first so the approach can be agreed before you write the code.
- Project decisions that pull requests must respect:
  - **On-device only.** No server or account is needed. The network is used only to download a toolchain or resource pack once.
  - **No built-in AI assistant.** The only allowed link is starting the user's own Claude Code CLI in the terminal (roadmap M9).
  - **Declarative extensions only:** themes, snippets, grammars, templates, resource catalogs and language configs. No extension code runs inside the app.
  - **No secrets** in the repository, logs or plain files. Signing uses environment variables only, and no keystore is ever committed.

## Build and check
Requirements and commands are in the [README](README.md#build). Before opening a pull request, run:

```bash
python3 scripts/check_contrast.py                     # WCAG AA check of both themes
./gradlew testDebugUnitTest testFullDebugUnitTest     # unit tests
./gradlew :app:assembleFullDebug :app:lintFullDebug   # build + lint (must report "No issues found")
```

CI runs the same steps on every push and pull request, plus emulator smoke tests (`app/src/androidTest`, run by `scripts/emulator-tests.sh`). To run them yourself on an emulator or device (Android 13+):

```bash
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest
scripts/emulator-tests.sh app/build/outputs/apk/full/debug results 0   # 1 = also ToolchainTest (downloads ~300 MB)
```
(copy `app-full-debug-androidTest.apk` from `app/build/outputs/apk/androidTest/full/debug/` into the same folder first)

## Code guidelines
- Kotlin + Jetpack Compose. Follow the style of the surrounding code; keep changes small and focused.
- Every bug fix comes with a test that fails without the fix, where a JVM test is possible.
- **Strings:** every user-visible text is a string resource, in **both** `values/strings.xml` (English) and `values-ar/strings.xml` (Arabic). Do not build messages in ViewModels.
- **Paths and files:** validate file names (`FileNames.validate`) and never follow paths outside the workspace.
- **Dependencies:** ask in an issue before adding a new library. It must have a license compatible with Apache-2.0, and must be listed in `THIRD_PARTY_LICENSES.md`.
- **Themes:** new colors must keep `scripts/check_contrast.py` passing.

## Pull requests
- **Nothing is merged into the default branch unless** the unit tests and lint pass in CI, **and**, for a change the user can see, the feature has been tried on a real device. Say which device in the pull request, or tick the matching lines of [`docs/DEVICE_TEST_v0.2.md`](docs/DEVICE_TEST_v0.2.md). Emulator tests do not replace this.
- One topic per pull request, with a clear description of what changed and how you tested it (device model and Android version, if you tested on a device).
- Fill in the pull request template.
- By contributing, you agree that your contribution is licensed under the project's [Apache License 2.0](LICENSE).
- The names and logo are not covered by that license; see [`TRADEMARKS.md`](TRADEMARKS.md).

## Reporting bugs
Use the bug report form in Issues. Attach the text from **Settings → About → Copy diagnostics**. It contains the app version, device, runtime state and the last crash trace, and no personal files.

Security problems: do **not** open a public issue. See [`SECURITY.md`](SECURITY.md).

All contributors follow the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## المساهمة (ملخص بالعربية)
- المساهمات مرحَّب بها: بلاغات الأخطاء، والترجمة، والتوثيق، والشيفرة.
- افتح بلاغًا (issue) قبل أي تغيير كبير.
- التزم بقرارات المشروع: العمل على الجهاز فقط، ولا مساعد ذكاء اصطناعي، والإضافات وصفية فقط، ولا أسرار في المستودع.
- شغّل فحص التباين والاختبارات وlint قبل طلب الدمج.
- لا دمج في الفرع الرئيسي دون نجاح الاختبارات وlint، ودون تجربة على جهاز حقيقي لكل ميزة يراها المستخدم.
- أضف كل نص يظهر للمستخدم بالإنجليزية والعربية معًا.
- تُرخَّص مساهمتك بموجب رخصة Apache-2.0.
- أبلغ عن الثغرات الأمنية بشكل خاص كما في `SECURITY.md`، لا في بلاغ عام.
