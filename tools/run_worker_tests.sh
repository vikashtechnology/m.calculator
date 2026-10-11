#!/usr/bin/env bash
# Builds the Worker into a testable module and runs the stub-D1 checks.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

python3 - << 'PY'
import pathlib
worker = pathlib.Path("admin/worker/worker.js").read_text()
start = worker.index("const DASHBOARD_HTML = `") + len("const DASHBOARD_HTML = `")
end = worker.rindex("`;")
module = worker[:start - len("const DASHBOARD_HTML = `")] + "const DASHBOARD_HTML = '<html>ok</html>';\n" + worker[end + 2:]
pathlib.Path("admin/worker/test/module-under-test.mjs").write_text(module)
PY

node admin/worker/test/worker.test.mjs
