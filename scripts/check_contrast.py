#!/usr/bin/env python3
"""WCAG 2.1 contrast check for both zakdroid-code themes.

Reads the editor themes (editor/src/main/assets/textmate/themes/*.json) and the
workbench palette (core/.../designsystem/WorkbenchColors.kt) and checks every
text/background pair against AA (4.5:1 normal text, 3:1 for UI parts such as the
cursor and indicators). Writes docs/CONTRAST.md and exits 1 on any failure.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEMES = ROOT / "editor/src/main/assets/textmate/themes"
WORKBENCH = ROOT / "core/src/main/java/tech/zakomi/zakdroid/core/designsystem/WorkbenchColors.kt"
BRAND = ROOT / "core/src/main/java/tech/zakomi/zakdroid/core/designsystem/BrandColors.kt"


def lum(hex_color: str) -> float:
    h = hex_color.lstrip("#")[-6:] if len(hex_color.lstrip("#")) == 8 else hex_color.lstrip("#")
    def ch(v):
        c = int(v, 16) / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(h[i:i + 2]) for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a: str, b: str) -> float:
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def parse_workbench():
    brand = {m.group(1): "#" + m.group(2)[2:] for m in re.finditer(r"val (\w+) = Color\(0x([0-9A-Fa-f]{8})\)", BRAND.read_text())}
    themes = {}
    text = WORKBENCH.read_text()
    for name, body in re.findall(r"val (Zakomi\w+Colors) = WorkbenchColors\((.*?)\n\)", text, re.S):
        vals = {}
        for key, expr in re.findall(r"(\w+) = ([^,\n]+),", body):
            m = re.match(r"Color\(0x([0-9A-Fa-f]{8})\)", expr)
            if m:
                vals[key] = "#" + m.group(1)[2:]
            elif expr.startswith("BrandColors."):
                vals[key] = brand[expr.split(".")[1]]
        themes[name] = vals
    return themes


def main() -> int:
    rows, failures = [], 0

    def check(theme, what, fg, bg, minimum):
        nonlocal failures
        r = ratio(fg, bg)
        ok = r >= minimum
        failures += 0 if ok else 1
        rows.append(f"| {theme} | {what} | `{fg}` on `{bg}` | {r:.2f}:1 | {minimum}:1 | {'✅' if ok else '❌'} |")

    for path in sorted(THEMES.glob("*.json")):
        t = json.loads(path.read_text())
        c, name = t["colors"], t["name"]
        bg, fg = c["editor.background"], c["editor.foreground"]
        check(name, "editor text", fg, bg, 4.5)
        check(name, "text on selection", fg, c["editor.selectionBackground"], 4.5)
        check(name, "text on current line", fg, c["editor.lineHighlightBackground"], 4.5)
        check(name, "text on find match", fg, c["editor.findMatchBackground"], 4.5)
        check(name, "line numbers", c["editorLineNumber.foreground"], bg, 4.5)
        check(name, "cursor", c["editorCursor.foreground"], bg, 3.0)
        check(name, "completion text", c["editorSuggestWidget.foreground"], c["editorSuggestWidget.background"], 4.5)
        check(name, "completion match (selected row)", c["editorSuggestWidget.highlightForeground"], c["editorSuggestWidget.selectedBackground"], 4.5)
        for token in t["tokenColors"]:
            check(name, f"syntax: {token['name']}", token["settings"]["foreground"], bg, 4.5)

    for name, w in parse_workbench().items():
        label = {"ZakomiNavyDarkColors": "Zakomi Navy Dark", "ZakomiLightColors": "Zakomi Light"}[name]
        check(label, "UI text on side bar", w["textPrimary"], w["sideBar"], 4.5)
        check(label, "secondary text on side bar", w["textSecondary"], w["sideBar"], 4.5)
        check(label, "secondary text on editor", w["textSecondary"], w["editor"], 4.5)
        check(label, "accent text on side bar", w["accentText"], w["sideBar"], 4.5)
        check(label, "accent text on editor", w["accentText"], w["editor"], 4.5)
        check(label, "active tab text", w["tabActiveText"], w["tabActive"], 4.5)
        check(label, "inactive tab text", w["tabInactiveText"], w["tabInactive"], 4.5)
        check(label, "active tab indicator", w["tabActiveIndicator"], w["tabActive"], 3.0)
        check(label, "status bar text", w["statusBarText"], w["statusBar"], 4.5)
        check(label, "activity bar icon", w["activityBarIcon"], w["activityBar"], 3.0)
        check(label, "activity bar active icon", w["activityBarIconActive"], w["activityBar"], 3.0)
        check(label, "selected list item text", w["selectionText"], w["selection"], 4.5)
        check(label, "text on hover row", w["textPrimary"], w["listHover"], 4.5)
        check(label, "error text", w["error"], w["sideBar"], 4.5)
        check(label, "warning text", w["warning"], w["sideBar"], 4.5)
        check(label, "success text", w["success"], w["sideBar"], 4.5)
        check(label, "terminal text", w["terminalForeground"], w["terminalBackground"], 4.5)
        check(label, "terminal cursor", w["terminalCursor"], w["terminalBackground"], 3.0)

    out = ROOT / "docs/CONTRAST.md"
    out.parent.mkdir(exist_ok=True)
    out.write_text(
        "# Theme contrast report (WCAG 2.1 AA)\n\n"
        "Generated by `scripts/check_contrast.py`. Do not edit by hand.\n\n"
        "| Theme | Element | Colors | Ratio | Required | Pass |\n|---|---|---|---|---|---|\n"
        + "\n".join(rows) + "\n"
    )
    print(f"{len(rows)} pairs checked, {failures} failing -> {out.relative_to(ROOT)}")
    for r in rows:
        if "❌" in r:
            print(r)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
