# Admin panel — set up from the Cloudflare dashboard (no terminal)

Everything below happens in a web browser. About five minutes, and the free tier
covers it: one Worker and one D1 database, both free, no card needed.

The Worker *brings its own tables*, so there is no SQL to run by hand: opening
the health URL at the end creates them. That is the step that makes this whole
setup work without a command line.

---

## 1. Create the database

1. Sign in at <https://dash.cloudflare.com>.
2. In the left sidebar open **Storage & Databases → D1** (in some layouts it is
   **Workers & Pages → D1**).
3. Select **Create database** (or **Create** → **D1**).
4. Name it exactly **`m_calculator_stats`** and select **Create**.
5. Leave the page open — you will pick this database from a dropdown in step 3.

Do **not** run anything in the Console tab; the Worker creates the tables itself.

## 2. Create the Worker

1. Go to **Workers & Pages** (sidebar) → **Create application**
   (some layouts: **Create** → **Workers** → **Create Worker**).
2. Choose **Start with Hello World!** → **Get started**.
3. Name it **`ai-calculator-stats`** and select **Deploy**.
   The name becomes the URL: `https://ai-calculator-stats.<your-subdomain>.workers.dev`.
   If Cloudflare asks you to choose a `workers.dev` subdomain, pick anything you
   like — it is shown in every URL afterwards.
4. Leave the page open on the Worker's **Overview**.

## 3. Bind the database to the Worker

1. On the Worker's page open **Settings → Bindings** (some layouts:
   **Bindings** at the top).
2. Select **Add binding** → **D1 database** → **Add binding**.
3. **Variable name: `DB`** — three letters, exactly as written. The code looks
   for `env.DB`, and any other name will leave the Worker without a database.
