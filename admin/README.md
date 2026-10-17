# Admin panel: the usage dashboard

A read-only dashboard for AI Calculator: installs, active users, and calculations
and AI questions per day, with a per-day table and a chart.

The dashboard also edits the text of the app's **About page**: `GET /v1/about`
is public (the app fetches it and keeps a copy for offline), `PUT /v1/about`
needs a signed-in admin session. See WEB_SETUP.md.

The dashboard itself is a static page in `admin/web`, published by GitHub Pages.
It signs in to the Worker (`POST /v1/login`) and carries the session in an
`Authorization` header; the Worker serves no HTML for it. That keeps the whole
admin in one place, on a free host, and the Worker down to an API.

The Worker also ships an **optional** `/v1/solve` endpoint that holds the AI
key server-side and checks a Google sign-in — the app does not call it (Ask AI
runs on the key the *user* pastes in), so leave those two secrets unset and the
Worker is purely the dashboard.

The data side runs entirely on Cloudflare — one Worker plus one D1 (SQLite)
database, both free-tier friendly. The dashboard page is four static files on
GitHub Pages, which is free for public repositories.

```
phone   ──POST /v1/events──▶  Worker  ──▶  D1 database
phone   ──POST /v1/solve───▶  Worker  ──▶  Gemini   (after checking the
                                          Google ID token the app sent)
browser ──GET /v1/stats────▶  Worker  ──▶  D1 database   (needs the session)
github  ──admin/web────────▶  the dashboard page itself, hosted by Pages
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

## Two ways to set it up

**In a browser, no terminal** — follow [`WEB_SETUP.md`](WEB_SETUP.md). You create
the Worker and the database in the Cloudflare dashboard, paste `worker.js`, set
the `ADMIN_PASSWORD_HASH` secret (made with `hash-password.mjs`), open
`…/health` — which creates the tables, so there is no SQL to run by hand — and
turn on GitHub Pages so the dashboard page has somewhere to live. The Worker
carries its own schema for exactly that reason (`SCHEMA_SQL`, kept in step with
`schema.sql` by the test runner).

**From a terminal, one command** — everything below, automated.

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
node hash-password.mjs 'your password'                      # → paste as ADMIN_PASSWORD_HASH
npx wrangler secret put ADMIN_PASSWORD_HASH                 # the dashboard password hash
npx wrangler secret put ADMIN_EMAIL                         # optional, if not the default
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
  "signIn": { "method": "email and password; the session goes in an Authorization header",
              "email": "rajkatrina90@gmail.com", "customPassword": true, "customSessionKey": false },
  "secrets": { "GEMINI_API_KEY": false, "GOOGLE_WEB_CLIENT_ID": false } }
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
| secret | `ADMIN_EMAIL` | the address that signs in (optional; default `rajkatrina90@gmail.com`) |
| secret | `ADMIN_PASSWORD_HASH` | `node admin/worker/hash-password.mjs '…'` |
| variable | `D1_DATABASE_ID` | the id `setup.sh` wrote into `wrangler.toml` |

Without them the workflow simply prints what is missing and does nothing.

## The dashboard on GitHub Pages

`admin/web` **is** the dashboard: `index.html`, `app.js`, `style.css` and
`config.js`, served by GitHub Pages. It holds no data — it signs in to the
Worker named in `config.js` and reads everything from there. The Worker serves
no HTML for it and has no pages of its own.

```bash
python3 tools/check_admin_web.py   # element ids, file references, syntax
```

`.github/workflows/pages.yml` publishes it on every push to `main` that touches
`admin/web`. Once, in the repository: **Settings → Pages → Source: GitHub
Actions**. Private repositories need a paid plan for Pages.

The sign-in: the page calls `POST /v1/login` with the email and password, gets
back a signed session, and sends it as `Authorization: Bearer …` on every admin
call. There is no cookie and no token in the address — `github.io` and
`workers.dev` are different sites, and a cookie across them is a third-party
cookie that browsers drop. The password is only ever checked by the Worker, and
the session lives in `sessionStorage` (gone when the tab closes).

## Local experiments

```bash
npx wrangler d1 execute m_calculator_stats --local --file=schema.sql
npx wrangler dev --local      # http://127.0.0.1:8787, ADMIN_EMAIL / ADMIN_PASSWORD_HASH from a [vars] entry
curl -X POST http://127.0.0.1:8787/v1/events \
  -H 'content-type: application/json' \
  -d '{"id":"testinstall1234","day":"2026-10-11","v":"1.0.0","sdk":34,"events":{"calc":12,"app_open":2}}'
