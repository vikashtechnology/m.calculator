# Publishing on Google Play

**What this file is:** the runbook for putting AI Calculator on Google Play —
where the upload file comes from, which key signs it, every word and picture the
store listing needs, the data-safety answers, and the order to do it in. Read it
top to bottom the first time; after that, only *"Every update after that"*
matters.

The build that Play accepts is the **Android App Bundle** (`app-release.aab`).
It is built by the same CI run as the APK and published on the rolling release
together with it:

* APK (install on a phone): `…/releases/download/latest-build/app-release.apk`
* **AAB (upload to Play): `…/releases/download/latest-build/app-release.aab`**
* Artifacts tab of any CI run also carries both (`m-calculator-build`).

Locally, the same file comes from:

```bash
./gradlew bundleRelease        # → app/build/outputs/bundle/release/app-release.aab
```

## Public or private repository — what actually changes

Nothing in the app, the admin Worker, or the Play upload depends on the
repository being public. Three things do change:

| | Public (today) | Private |
| --- | --- | --- |
| **CI minutes** | unlimited and free | Free plan: 2,000 Linux minutes/month, then $0.006/min or runs are blocked at a $0 spend limit. A build here takes ~4½ minutes, so the free allowance is roughly **440 builds a month** |
| **Release downloads** | `latest-build` links work for anyone, no login | links need a GitHub login, so **testers cannot install the APK by URL** |
| **File links** (raw `worker.js`, this file) | open in any browser | need login (fine for you, not for a shared link) |
| **The bundled signing key** | visible to everyone | hidden — but it is already in the git history, so make a fresh upload key for Play either way (see above) |
| Play Console, Cloudflare Worker, app behaviour | — | unchanged |

So the only real consequence is **how testers get the app**. Two ways to keep
that working after switching to private:

1. **Use Play's closed testing track** (which the 12-tester/14-day rule makes
   unavoidable for a new personal account anyway). Testers open the Play link
   you send them and install through the Play Store — no repository access
   needed, and it works for the APK-side too because Play ships the `.aab`.
2. **Or keep an APK link alive** by publishing builds to a second, always-public
   repository (for example `m.calculator-releases`) from this workflow, or by
   uploading the APK to Cloudflare R2 / any file host and sharing that URL.

To switch: **Settings → General → Danger zone → Change repository visibility**.
Everything under `admin/` keeps deploying exactly as before — the worker workflow
runs inside whichever repository it lives in.

## Copy-paste kit

**Release notes** — *What's new* in Play Console (limit 500 characters):

```
First release.
• Scientific calculator with history, fractions and exact results
• Graphs: up to five functions, pan, zoom and trace
• Solver, derivatives, integrals and series
• Matrices, complex numbers, 250+ unit conversions
• GST, income tax, EMI, BMI, interest, profit, bases, age
• Ask AI (optional, your own key): type, photograph or attach
• Light and dark themes, no sign-up, no ads, works offline
```

**Closed-test invitation** — send this to your 12 testers (WhatsApp, email,
Slack — anything):

```
Hi! I'm testing my Android calculator app on Google Play before releasing it —
could you help me out?

1. Open this link on your Android phone: <PASTE YOUR CLOSED-TEST LINK>
2. Tap "Become a tester", then "Install".
3. Please keep it installed and open it at least once a day for the next two
   weeks. Play requires 12 testers using an app for 14 days in a row before it
   can be published, so please don't uninstall it in that time.
4. If anything looks wrong or confusing, tell me — your replies are what makes
   the next version better.

It's free, no ads, and all the maths works offline. Thank you!
```

Where the link comes from: Play Console → **Testing → Closed testing → Testers**
→ add the testers' Google accounts (or a Google Group) → copy the
**"How testers join your test"** link.

**Support email** — needed on the listing and in the privacy policy. Put the same
address into `CONTACT` at the top of `admin/worker/worker.js`.

**App category / tags** — *Tools* is the honest category; tags: Calculator,
Utilities, Education.

## Read this first: the signing key

Out of the box the project signs with the committed key
`app/signing/mcalculator.p12` (alias `mcalculator`, password `mcalculator`).
That key was fine for sideloaded test builds, but **it is in a public
repository**, so it must not be the key Play uses to sign your app: anyone
holding it could publish an update as you.

So, before the first upload, do one of these:

**A. Let Google sign it (recommended, least work).** Upload the bundle as it is
and enrol in **Play App Signing** when the Console offers it during the first
upload. Google then generates and holds the *app signing key*, and the committed
key is only used to prove the upload is yours. You can rotate the upload key
later without losing your users.

**B. Create your own upload key (more control).**

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the project root (it is git-ignored — never
commit it, and keep a backup, because losing it means support has to reset your
upload key):

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

`app/build.gradle.kts` picks it up automatically and signs every release with
it. Rebuild the bundle (`./gradlew bundleRelease`, or let CI do it).

Optionally put the same four values in GitHub secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and let CI
sign instead; the workflow reads a `keystore.properties` if one is present.

## Store listing

Everything the listing needs is generated in the repository, and can be
refreshed with `python3 tools/make_icons.py`:

