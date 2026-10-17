#!/usr/bin/env python3
"""Checks the GitHub Pages copy of the admin dashboard (admin/web).

It is plain HTML, CSS and JavaScript with no build step, so there is no compiler
to catch a typo. These checks stand in for one:

  * every element app.js reaches for exists in index.html;
  * every file the page asks the browser to load exists;
  * config.js points at a plain https address with no trailing slash;
  * neither file throws when parsed.
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent.parent
WEB = HERE / "admin" / "web"

INDEX = WEB / "index.html"
APP = WEB / "app.js"
CONFIG = WEB / "config.js"
STYLE = WEB / "style.css"

problems: list[str] = []


def read(path: pathlib.Path) -> str:
    if not path.exists():
        problems.append(f"{path.relative_to(HERE)} is missing")
        return ""
    return path.read_text(encoding="utf-8")


index = read(INDEX)
app = read(APP)
config = read(CONFIG)
style = read(STYLE)

if not problems:
    # 1. the ids app.js asks for must exist in the page
    ids = set(re.findall(r'\bid="([^"]+)"', index))
    wanted = set(re.findall(r'\bel\("([^"]+)"\)', app))
    wanted |= set(re.findall(r'getElementById\("([^"]+)"\)', app))
    for name in sorted(wanted - ids):
        problems.append(f'app.js uses #{name}, which index.html does not have')
    for name in sorted(ids - wanted):
        # not an error, just noise worth knowing about — except for ids the
        # browser needs (anchors, labels); keep it quiet.
        pass

    # 2. the files the page loads must exist
    for src in re.findall(r'<script[^>]*src="([^"]+)"', index):
        if src.startswith(("http://", "https://", "//")):
            problems.append(f"index.html loads a script from {src}; keep it self-contained")
        elif not (WEB / src).exists():
            problems.append(f"index.html loads {src}, which does not exist")
    for href in re.findall(r'<link[^>]*href="([^"]+)"', index):
        if href.startswith(("http://", "https://", "//")):
            problems.append(f"index.html loads {href}; keep it self-contained")
        elif not (WEB / href).exists():
            problems.append(f"index.html loads {href}, which does not exist")

    # 3. the page must be told where the Worker is
    match = re.search(r'apiBase:\s*"([^"]*)"', config)
    if not match:
        problems.append("config.js does not set apiBase")
    else:
        base = match.group(1)
        if not base.startswith("https://"):
            problems.append(f"apiBase should be an https address, not {base!r}")
        if base.endswith("/"):
            problems.append("apiBase should not end with a slash (app.js adds them)")

    # 4. a page that runs no inline script keeps its Content-Security-Policy simple
    if re.search(r'<script(?![^>]*\bsrc=)[^>]*>', index):
        problems.append("index.html has an inline <script>; put the code in app.js")

    # 5. syntax
    for path, name in ((APP, "app.js"), (CONFIG, "config.js")):
        result = subprocess.run(
            ["node", "--check", str(path)], capture_output=True, text=True
        )
        if result.returncode != 0:
            problems.append(f"{name} does not parse:\n{result.stderr.strip()}")

if problems:
    print("admin web: PROBLEMS")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)

lookups = len(re.findall(r'\bel\("([^"]+)"\)', app))
print(
    "admin web: index.html, style.css, app.js and config.js agree "
    f"({lookups} element lookups, all present)"
)
