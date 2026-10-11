# Admin panel: the usage dashboard

A read-only dashboard for AI Calculator: installs, active users, and calculations
and AI questions per day, with a per-day table and a chart.

The Worker also ships an **optional** `/v1/solve` endpoint that holds the Gemini
key server-side and checks a Google sign-in — the app does not call it (Ask AI
runs on the key the *user* pastes in), so leave those two secrets unset and the
Worker is purely the dashboard.

It runs entirely on Cloudflare — one Worker plus one D1 (SQLite) database, both
free-tier friendly, no other service to sign up for.

```
phone   ──POST /v1/events──▶  Worker  ──▶  D1 database
phone   ──POST /v1/solve───▶  Worker  ──▶  Gemini   (after checking the
                                          Google ID token the app sent)
browser ──GET /────────────▶  dashboard (asks for the admin token)
browser ──GET /v1/stats────▶  aggregated JSON
```

## What is collected

| Sent | Not sent |
| --- | --- |
| A random install id created on first launch | Anything about you or the device owner |
| The day, the app version, the Android API level | Expressions, results, history, files |
| Counts of: app opens, calculations, AI questions solved | Accounts, emails, the Google sign-in |

Nothing is sent while the switch in the app's drop-down menu
(*Anonymous usage stats*) is off, and turning it off also drops whatever was
still waiting to be uploaded.

## Set it up — one command

```bash
cd admin/worker
./setup.sh
```

That script logs you in to Cloudflare (a browser opens once), creates the D1
database, writes its id into `wrangler.toml`, applies `schema.sql`, asks for the
dashboard password, deploys the Worker, and prints the URL plus a `/health`
check. It is safe to re-run.

<details>
<summary>What it does, step by step (doing it by hand)</summary>

```bash
cd admin/worker
npx wrangler login                                          # once per machine
npx wrangler d1 create m_calculator_stats                   # prints database_id
#   → paste that id into wrangler.toml
npx wrangler d1 execute m_calculator_stats --remote --file=schema.sql
npx wrangler secret put ADMIN_TOKEN                         # the dashboard password
npx wrangler deploy
```

Optional, only for the server-side solver (the app does not call it — Ask AI
runs on the user's own key):

```bash
npx wrangler secret put GEMINI_API_KEY                      # aistudio.google.com key
npx wrangler secret put GOOGLE_WEB_CLIENT_ID                # the *Web* OAuth client ID
```
</details>

## After deploying

Open `https://<your-worker>.workers.dev/health` — it is a setup checklist that
never reveals a secret:

```json
{ "ok": true, "database": "ok",
  "secrets": { "ADMIN_TOKEN": true, "GEMINI_API_KEY": false, "GOOGLE_WEB_CLIENT_ID": false } }
```

Then switch on the app's counters with one GitHub secret
(Settings → Secrets and variables → Actions → New repository secret):

| Name | Value |
| --- | --- |
| `TELEMETRY_URL` | the worker URL, e.g. `https://m-calculator-stats.you.workers.dev` |

Push any commit (or re-run the Android CI workflow) and the next APK reports
counts. Until then the app keeps every counter on the phone.

## Keep it deployed automatically (optional)

`.github/workflows/worker.yml` redeploys the Worker whenever `admin/worker`
changes — as soon as these exist:

| Kind | Name | Where it comes from |
| --- | --- | --- |
| secret | `CLOUDFLARE_API_TOKEN` | Cloudflare → My Profile → API Tokens → *Edit Cloudflare Workers* |
| secret | `CLOUDFLARE_ACCOUNT_ID` | Cloudflare dashboard → Workers → Account ID |
| secret | `ADMIN_TOKEN` | the password you chose in `setup.sh` |
| variable | `D1_DATABASE_ID` | the id `setup.sh` wrote into `wrangler.toml` |

Without them the workflow simply prints what is missing and does nothing.

## Look at the dashboard without deploying

```bash
python3 admin/worker/preview.py       # http://localhost:8080
```

Serves the real dashboard page (straight from `worker.js`) with sample numbers,
so you can see exactly what deploys before signing up to anything.

## Local experiments

```bash
npx wrangler d1 execute m_calculator_stats --local --file=schema.sql
npx wrangler dev --local      # http://127.0.0.1:8787, ADMIN_TOKEN from a [vars] entry
curl -X POST http://127.0.0.1:8787/v1/events \
  -H 'content-type: application/json' \
  -d '{"id":"testinstall1234","day":"2026-10-11","v":"1.0.0","sdk":34,"events":{"calc":12,"app_open":2}}'
curl -s 'http://127.0.0.1:8787/health'
curl -s 'http://127.0.0.1:8787/v1/stats?days=7&token=localdev'
```

## Tests

```bash
./tools/run_worker_tests.sh      # from the repository root
```

Runs the Worker against a stub D1 — health, event ingest (including the
whitelist), the stats token check, the dashboard page and the solver's
missing-secret path. No Cloudflare account needed. The Android CI workflow runs
the same script on every push.

## API

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/v1/events` | `{id, day, v, sdk, events:{app_open,calc,ai}}` — only these three kinds are stored |
| `POST` | `/v1/solve` | Ask AI: needs `Authorization: Bearer <Google ID token>`; body `{model, question, attachment:{mimeType,data}}`; answers `{answer, by}` |
| `GET` | `/v1/stats?days=30&token=…` | totals, DAU/WAU/MAU, per-day series, installs per day, versions |
| `GET` | `/` | the dashboard |
| `GET` | `/health` | `{"ok":true}` |

Swapping D1 for Supabase or Firebase later only means changing the two places
that touch `env.DB` in `worker.js` — the endpoints and the dashboard stay as they
are.

## How the sign-in check works

`POST /v1/solve` expects the Google ID token the app got from Credential Manager.
The Worker checks the JWT itself, with WebCrypto and no dependencies: it fetches
Google's published keys from `https://www.googleapis.com/oauth2/v3/certs` (cached
for 30 minutes), verifies the RS256 signature, and checks `aud` against
`GOOGLE_WEB_CLIENT_ID`, `iss` against `accounts.google.com` and `exp` against the
clock. Only then does it call Gemini with `env.GEMINI_API_KEY`.

* without `GOOGLE_WEB_CLIENT_ID` on the Worker, `/v1/solve` answers anonymously
  (handy while trying it out, but anyone with the URL could use your key);
* the app needs the same Web client ID to show the account picker, plus
  `AI_PROXY_URL` pointing here.