curl -s 'http://127.0.0.1:8787/health'

# sign in, keep the session, and ask for the numbers
TOKEN=$(curl -s -X POST http://127.0.0.1:8787/v1/login \
  -H 'content-type: application/json' \
  -d '{"email":"rajkatrina90@gmail.com","password":"admin123"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s -H "authorization: Bearer $TOKEN" 'http://127.0.0.1:8787/v1/stats?days=7'
```

The dashboard is the GitHub Pages page (`admin/web`); the Worker only answers
its API. Opening the Worker's own `/` forwards there.

## Privacy policy page

`GET /privacy` serves the policy the Play listing links to (an app that sends
anything off the device needs a reachable URL). It covers the three things that
can leave the phone: the anonymous usage counts, the AdMob banner's advertising
ID, and Ask AI. Set `CONTACT` at the top of `worker.js` to your support email
before publishing, then redeploy.

## Tests

```bash
./tools/run_worker_tests.sh      # from the repository root
```

Runs the Worker against a stub D1 — health, event ingest (including the
whitelist), signing in with a right and a wrong password, the rate limit, the
guards on stats and About, forged sessions, the forward from `/`, the privacy
page and the solver's missing-secret path — then the static checks on
`admin/web`. No Cloudflare account needed. The Android CI workflow runs the same
script on every push.

## API

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/v1/events` | `{id, day, v, sdk, events:{app_open,calc,ai}}` — only these three kinds are stored |
| `POST` | `/v1/solve` | Ask AI: needs `Authorization: Bearer <Google ID token>`; body `{model, question, attachment:{mimeType,data}}`; answers `{answer, by}` |
| `POST` | `/v1/login` | `{email, password}` → `{email, token, expiresAt, days}`; the dashboard signs in here |
| `GET` | `/v1/stats?days=30` | totals, DAU/WAU/MAU, per-day series, installs per day, versions — needs `Authorization: Bearer …` |
| `GET` | `/v1/about` | the About text the app shows (public) |
| `PUT` | `/v1/about` | saves it — needs `Authorization: Bearer …` |
| `GET` | `/privacy` | the policy page for the Play listing |
| `GET` | `/` | forwards to the dashboard on GitHub Pages |
| `GET` | `/health` | `{"ok":true}` |

Swapping D1 for Supabase or Firebase later only means changing the places that
touch `env.DB` in `worker.js` — the endpoints and the dashboard stay as they
are.

## How the sign-in check works

`POST /v1/solve` expects the Google ID token the app got from Credential Manager.
The Worker checks the JWT itself, with WebCrypto and no dependencies: it fetches
Google's published keys from `https://www.googleapis.com/oauth2/v3/certs` (cached
for 30 minutes), verifies the RS256 signature, and checks `aud` against
`GOOGLE_WEB_CLIENT_ID`, `iss` against `accounts.google.com` and `exp` against the
clock. Only then does it call the model with `env.GEMINI_API_KEY`.

> The app itself does not use this endpoint: Ask AI talks to the AI service
directly with the key each user pasted in. This proxy is here for anyone who
wants to hold the key server-side instead.

* without `GOOGLE_WEB_CLIENT_ID` on the Worker, `/v1/solve` answers anonymously
  (handy while trying it out, but anyone with the URL could use your key);
* the app needs the same Web client ID to show the account picker, plus
  `AI_PROXY_URL` pointing here.
