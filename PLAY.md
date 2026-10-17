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
• Light and dark themes, no sign-up, works offline
• Free, supported by a small banner ad
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

It's free, and all the maths works offline. Thank you!
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
  > • No sign-up. The maths never leaves your phone.
  > • Free to use, paid for by one banner ad at the bottom of the screen. It
  > never sees your calculations, and where the law requires a choice the app
  > asks for consent before the first ad is requested.
  >
  > Anonymous usage counts can be switched off in the menu, and nothing is sent
  > while they are off.

* **Privacy policy URL** — the Worker from `admin/worker` already serves one:

  ``https://<your-worker>.workers.dev/privacy``

  Before publishing, open `admin/worker/worker.js`, set `CONTACT` at the top to
  the support email you use on the listing, and redeploy (paste and Deploy in
  Cloudflare — see [`WEB_SETUP.md`](WEB_SETUP.md)).

## The ad units (AdMob)

The banner is real AdMob code. Whether it serves a test ad or a paid one depends
on the two IDs the build is given, and they come from `gradle.properties`:

```properties
admobAppId=ca-app-pub-7016837191893767~7695145778
admobBannerUnitId=ca-app-pub-7016837191893767/4338354697
```

Both are set in `gradle.properties` for this app, so every build CI produces
carries the real units. Remove the two lines and the build falls back to
**Google's public test units** — a "Test Ad" banner that earns nothing.

* Get the real pair from <https://admob.google.com> → **Apps → Add app**
  (the app ID, with the `~`) and **Ad units → Banner** (the unit ID, with the
  `/`). One app can hold several units; the banner uses the first one.
* The app ID is injected into the manifest meta-data from the same property, so
  setting it in one place is enough — the consent SDK reads it there.
* **Uploading a bundle that still carries the test app ID breaks Google's
  policy.** Put the real values in `gradle.properties` (or pass
  `-PadmobAppId=… -PadmobBannerUnitId=…`) before creating the release bundle.
* On the first launch in the EEA, the UK or Switzerland the consent form appears
  before any ad is requested; where Google requires a way back into it, the app
  menu shows **Privacy settings**. Answer *App content → Ads* accordingly.

## Checking that the banner is working

Three places say what is happening, in this order of usefulness:

1. **On the phone, in a *debug* build: ☰ → Settings → "Ad diagnostics".** It
   names the units the build carries and the last thing the banner did. Release
   builds show nothing about ads here — the wording below is for the debug box:

   | What it says | What it means |
   | --- | --- |
   | *Banner is showing* | Working. Impressions are being recorded in AdMob. |
   | *No ad right now — …* | The request reached Google; there was simply no ad to fill it. Normal for the first day or two of a new AdMob app, and in some countries. |
   | *Starting the ads SDK…* / *Waiting for the consent form…* | Still getting ready. If it stays here, the phone has no working connection to Google. |
   | *No room for a banner on this screen* | The screen is too short for the ad slot (616dp of usable height is needed, after the status and navigation bars). The calculator fills the gap, no ad is requested. |
   | *Google test units* | This is a debug build, or the IDs are missing from `gradle.properties`. Test ads earn nothing. |

2. **In AdMob: Apps → AI Calculator → Ad units → the banner unit.** Impressions
   and requests are counted per day. A handful of requests with zero impressions
   on the first day is normal.

3. **Logcat**, if you have a computer attached: `adb logcat -s Ads AdBanner` —
   every step (SDK start, consent result, load success, failure with Google's
   own code and message) is logged there.

### Two things that are not faults

* **"No ads to show" for the first day or two.** A brand-new AdMob app has no
  serving history; Google fills it in as it learns. Nothing to fix, and nothing
  to change in the app.
* **An ad that never appears in a debug build.** Debug builds deliberately ask
  for Google's test ads instead of the real units, so that tapping them while
  testing can never count as invalid traffic on the account. Live ads only come
  from the release APK/AAB.

### Never tap your own live ads

Clicking or repeatedly refreshing ads on the app's own units is invalid traffic,
and it is the fastest way to have an AdMob account suspended. To test safely:
add the phone as a **test device** (AdMob → the gear icon → *Test devices*; you
need the phone's advertising ID from Android's Settings → Privacy → Ads), or
install a debug build, which uses Google's test units by design.

