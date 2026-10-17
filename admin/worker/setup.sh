#!/usr/bin/env bash
#
# One-command setup for the AI Calculator usage dashboard (Cloudflare Worker + D1).
#
#   cd admin/worker
#   ./setup.sh                     # asks for the admin password, then deploys
#   ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='a long password' ./setup.sh
#
# It logs you in to Cloudflare (a browser opens once), creates the database if
# needed, writes its id into wrangler.toml, applies schema.sql, sets the admin
# token, deploys, and finally checks /health and prints the URL to paste into
# the repository secret TELEMETRY_URL.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

DB_NAME="m_calculator_stats"
ADMIN_EMAIL="${ADMIN_EMAIL:-rajkatrina90@gmail.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

say()  { printf '\033[1;35m▸\033[0m %s\n' "$1"; }
ok()   { printf '\033[1;32m✓\033[0m %s\n' "$1"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$1"; }
die()  { printf '\033[1;31m✗\033[0m %s\n' "$1" >&2; exit 1; }

command -v npx >/dev/null 2>&1 || die "Node/npm is required (npx not found)."
command -v python3 >/dev/null 2>&1 || die "python3 is required (used to edit wrangler.toml)."

# ---- 1. log in ---------------------------------------------------------------
say "Checking your Cloudflare login…"
if ! npx --yes wrangler@3 whoami >/dev/null 2>&1; then
  warn "Not logged in — a browser window will open for Cloudflare."
  npx --yes wrangler@3 login
fi
ok "Logged in as $(npx --yes wrangler@3 whoami 2>/dev/null | tail -1)"

# ---- 2. the database ---------------------------------------------------------
say "Looking for the $DB_NAME database…"
DB_ID="$(npx --yes wrangler@3 d1 list --json 2>/dev/null \
  | python3 -c "
import json,sys
try:
    rows = json.load(sys.stdin)
except Exception:
    rows = []
name = '$DB_NAME'
for row in rows:
    if row.get('name') == name:
        print(row.get('uuid') or row.get('id') or '')
        break
" || true)"

if [ -z "$DB_ID" ]; then
  say "Creating the $DB_NAME database…"
  CREATE_OUT="$(npx --yes wrangler@3 d1 create "$DB_NAME" 2>&1)"
  echo "$CREATE_OUT"
  DB_ID="$(printf '%s' "$CREATE_OUT" | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1)"
fi
[ -n "$DB_ID" ] || die "Could not work out the database id — run 'npx wrangler d1 create $DB_NAME' by hand and paste it into wrangler.toml."
ok "Database id: $DB_ID"

# ---- 3. wrangler.toml --------------------------------------------------------
say "Writing the database id into wrangler.toml…"
python3 - "$DB_ID" << 'PY'
import pathlib, re, sys
database_id = sys.argv[1]
path = pathlib.Path("wrangler.toml")
text = path.read_text()
text = re.sub(r'database_id\s*=\s*".*?"', f'database_id = "{database_id}"', text)
path.write_text(text)
PY
ok "wrangler.toml updated"

# ---- 4. schema ---------------------------------------------------------------
say "Applying schema.sql to the remote database…"
npx --yes wrangler@3 d1 execute "$DB_NAME" --remote --file=schema.sql >/dev/null
ok "Tables ready"

# ---- 5. the admin password ---------------------------------------------------
if [ -z "$ADMIN_PASSWORD" ]; then
  read -r -s -p "Choose the dashboard password (at least 8 characters): " ADMIN_PASSWORD
  echo
fi
[ "${#ADMIN_PASSWORD}" -ge 8 ] || die "Use at least 8 characters for the password."

say "Storing the sign-in as Worker secrets…"
PASSWORD_HASH="$(node "$HERE/hash-password.mjs" "$ADMIN_PASSWORD")"
printf '%s' "$ADMIN_EMAIL" | npx --yes wrangler@3 secret put ADMIN_EMAIL >/dev/null
printf '%s' "$PASSWORD_HASH" | npx --yes wrangler@3 secret put ADMIN_PASSWORD_HASH >/dev/null
ok "ADMIN_EMAIL and ADMIN_PASSWORD_HASH stored (the password itself is never stored)"

# ---- 6. deploy ---------------------------------------------------------------
say "Deploying the Worker…"
DEPLOY_OUT="$(npx --yes wrangler@3 deploy 2>&1)"
echo "$DEPLOY_OUT" | tail -6
WORKER_URL="$(printf '%s' "$DEPLOY_OUT" | grep -oE 'https://[a-z0-9.-]+\.workers\.dev' | head -1)"
[ -n "$WORKER_URL" ] || die "Deployed, but the URL could not be read — check the output above."

# ---- 7. verify ---------------------------------------------------------------
say "Checking /health…"
sleep 3
HEALTH="$(curl -s --max-time 20 "$WORKER_URL/health" || true)"
if printf '%s' "$HEALTH" | grep -q '"ok":true'; then
  ok "Worker is healthy"
else
  warn "Unexpected /health reply:"; echo "$HEALTH"
fi

cat << EOF

────────────────────────────────────────────────────────────────────────
Worker (the API):  $WORKER_URL
Health check:      $WORKER_URL/health
Account:           $ADMIN_EMAIL (password: the one you chose)

Two more things in GitHub:
  Settings → Secrets and variables → Actions → New repository secret
      TELEMETRY_URL = $WORKER_URL
  Settings → Pages → Build and deployment → Source: GitHub Actions
      then Actions → "Admin page (GitHub Pages)" → Run workflow
The dashboard appears at https://<your-github-name>.github.io/m.calculator/

Then run the Android CI again (or push any commit) and install the APK.
The dashboard will start filling in as people use the app.
────────────────────────────────────────────────────────────────────────
EOF
