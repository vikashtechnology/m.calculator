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

1. Copy **all** of `admin/worker/worker.js` — the file in this project, or on
   GitHub:

   <https://github.com/vikkiRoy899/m.calculator/blob/main/admin/worker/worker.js>

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
4. **Name: `ADMIN_PASSWORD_HASH`** — exactly.
5. **Value:** the output of `node admin/worker/hash-password.mjs 'your password'`
   (it starts with `pbkdf2$`). The password itself is never stored — not here,
   not in the database, not in this repository.
6. **Add** a second secret, **`ADMIN_EMAIL`**, if the address that signs in is not
   the built-in `rajkatrina90@gmail.com`.
7. Select **Save** / **Deploy** so the Worker runs with the new secrets.

## 6. Check the Worker

Open `https://ai-calculator-stats.<your-subdomain>.workers.dev/health` (use your
Worker's own URL). This is the setup checklist, and it also creates the tables.
You want:

```json
{
  "ok": true,
  "service": "AI Calculator stats",
  "database": "ok",
  "schema": "ready",
  "signIn": { "method": "email and password; the session goes in an Authorization header",
              "email": "rajkatrina90@gmail.com", "customPassword": true,
              "customSessionKey": false }
}
```

* `"ok": false` with `"database": "error: … Cannot read properties of undefined"` →
  the binding is missing or misnamed: it must be the variable name **DB** (step 3).
* `"customPassword": false` → the built-in password is still in use. It works,
  but set `ADMIN_PASSWORD_HASH` (step 5) before showing the dashboard to anyone.

The Worker is an API: it answers JSON and holds the data. The dashboard itself is
a page on GitHub Pages, and step 7 puts it there.

### Editing the app's About page

The same dashboard edits the page that **☰ → About AI Calculator** shows in the
app:

1. Scroll to the panel headed **About page (shown inside the app)**.
2. Type the title, the tagline, the paragraphs (one per line), any links (one per
   line as `Label | https://address`) and a support email.
3. Select **Save the About page**. It saves as the account you signed in with.
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
* `GET /v1/about` is public (the app reads it without signing in); saving needs
  the admin session, exactly like the stats.

#### The donate button

The same panel has a **donate** block:

* **Show the donate button in the app** — uncheck it and the card disappears from
  the app's About page entirely.
* **Donate UPI id** — the address a UPI app pays to, in the form `name@bank`
  (here `vikashraoa2343-1@oksbi`). The app builds the payment link and draws the
  QR code from this id, so changing it here changes the code as well; there is no
  picture to replace.
* **Donate name** — what the payer's UPI app shows as the recipient.

A value that is not a plausible UPI id is refused rather than saved — a typo
would send money nowhere. Switching the button off keeps the id, so it can be
switched back on later.

QR codes are drawn black on white in both themes on purpose: a camera has to read
them. A pre-generated copy of the same code is in `assets/donate-qr.png` (it
encodes `upi://pay?pa=vikashraoa2343-1@oksbi&pn=AI%20Calculator&cu=INR`) for
printing or posting outside the app.

### If the dashboard will not let you in

* **It keeps asking me to sign in.** The session is kept by the page in that tab
  only, so a new tab, a new browser or a reload of the same tab after it was
  closed means signing in again. Sessions last 30 days at most.
* **It says "That email and password do not match."** Either the password is not
  the one this Worker was given, or `ADMIN_PASSWORD_HASH` / `ADMIN_EMAIL` were
  changed afterwards. Set a new one: `node admin/worker/hash-password.mjs 'new
  password'`, then **Workers & Pages → your Worker → Settings → Variables and
  Secrets → `ADMIN_PASSWORD_HASH` → Edit**. `/health` reports
  `"customPassword": true` when a hash of your own is in place, and
  `"customPassword": false` when the built-in account is still being used.
* **Too many attempts.** Eight wrong attempts from one address in ten minutes
  are refused for the rest of that window — that is the brute-force guard, not a
  fault.
* **You wanted to change the Worker code but the editor is read-only.** That
  means this Worker was last deployed from a terminal or from the GitHub Action
  rather than pasted in by hand; Cloudflare does not allow hand-editing those.
  Use the same route that deployed it (Actions → **Dashboard worker** → *Run
  workflow*), which deploys `admin/worker/worker.js` from the repository.
* **Which version is live?** `GET /health` lists the endpoints the deployed
  Worker knows. If `/v1/login` is not in that list, the deployed copy is older
  than the dashboard page — paste `worker.js` again (step 4) or run the action.
* **No browser works at all?** The API is plain JSON, so `curl` is the way in:

  ```bash
  TOKEN=$(curl -s -X POST https://<your-worker>.workers.dev/v1/login \
    -H 'content-type: application/json' \
    -d '{"email":"rajkatrina90@gmail.com","password":"…"}' \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
  curl -s -H "authorization: Bearer $TOKEN" 'https://<your-worker>.workers.dev/v1/stats?days=30'
  ```

## 7. Publish the dashboard on GitHub Pages

The dashboard is four static files in `admin/web`, and GitHub Pages serves them.
It holds no data: it signs in to the Worker and reads everything from there.

1. In the repository: **Settings → Pages → Build and deployment → Source:
   GitHub Actions**.
2. **Actions → Admin page (GitHub Pages) → Run workflow**. The page appears at

   ```
   https://vikkiroy899.github.io/m.calculator/
   ```

   After that, every change under `admin/web/` republishes it.
3. If your Worker is at a different address from the one in
   `admin/web/config.js`, change that one line (no trailing slash) and run the
   workflow again. It is the same address as `TELEMETRY_URL`.
4. Open it and sign in with the admin email and password. Until the app reports,
   every number reads 0.

The Worker's own address (`https://<your-worker>.workers.dev/`) forwards here, so
an old bookmark still lands on the dashboard.

**How the sign-in travels.** Pages on `github.io` and the Worker on `workers.dev`
are different sites, so a cookie cannot carry the session between them — browsers
drop third-party cookies. Instead the page asks `POST /v1/login` for a signed
session and sends it as an `Authorization` header. The password is only ever
checked by the Worker, the session stays in that tab, and nothing goes in the
address bar.

**If the workflow fails at "Set up Pages"**, the repository is private: GitHub
Pages on a private repository needs a paid plan. Either make the repository
public or keep administering through `curl` (above).

**Signing out** forgets the session in that tab. A signed session cannot be
called back before it expires — that is what makes it stateless — so if one ever
leaks, set a new `SESSION_SECRET` (Workers & Pages → your Worker → Settings →
Variables and Secrets) and every session ends at once.

## 8. Point the app at it (GitHub, one field)

1. Open <https://github.com/vikkiRoy899/m.calculator/settings/secrets/actions>
2. **New repository secret**
   * **Name:** `TELEMETRY_URL`
   * **Secret:** your Worker URL, `https://ai-calculator-stats.<your-subdomain>.workers.dev`
     — no trailing slash, no `/health`.
3. **Add secret**

While you are on that page, remove or leave empty anything you do not want: with
no `TELEMETRY_URL`, the app simply keeps its counters on the phone.

## 9. Get an APK that reports

1. Open <https://github.com/vikkiRoy899/m.calculator/actions/workflows/android.yml>
2. Select **Run workflow** → **Run workflow** (branch: `main`).
3. When the run finishes, the fresh APK is on
   <https://github.com/vikkiRoy899/m.calculator/releases/tag/latest-build>
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
<https://github.com/vikkiRoy899/m.calculator/settings/secrets/actions>:

| Kind | Name | Where to find it |
| --- | --- | --- |
| Secret | `CLOUDFLARE_API_TOKEN` | Cloudflare → **My Profile → API Tokens → Create Token → Edit Cloudflare Workers** |
| Secret | `CLOUDFLARE_ACCOUNT_ID` | Cloudflare dashboard → **Workers & Pages** → *Account ID* in the sidebar |
| Secret | `ADMIN_EMAIL` | the address that signs in (default `rajkatrina90@gmail.com`) |
| Secret | `ADMIN_PASSWORD_HASH` | from `node admin/worker/hash-password.mjs '…'` |
| Variable | `D1_DATABASE_ID` | D1 → `m_calculator_stats` → the **Database ID** (a UUID) |

Then the **Dashboard worker** workflow (Actions tab) deploys and runs the health
check on every change. Until those exist it prints what is missing and skips —
it never fails a run for being unconfigured.

## Optional: the same setup from a terminal

If you ever prefer the command line, `admin/worker/setup.sh` does all of the
above in one command (login, create the database, write `wrangler.toml`, create
the tables, store the sign-in, deploy, health check) — see
[`README.md`](README.md). Both paths deploy the same Worker.
