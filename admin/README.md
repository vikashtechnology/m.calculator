# Usage dashboard (admin panel)

A read-only dashboard for M Calculator: how many people installed the app, how
many are active, and how many calculations and AI questions were solved per day.

It runs entirely on Cloudflare — one Worker plus one D1 (SQLite) database, both
free-tier friendly, no other service to sign up for.

```
phone ──POST /v1/events──▶  Worker  ──▶  D1 database
browser ──GET /──────────▶  dashboard (asks for the admin token)
browser ──GET /v1/stats──▶  aggregated JSON
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

## Deploy it (about five minutes)

```bash
cd admin/worker
npx wrangler login                                        # once per machine
npx wrangler d1 create m_calculator_stats                 # prints a database_id
#   → paste that id into wrangler.toml
npx wrangler d1 execute m_calculator_stats --remote --file=schema.sql
npx wrangler secret put ADMIN_TOKEN                       # pick a long password
npx wrangler deploy
```

`wrangler deploy` prints the worker URL, e.g.
`https://m-calculator-stats.<your-name>.workers.dev`. Open it in a browser, paste
the admin token and the dashboard appears.

Point the app at the same URL, either

* add a repository secret `TELEMETRY_URL` with that URL (CI passes it as
  `-PtelemetryUrl=…`), or
* put `telemetryUrl=https://…` in `gradle.properties` (or pass
  `-PtelemetryUrl=https://…` on the command line).

Builds without it simply never report anything.

## Local experiments

```bash
npx wrangler d1 execute m_calculator_stats --local --file=schema.sql
npx wrangler dev                     # http://127.0.0.1:8787
curl -X POST http://127.0.0.1:8787/v1/events \
  -H 'content-type: application/json' \
  -d '{"id":"testinstall1234","day":"2026-10-11","v":"1.0.0","sdk":34,"events":{"calc":12,"app_open":2}}'
```

## API

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/v1/events` | `{id, day, v, sdk, events:{app_open,calc,ai}}` — only these three kinds are stored |
| `GET` | `/v1/stats?days=30&token=…` | totals, DAU/WAU/MAU, per-day series, installs per day, versions |
| `GET` | `/` | the dashboard |
| `GET` | `/health` | `{"ok":true}` |

Swapping D1 for Supabase or Firebase later only means changing the two places
that touch `env.DB` in `worker.js` — the endpoints and the dashboard stay as they
are.