4. **D1 database:** choose `m_calculator_stats`.
5. Select **Add binding** (or **Deploy**/**Save** if that is what the page shows).

## 4. Paste the Worker code

1. Open this file on GitHub and copy **all** of it:

   <https://github.com/vikashtechnology/m.calculator/blob/arena/06e942f0-m-calculator/admin/worker/worker.js>

   Use **Copy raw file** (the icon beside the *Raw* button), or open the *Raw*
   view and select everything with `Ctrl+A` / `Cmd+A`, then copy.
2. Back in the Cloudflare dashboard, on the Worker's page select **Edit code**
   (or **Edit**). The editor opens with the Hello World sample in `worker.js`.
3. Select everything in the editor (`Ctrl+A` / `Cmd+A`) and paste the copied
   code over it. The line `export default {` should be near the top — that is
   how you know the whole file arrived.
4. Select **Deploy** (the editor may call it **Save and Deploy**).

## 5. Set the dashboard password

1. On the Worker's page open **Settings → Variables and Secrets**
   (some layouts: **Variables**).
2. Select **Add** (or **Add variable**).
3. **Type: Secret** (important — a plain text variable is readable by anyone
   with access to the account).
4. **Name: `ADMIN_TOKEN`** — exactly.
5. **Value:** the password you want for the dashboard, 12 characters or more.
   Save it in your password manager; it is never shown again.
6. Select **Save** / **Deploy** so the Worker runs with the new secret.

## 6. Check it, then open the dashboard

1. Open `https://ai-calculator-stats.<your-subdomain>.workers.dev/health`
   (use your Worker's own URL).

   This is the setup checklist, and it also creates the tables. You want:

   ```json
   {
     "ok": true,
     "service": "AI Calculator stats",
     "database": "ok",
     "schema": "ready",
     "secrets": { "ADMIN_TOKEN": true, "GEMINI_API_KEY": false, "GOOGLE_WEB_CLIENT_ID": false }
   }
   ```

   * `"ok": false` with `"database": "error: … Cannot read properties of undefined"` → the
     binding is missing or misnamed: it must be the variable name **DB** (step 3).
   * `"ADMIN_TOKEN": false` → the secret is not set on the deployed version yet
     (step 5); re-deploy afterwards.

2. Open the Worker's root URL: `https://ai-calculator-stats.<your-subdomain>.workers.dev/`
3. Type your admin token into the box and select **Load**. The page remembers it
   in this browser only, and shows installs, active users, and calculations and
   AI questions per day. Until the app reports, everything reads 0.

**Nothing else is needed for the dashboard itself.** Steps 7 and 8 are what make
the *app* send numbers.

### Editing the app's About page

The same dashboard edits the page that **☰ → About AI Calculator** shows in the
app:

1. Scroll to the panel headed **About page (shown inside the app)**.
2. Type the title, the tagline, the paragraphs (one per line), any links (one per
   line as `Label | https://address`) and a support email.
3. Select **Save the About page** — the token box at the top of the page is the
   one it uses; if you have not entered it yet, enter it there first.
4. In the app: **☰ → About AI Calculator → Refresh**. The new text appears. The
   app also keeps the last copy it downloaded, so the page reads correctly with
   no connection.

Notes:

* Nothing needs creating in the database: the Worker makes the `about` table the
  first time it needs it (the same `CREATE TABLE IF NOT EXISTS` list as the
  counters). If you deploy with `wrangler` instead, `schema.sql` creates it too.
* Only `http` and `https` links are accepted, at most 8 of them, and the text is
  trimmed to sensible lengths — the page inside the app cannot be made to do
  anything odd from here.
* Leaving the boxes empty restores the built-in wording.
* `GET /v1/about` is public (the app reads it without a token); saving needs the
  admin token, exactly like the stats.

### If the dashboard will not let you in

* **It keeps asking for the token.** The token is remembered in the browser you
  typed it into and nowhere else, so a new phone, a different browser or cleared
  site data means typing it again.
* **It says "That token was not accepted."** Either it is not the token this
  Worker was given, or the `ADMIN_TOKEN` secret was changed afterwards.
  Cloudflare never shows a secret back, so the way forward is to set a new one:
  **Workers & Pages → your Worker → Settings → Variables and Secrets →
  `ADMIN_TOKEN` → Edit**, save a new value, then type that value in. `/health`
  is worth a look as well — it reports `"ADMIN_TOKEN": true` when the secret is
  in place.
* **You wanted to change the Worker code but the editor is read-only.** That
  means this Worker was last deployed from a terminal or from the GitHub Action
  rather than pasted in by hand; Cloudflare does not allow hand-editing those.
  Use the same route that deployed it (Actions → **Dashboard worker** → *Run
  workflow*), which deploys `admin/worker/worker.js` from the repository.
* **Which version is live?** `GET /health` lists the endpoints the deployed
  Worker knows. If `/v1/about` is not in that list, the deployed copy is older
  than the About editor — paste `worker.js` again (step 4) or run the action.

## 7. Point the app at it (GitHub, one field)

1. Open <https://github.com/vikashtechnology/m.calculator/settings/secrets/actions>
2. **New repository secret**
   * **Name:** `TELEMETRY_URL`
   * **Secret:** your Worker URL, `https://ai-calculator-stats.<your-subdomain>.workers.dev`
     — no trailing slash, no `/health`.
3. **Add secret**

While you are on that page, remove or leave empty anything you do not want: with
no `TELEMETRY_URL`, the app simply keeps its counters on the phone.

## 8. Get an APK that reports

1. Open <https://github.com/vikashtechnology/m.calculator/actions/workflows/android.yml>
2. Select **Run workflow** → **Run workflow** (branch: `main`).
3. When the run finishes, the fresh APK is on
   <https://github.com/vikashtechnology/m.calculator/releases/tag/latest-build>
4. Install it, open the app, and make sure **☰ → Anonymous usage stats** is on.
   Use the calculator a little, then reload the dashboard: the numbers appear
   within a minute (the app uploads in the background).

That is the whole loop. What is sent is only counts — app opens, calculations
and AI questions, the day, the app version and the Android API level, under a
random install id created on the phone. No expressions, results, files or
accounts ever leave the device.

---

## Optional: let GitHub deploy the Worker for you

Once the Worker exists, new versions can deploy on their own whenever
`admin/worker` changes. Add these to
<https://github.com/vikashtechnology/m.calculator/settings/secrets/actions>:

| Kind | Name | Where to find it |
| --- | --- | --- |
| Secret | `CLOUDFLARE_API_TOKEN` | Cloudflare → **My Profile → API Tokens → Create Token → Edit Cloudflare Workers** |
| Secret | `CLOUDFLARE_ACCOUNT_ID` | Cloudflare dashboard → **Workers & Pages** → *Account ID* in the sidebar |
| Secret | `ADMIN_TOKEN` | the password from step 5 |
| Variable | `D1_DATABASE_ID` | D1 → `m_calculator_stats` → the **Database ID** (a UUID) |

Then the **Dashboard worker** workflow (Actions tab) deploys and runs the health
check on every change. Until those exist it prints what is missing and skips —
it never fails a run for being unconfigured.

## Optional: the same setup from a terminal

If you ever prefer the command line, `admin/worker/setup.sh` does all of the
above in one command (login, create the database, write `wrangler.toml`, create
the tables, store the token, deploy, health check) — see
[`README.md`](README.md). Both paths deploy the same Worker.
