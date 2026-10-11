"""Catches the Kotlin mistakes that only show up at compile time in CI.

Run from the repository root:  python3 tools/check_kotlin_patterns.py

Checks every .kt file under app/src for:
  * unbalanced (), {} or [] over the whole file (comments/strings stripped)
  * redundant Num(...) wrappers around values that already are Num
  * an `if` without `else` used as an expression inside a string template
  * Java-style Double.isNaN / .size() instead of Kotlin equivalents
"""
import pathlib
import re
import sys


def strip_code(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\\n])'", "''", text)
    return text


BAD_PATTERNS = {
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