| Play field | Requirement | File |
| --- | --- | --- |
| App icon | 512×512 PNG | `assets/play-store-icon-512.png` |
| Feature graphic | 1024×500 PNG | `assets/play-feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, 16:9 or 9:16 | `assets/play-screenshot-1..3-1080x1920.png` |

Screenshots of the real app are even better — take them on a phone (that is what
the eye-catching ones are), keep at least two, and upload those instead.

Suggested text:

* **Name** (30 chars): `AI Calculator`
* **Short description** (80 chars):
  `Scientific calculator with graphing, calculus, matrices, units and AI help.`
* **Full description** (4000 chars) — the README's feature list, cut down:

  > AI Calculator is a fast, private, all-in-one calculator.
  >
  > • Everything computes offline: arithmetic, percentages, powers and roots,
  > factorials, trigonometry in degrees, radians or gradians, logs, statistics
  > and exact fractions.
  > • Calculus: solve equations, differentiate, integrate, and evaluate Σ and Π
  > series.
  > • Graphs: plot up to five functions, pan, pinch-zoom and trace values.
  > • Matrices: add, multiply, invert, determinant, rank, dot and cross products.
  > • Complex numbers, including polar form.
  > • Units: lengths, areas, volumes, mass, temperature, speed, pressure, energy,
  > data and time.
  > • Everyday tools: GST, income tax, EMI, BMI, percentage, interest, profit and
  > loss, algebra, number bases and age.
  > • Ask AI (optional): type a question, photograph it or attach a file, and
  > read a step-by-step answer. It uses your own free Google AI Studio key, and
  > the chat keeps no history.
  > • No sign-up. No ads. The maths never leaves your phone.
  >
  > Anonymous usage counts can be switched off in the menu, and nothing is sent
  > while they are off.

* **Privacy policy URL** — the Worker from `admin/worker` already serves one:

  ``https://<your-worker>.workers.dev/privacy``

  Before publishing, open `admin/worker/worker.js`, set `CONTACT` at the top to
  the support email you use on the listing, and redeploy (paste and Deploy in
  Cloudflare — see [`WEB_SETUP.md`](WEB_SETUP.md)).

## Data safety form

Play asks what the app collects and shares. These are the answers that match
what the code actually does:

| Question | Answer |
| --- | --- |
| Does your app collect or share any required user data types? | **Yes** |
| **App activity → App interactions** | Collected, not shared, **optional** (the user can turn the counters off), used for Analytics |
| **Device or other IDs** | Collected (a random install id made on the phone, not an Android ID), not shared, optional, used for Analytics |
| **Photos** | Shared **only** when the user attaches one to an Ask AI question — with Google's Gemini API, for App functionality. Not stored by the app |
| **Files and docs** | Same as photos, for PDFs and text attachments |
| Location, personal info, financial info, messages, contacts, health | **Not collected** |
| Is data encrypted in transit? | **Yes** (HTTPS only) |
| Can users request deletion? | **Yes** — contact email, or turn the counters off in the app |
| Is data sold or used for advertising? | **No** |

Content rating: answer the questionnaire honestly — a general-purpose
calculator is "Everyone". Declare **no ads** and **no in-app purchases**.

## The first upload, step by step

1. **Developer account** — <https://play.google.com/console>. The one-off fee is
   $25. A *personal* account created after 13 November 2023 also has to be
   identity-verified (photo ID + address), and cannot go to production until it
   has run a closed test — see below.
2. **Create app** — name `AI Calculator`, default language, "App", "Free".
3. **Set up your app** (the Console's checklist): privacy policy URL, app
   access (everything works without a login — say so), ads = no, content rating
   questionnaire, target audience, data safety (table above).
4. **Store listing** — the text and the three graphics from the table above.
5. **App content → Signing** — enrol in Play App Signing if you have not already.
6. **Testing → Closed testing** — create a track, upload the `.aab`, add
   testers (12 or more for a new personal account, and they must stay opted in
   for 14 continuous days — Play says invited-but-not-installed does not count).
7. **Apply for production access** on the Dashboard once the counter is green,
   then **Production → Create release** with the same bundle and roll it out.

## Every update after that

1. Bump the version. The repository variable
   **`PLAY_VERSION_CODE`** (Settings → Secrets and variables → Actions →
   Variables) is passed to Gradle; Play rejects an upload whose `versionCode`
   is not higher than the last one, so set it to something larger before each
   upload. `versionName` in `app/build.gradle.kts` is what users see.
2. Push (or **Actions → Android CI → Run workflow** on `main`).
3. Download `app-release.aab` from the **latest-build** release.
4. **Production → Create release →** upload, add release notes, roll out.

## Notes that save a round trip

* The **package name is permanent** once published — `com.advanceai.calculator`
  is already a rename-safe choice.
* Keep the **upload key** backed up outside GitHub. If it is ever lost, use
  Play Console → *App integrity → Change upload key*.
* New apps must target **API 36 (Android 16)** since 31 August 2026; this
  project already does (`compileSdk`/`targetSdk = 36`), so uploads are accepted.
* Play flags an app that requests the **camera** unless the permission is
  clearly used — Ask AI's photo question is the reason, and it is declared in
  the manifest with `required="false"`, so tablets without a camera still work.
* If you also want the APK for phones beside the bundle, it is on the same
  release; Play itself only takes the `.aab`.
