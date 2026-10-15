"""Catches the Kotlin mistakes that only show up at compile time in CI.

Run from the repository root:  python3 tools/check_kotlin_patterns.py

Checks every .kt file under app/src for:
  * unbalanced (), {} or [] over the whole file (comments/strings stripped)
  * redundant Num(...) wrappers around values that already are Num
  * an `if` without `else` used as an expression inside a string template
  * Java-style Double.isNaN / .size() instead of Kotlin equivalents
  * a Material icon used without the import that brings it in (a compile error
    that costs a ten-minute CI round otherwise)
  * `painterResource(R.mipmap.ic_launcher)`, which compiles and then throws at
    runtime on any phone running Android 8 or newer (the id resolves to the
    adaptive-icon XML, and Compose only loads vectors and raster assets)
"""
import pathlib
import re
import sys


def strip_code(text: str) -> str:
    # Strings first: a `//` inside a string literal (a URL, a snippet) must not
    # be mistaken for the start of a comment.
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\\n])'", "''", text)
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


BAD_PATTERNS = {
    r"painterResource\(\s*R\.mipmap\.": (
        "adaptive icons cannot be loaded by painterResource — use a raster "
        "drawable such as R.drawable.app_icon"
    ),
    r"\bNum\(\s*(ComplexMath|outAngle|fromAngle|reciprocal)\b": "redundant Num() wrapper",
    r"Double\.isNaN": "use Kotlin's x.isNaN()",
    r"\.size\(\)": "use Kotlin's x.size",
}


def main() -> int:
    problems = 0
    for path in sorted(pathlib.Path("app/src").rglob("*.kt")):
        raw = path.read_text()
        code = strip_code(raw)

        for open_c, close_c in (("{", "}"), ("(", ")"), ("[", "]")):
            diff = code.count(open_c) - code.count(close_c)
            if diff:
                print(f"UNBALANCED {path}: {open_c}{close_c} differ by {diff}")
                problems += 1

        for number, line in enumerate(raw.splitlines(), 1):
            for pattern, why in BAD_PATTERNS.items():
                if re.search(pattern, line):
                    print(f"PATTERN {path}:{number}: {why} -> {line.strip()[:100]}")
                    problems += 1
            if "${if (" in line and " else " not in line:
                print(f"TEMPLATE {path}:{number}: if without else in a template")
                problems += 1

        # `Icons.Filled.Tune` needs `import ...material.icons.filled.Tune`; without
        # it Kotlin says "Unresolved reference 'Tune'" only in CI.
        imports = set(re.findall(r"^import (\S+)$", raw, re.M))
        for match in re.finditer(
            r"Icons\.(AutoMirrored\.)?(Filled|Outlined|Rounded|Sharp|TwoTone)?\.?(\w+)", raw
        ):
            mirrored, style, name = match.group(1), match.group(2) or "Filled", match.group(3)
            package = "androidx.compose.material.icons."
            wanted = (
                package + "automirrored." + style.lower() + "." + name
                if mirrored
                else package + style.lower() + "." + name
            )
            if wanted not in imports:
                hint = "import " + wanted
                print(f"ICON {path}: {match.group(0)} without '{hint}'")
                problems += 1

        # `var x by remember { ... }` only compiles with the delegation operators
        # imported, and a "no unused imports" cleanup happily deletes them.
        if re.search(r"\bby\s+(remember|mutableStateOf|viewModel)", raw):
            for required in ("import androidx.compose.runtime.getValue",
                             "import androidx.compose.runtime.setValue"):
                if required not in raw:
                    print(f"DELEGATE {path}: missing '{required}' for a `by` delegate")
                    problems += 1

    if problems:
        print(f"\n{problems} problem(s) found")
        return 1
    print("no pattern problems found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
