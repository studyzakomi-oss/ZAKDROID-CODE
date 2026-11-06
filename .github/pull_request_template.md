## What and why
<!-- What does this change, and which issue or bug ID (e.g. O3) does it address? -->

## How it was tested
<!-- Commands run and their result; device model and Android version if tested on a device. -->
- [ ] `python3 scripts/check_contrast.py`
- [ ] `./gradlew testDebugUnitTest testFullDebugUnitTest`
- [ ] `./gradlew :app:assembleFullDebug :app:lintFullDebug` (lint: "No issues found")

## Checklist
- [ ] New user-visible text is in both `values/strings.xml` and `values-ar/strings.xml`
- [ ] A bug fix includes a test that fails without the fix (where a JVM test is possible)
- [ ] No secrets, keys or personal data added
- [ ] New dependencies (if any) are agreed in an issue and listed in `THIRD_PARTY_LICENSES.md`
- [ ] `CHANGELOG.md` updated under "Unreleased"
