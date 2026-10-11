#!/usr/bin/env python3
"""Keeps the app readable in both themes.

    python3 tools/check_theme.py

Three checks, all of which have caught real bugs:

1. **No colours outside the theme.** Every `Color(0x…)` and every named Compose
   colour must live in `ui/theme/Theme.kt`; anywhere else it cannot follow the
   light/dark switch. (`Color.Transparent` is allowed — it has no hue.)
2. **Both palettes are complete.** The light and dark schemes must define the
   same roles, so no screen silently falls back to Material's baseline purple.
3. **Contrast.** Each (text, background) pair the screens actually use is
   measured with the WCAG formula and must clear its limit — 4.5:1 for body
   text, 3:1 for strokes and large display type.

Exit code 0 when everything passes, 1 with a list of what to fix otherwise.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME = ROOT / "app/src/main/java/com/advanceai/calculator/ui/theme/Theme.kt"
SOURCES = ROOT / "app/src/main/java"

BODY = 4.5     # body text
LARGE = 3.0    # display numbers, key symbols, chart strokes

# (foreground, background, minimum) — one row per way the app pairs colours.
PAIRS = [
    ("onBackground", "background", BODY),
    ("onSurface", "surface", BODY),
    ("onSurface", "surfaceContainerLowest", BODY),
    ("onSurface", "surfaceContainerLow", BODY),
    ("onSurface", "surfaceContainer", BODY),
    ("onSurface", "surfaceContainerHigh", BODY),
    ("onSurface", "surfaceContainerHighest", BODY),
    ("onSurfaceVariant", "surface", BODY),
    ("onSurfaceVariant", "surfaceContainerLow", BODY),
    ("onSurfaceVariant", "surfaceContainer", BODY),
    ("onSurfaceVariant", "surfaceContainerHigh", BODY),
    ("onSurfaceVariant", "surfaceContainerHighest", BODY),
    ("onSurfaceVariant", "surfaceVariant", BODY),
    ("primary", "surface", LARGE),
    ("primary", "surfaceContainer", LARGE),
    ("primary", "surfaceContainerHigh", LARGE),
    ("onPrimary", "primary", BODY),
    ("onPrimaryContainer", "primaryContainer", BODY),
    ("onPrimaryContainer", "surfaceContainerHigh", BODY),
    ("secondary", "surface", BODY),
    ("secondary", "surfaceContainer", BODY),
    ("secondary", "surfaceContainerHigh", BODY),
    ("onSecondary", "secondary", BODY),
    ("onSecondaryContainer", "secondaryContainer", BODY),
    ("tertiary", "surfaceContainer", BODY),
    ("onTertiary", "tertiary", BODY),
    ("onTertiaryContainer", "tertiaryContainer", BODY),
    ("onError", "error", BODY),
    ("onErrorContainer", "errorContainer", BODY),
    ("error", "surfaceContainerHigh", BODY),
    ("inverseOnSurface", "inverseSurface", BODY),
    ("inversePrimary", "inverseSurface", BODY),
    ("outline", "surface", 1.2),          # divider / border: decorative
    ("outlineVariant", "surface", 1.05),  # hairline: decorative
]

COLOR_CALL = re.compile(r"Color\(\s*0x([0-9A-Fa-f]{8})\s*\)")
NAMED_COLOR = re.compile(
    r"\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|Grey|LightGray|DarkGray)\b"
)
ROLE = re.compile(r"^\s{4}(\w+)\s*=\s*Color\(\s*0xFF([0-9A-Fa-f]{6})\s*\)", re.M)

REQUIRED = {
    r for name in (
        "primary onPrimary primaryContainer onPrimaryContainer inversePrimary "
        "secondary onSecondary secondaryContainer onSecondaryContainer "
        "tertiary onTertiary tertiaryContainer onTertiaryContainer "
        "background onBackground surface onSurface surfaceVariant onSurfaceVariant "
        "surfaceTint inverseSurface inverseOnSurface error onError errorContainer "
        "onErrorContainer outline outlineVariant scrim surfaceDim surfaceBright "
        "surfaceContainerLowest surfaceContainerLow surfaceContainer "
        "surfaceContainerHigh surfaceContainerHighest"
    ).split()
    for r in [name]
}


def relative_luminance(hex6: str) -> float:
    values = [int(hex6[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in values]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(a: str, b: str) -> float:
    la, lb = relative_luminance(a), relative_luminance(b)
    lighter, darker = max(la, lb), min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)


def parse_schemes() -> dict[str, dict[str, str]]:
    text = THEME.read_text()
    schemes = {}
    for name in ("DarkColors", "LightColors"):
        match = re.search(rf"{name}\s*=\s*\w+ColorScheme\((.*?)\n\)", text, re.S)
        if not match:
            sys.exit(f"could not find {name} in {THEME.relative_to(ROOT)}")
        schemes[name] = {role: hex6 for role, hex6 in ROLE.findall(match.group(1))}
    return schemes


def check_no_stray_colours() -> list[str]:
    problems = []
    for path in sorted(SOURCES.rglob("*.kt")):
        if path == THEME:
            continue
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if COLOR_CALL.search(line) or NAMED_COLOR.search(line):
                if "Color.Transparent" in line and not COLOR_CALL.search(line):
                    continue
                problems.append(
                    f"  {path.relative_to(SOURCES)}:{number}: {line.strip()[:90]}"
                )
    return problems


def check_schemes(schemes: dict[str, dict[str, str]]) -> list[str]:
    problems = []
    dark, light = schemes["DarkColors"], schemes["LightColors"]
    for name, roles in schemes.items():
        missing = sorted(REQUIRED - set(roles))
        if missing:
            problems.append(f"  {name} is missing: {', '.join(missing)}")
    for role in sorted(set(dark) ^ set(light)):
        problems.append(f"  {role} is defined for only one theme")
    return problems


def chart_colours() -> dict[str, list[str]]:
    text = THEME.read_text()
    out = {}
    for name in ("DarkAppColors", "LightAppColors"):
        match = re.search(rf"{name}\s*=\s*AppColors\((.*?)\n\)", text, re.S)
        if not match:
            sys.exit(f"could not find {name}")
        out[name] = re.findall(r"Color\(\s*0xFF([0-9A-Fa-f]{6})\s*\)", match.group(1))
    return out


def main() -> int:
    problems: list[str] = []

    stray = check_no_stray_colours()
    if stray:
        problems.append("colours outside ui/theme/Theme.kt (they cannot follow the theme):")
        problems += stray

    schemes = parse_schemes()
    problems += check_schemes(schemes)

    print("Contrast — text and strokes against the surface they are drawn on\n")
    failures = 0
    for theme_name, roles in schemes.items():
        chart = chart_colours()[theme_name.replace("Colors", "AppColors")]
        rows = [(fg, bg, minimum, roles) for fg, bg, minimum in PAIRS]
        rows += [
            (f"chart[{i}]", "surface", LARGE, {f"chart[{i}]": hex6, "surface": roles["surface"]})
            for i, hex6 in enumerate(chart)
        ]
        print(f"  {theme_name}")
        for fg, bg, minimum, role_map in rows:
            if fg not in role_map or bg not in role_map:
                continue
            ratio = contrast(role_map[fg], role_map[bg])
            status = "ok  " if ratio >= minimum else "FAIL"
            if ratio < minimum:
                failures += 1
                problems.append(
                    f"  {theme_name}: {fg} on {bg} is {ratio:.2f}:1, needs {minimum}:1"
                )
            print(f"    {status} {ratio:5.2f}:1  (min {minimum})  {fg} on {bg}")
        print()

    if problems:
        print("Problems to fix\n")
        for line in problems:
            print(line)
        print(f"\n{len(problems)} problem(s), {failures} of them contrast failures.")
        return 1

    total = sum(len(v) for v in schemes.values())
    print(f"All good: {total} colour roles, every usage pair readable, no stray colours.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
