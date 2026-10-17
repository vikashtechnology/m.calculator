#!/usr/bin/env bash
# Builds the Worker into a testable module, compares its embedded schema with
# schema.sql, and runs the stub-D1 checks.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

python3 - << 'PY'
import pathlib
import re
import sys

worker_path = pathlib.Path("admin/worker/worker.js")
worker = worker_path.read_text()

# 1. module under test: the Worker with the two big HTML templates replaced by
#    markers, so the test file stays about behaviour rather than markup.
def stub(module, name, marker):
    start = module.index(f"const {name} = `") + len(f"const {name} = `")
    end = module.index("`;", start)
    return module[:start - len(f"const {name} = `")] + f"const {name} = '{marker}';\n" + module[end + 2:]

module = stub(worker, "DASHBOARD_HTML", "<html>ok</html>")
module = stub(module, "PRIVACY_HTML", "<html>privacy</html>")
pathlib.Path("admin/worker/test/module-under-test.mjs").write_text(module)


# 2. the schema the Worker creates must match schema.sql, or the two deployments
#    (wrangler and dashboard) would drift apart.
def normalise(statements):
    return [
        re.sub(r"\s+", " ", s).strip().rstrip(";").lower()
        for s in statements
        if s.strip()
    ]


match = re.search(r"const SCHEMA_SQL = \[(.*?)\n\];", worker, re.S)
if not match:
    sys.exit("could not find SCHEMA_SQL in admin/worker/worker.js")
in_code = re.findall(r"`(.*?)`", match.group(1), re.S)

schema_file = pathlib.Path("admin/worker/schema.sql").read_text()
without_comments = "\n".join(
    re.sub(r"--.*$", "", line) for line in schema_file.splitlines()
)
in_file = without_comments.split(";")

a, b = normalise(in_code), normalise(in_file)
if a != b:
    print("SCHEMA MISMATCH between admin/worker/worker.js and admin/worker/schema.sql")
    for i in range(max(len(a), len(b))):
        left = a[i] if i < len(a) else "<missing>"
        right = b[i] if i < len(b) else "<missing>"
        if left != right:
            print(f"  #{i + 1}\n    worker.js: {left}\n    schema.sql: {right}")
    sys.exit(1)
print(f"schema: worker.js and schema.sql agree ({len(a)} statements)")
PY

node admin/worker/test/worker.test.mjs