## Creating the AdMob app and ad unit, click by click

1. **Sign in** at <https://admob.google.com> with the Google account you want to
   keep for this app — an AdMob app cannot be moved to another account later.
2. **First-time sign-up:** accept the terms, then set your **country** and
   **time zone**. Reports follow the time zone, so pick the one you actually
   live in; it cannot be changed afterwards.
3. **Payments — can wait:** *Payments → Add payment method* (name, address, bank
   details). Nothing is paid out until you pass the threshold shown there, so
   this is only needed before the first payout.
4. **Add the app:** *Apps → Add app*.
   * Platform: **Android**.
   * "Is your app listed on a supported app store?" → **No** (it is not on Play
     yet; that answer is fine).
   * App name: `AI Calculator`.
   AdMob then shows the **App ID** — the one with the tilde, like
   `ca-app-pub-0000000000000000~0000000000`.
5. **Add the banner unit:** open the app → *Ad units → Add ad unit* → **Banner**.
   * Name: `Calculator banner` (any name works).
   * Leave the advanced settings alone; the default refresh rate is what the
     code expects.
   * Save, then copy the **ad unit ID** — the one with the slash, like
     `ca-app-pub-0000000000000000/0000000000`.
6. **Put both into the build:** `gradle.properties` → `admobAppId=…` and
   `admobBannerUnitId=…`, then rebuild. No code changes needed.
7. **While testing, stay on the test units.** Google's IDs earn nothing and
   cannot get your account suspended; tapping your own live ads can.

Two things worth knowing:

* A brand-new AdMob app can answer "no ads to show" for a while — inventory has
  to build up. It fills in on its own.
* Once the app is live on Play, publish an **`app-ads.txt`** file (AdMob shows
  the exact line under *Apps → app-ads.txt*) on the website named on your Play
  listing. Without it, some advertisers will not bid.

## Data safety form

Play asks what the app collects and shares. These are the answers that match
what the code actually does:

| Question | Answer |
| --- | --- |
| Does your app collect or share any required user data types? | **Yes** |
| **App activity → App interactions** | Collected, not shared, **optional** (the user can turn the counters off), used for Analytics |
| **Device or other IDs** | Collected (a random install id made on the phone, not an Android ID), not shared, optional, used for Analytics |
| **Photos** | Shared **only** when the user attaches one to an Ask AI question — with the AI service the user's own key belongs to (Groq by default), for App functionality. Not stored by the app |
| **Voice** | Ask AI can dictate a question using the phone's own speech recogniser. The app records nothing and keeps no audio |
| **Files and docs** | Same as photos, for PDFs and text attachments |
| Location, personal info, financial info, messages, contacts, health | **Not collected** |
| Is data encrypted in transit? | **Yes** (HTTPS only) |
| Can users request deletion? | **Yes** — contact email, or turn the counters off in the app |
| **Device or other IDs → Advertising ID** | Collected by Google's Mobile Ads SDK when an ad loads, shared with Google for Advertising or marketing. Present in the ad-supported build only |
| Is data sold or used for advertising? | **Yes** — the ad SDK uses the advertising ID to serve and measure ads. Nothing the user types, calculates or asks is ever used for advertising, and the maths stays offline |

Content rating: answer the questionnaire honestly — a general-purpose
calculator is "Everyone". Declare **contains ads** (AdMob banners) and **no
in-app purchases**. Play then asks for the app's **Ads** declaration separately
in *App content → Ads*: answer "Yes, my app contains ads".

## The first upload, step by step

1. **Developer account** — <https://play.google.com/console>. The one-off fee is
   $25. A *personal* account created after 13 November 2023 also has to be
   identity-verified (photo ID + address), and cannot go to production until it
   has run a closed test — see below.
2. **Create app** — name `AI Calculator`, default language, "App", "Free".
3. **Set up your app** (the Console's checklist): privacy policy URL, app
   access (everything works without a login — say so), ads = yes, content rating
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
* The banner is a fixed 320×50 ad in a slot that is reserved from the first
  frame, so an ad arriving never resizes the keypad. The slot only opens on
  screens with room to spare (about 560dp of content, which covers ordinary
  phones); on a small screen or in split screen no ad is requested at all.
* Ad revenue and the wording of the listing have to move together: the store
  text, the data-safety answers and the privacy policy all say the app carries
  ads now.
