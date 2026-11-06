# Security policy

## Supported versions
zakdroid-code is in alpha. Only the **latest published release** receives security fixes.

## Reporting a vulnerability
Please **do not open a public issue**.
- Report privately through GitHub: **Security → Report a vulnerability** on this repository. This is GitHub's private vulnerability reporting, which the owner enables in the repository settings.
- Include the affected version (Settings → About → Copy diagnostics), the steps to reproduce, and the impact.

You will get a reply as soon as the maintainer can; this is a volunteer project. Once a fix is released, the report is credited unless you prefer otherwise.

## Scope and known limits
- **Alpha builds are debuggable** (open issue O2 in `docs/BUGS_AND_FIXES.md`). With `adb` access, anyone can read the app's private files. Do not keep secrets in alpha installs.
- **PRoot is not a security sandbox.** Programs you run in the terminal run with the app's own permissions and can read the app's files. Run only code you trust.
- **The toolchain download** is pinned to Alpine 3.24.3, and the root file system is checked against a SHA-256 hash. Packages come from the Alpine mirror over HTTPS and are verified by `apk` signatures.
- **Extensions are declarative only.** No extension code runs inside the app. A way to make an extension execute code is a vulnerability.

---

**بالعربية:**
- لا تنشر الثغرات الأمنية في بلاغ عام. أبلغ عنها بشكل خاص عبر GitHub: ‏Security ← Report a vulnerability.
- الدعم الأمني لآخر إصدار منشور فقط.
- إصدارات ألفا قابلة للتصحيح (debuggable)، وPRoot ليس عزلًا أمنيًا، فلا تشغّل إلا شيفرة تثق بها.
