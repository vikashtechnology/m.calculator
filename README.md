# AI Calculator

An advanced scientific calculator for Android — offline-first, ad-supported and
open source.
Written in Kotlin with Jetpack Compose, it ships a hand-written math engine that
handles real and complex numbers, matrices, calculus, units and statistics.

> **Status:** version 1.1.0. Unit tests cover the math engine, and every push
> is compiled into an installable APK by GitHub Actions.

---

## Get the app

1. Open the [**latest-build release**](../../releases/tag/latest-build) and
   download `app-release.apk`. Every green build refreshes that same tag, so the
   file there is always the newest working version (branch builds are marked as
   pre-releases, which is why the direct tag link is used).
2. On the phone, tap the downloaded file and allow *Install unknown apps* for your
   browser or file manager when Android asks.
3. Alternatively, open the **Actions** tab, pick the newest green *Android CI* run
   and download the `m-calculator-apk` artifact (contains both the debug and
   release APKs).

Requirements: Android 7.0 (API 24) or newer. The maths always runs offline —
the only two things that touch the network are **Ask AI** (your own key) and the
**AdMob banner**, and both simply do nothing when there is no connection. Of the
app's permissions (`INTERNET`, `CAMERA`, `POST_NOTIFICATIONS`), the camera and
notifications belong to Ask AI alone; `INTERNET` also carries the ad request.

## Features

### Navigation
The six tools live in a **drop-down menu**: tap the ☰ button in the top bar and the
panel drops down over the screen with three lines of two tiles (Calc, Graph /
Solve, Matrix / Units, Stats), followed by **Everyday calculators** — ten small
tools in three-per-line tiles (GST, Tax, EMI, BMI, Percent, Interest, Profit,
Algebra, Bases, Age). Pick a tile to switch, or dismiss it by tapping the
scrim, the close icon, the ☰ button again, or the back button. Because the menu
overlays rather than reserves space, every tool uses the full screen — and the
keypad no longer shares the screen with a bottom tab bar.

### Calculator pad
* Full expression parser: `2+3(4+1)`, `2π`, `2sin(30)`, `3!`, `50%`, `10 mod 3`
* Live preview of the answer while you type, plus alternate forms (`≈ 1/3`)
* Degrees / radians / gradians, `Ans`, memory (`MS`, `M+`, `M−`, `MR`), `π e φ τ i`
* Variables `A`–`F` and `x` — long press a key to store the current result
* History of the last 200 calculations, tap to reuse, long press to reuse the result
* Every key documents itself: long press it
* Swipe-free editing: tap anywhere in the expression to place the cursor
* Copy or **share** a result straight into any other app
* **Share a sum to the app** from anywhere (WhatsApp, a note, a browser): the pad
  calculates it, and a shared question goes to Ask AI instead. A `calc:2+3` link
  does the same from a browser
* **Settings** (drop-down menu): digits shown (8/10/12/15), digit grouping
  (off / 1,234,567 / 12,34,567), and the key feedback — a short knock on every
  press, plus an optional click sound

### Functions (≈90)
| Group | Examples |
| --- | --- |
| Trigonometry | `sin cos tan csc sec cot asin acos atan atan2` (angle-mode aware) |
| Hyperbolic | `sinh cosh tanh csch sech coth asinh acosh atanh` |
| Logs & powers | `ln log log2 exp sqrt cbrt nthroot hypot x^y` |
| Rounding | `floor ceil round(x,n) trunc frac abs sign` |
| Combinatorics | `ncr npr fact gamma fib gcd lcm` |
| Statistics | `mean median stdev variance min max sum product count` |
| Complex numbers | `i`, `abs`, `re`, `im`, `conj`, `arg`, `polar`, and complex `sqrt/ln/sin`… |
| Matrices | `det inv transpose trace rank dot cross solve identity dims normalize` |

### Graphing
Up to five curves with automatic 1-2-5 grid steps, drag to pan, pinch to zoom and
tap-to-trace. Handles poles and off-domain gaps without drawing false lines.

### Solver and calculus
* **Solve** `f(x) = 0` — Newton's method backed by a sign-change scan, reports every root
* **d/dx** at a point (five-point formula) with the second derivative for reference
* **∫** definite integral using Simpson's rule, refined once for accuracy
* **Σ / Π** sums and products over integer ranges

### Matrices
Build matrices cell by cell or type `[1, 2; 3, 4]`. Add, subtract, multiply, power,
determinant, inverse, transpose, rank, trace, dot, cross, solve `A·x = b`.

### Units
11 categories (length, mass, temperature, area, volume, speed, time, data, angle,
energy, pressure) with a live table of every unit, plus quick text conversion:
type `12 km to mi` and press Convert.

### Statistics
Paste numbers and get count, sum, mean, median, quartiles, min/max, range, sample
and population standard deviation and variance.

### Everyday calculators
Tap ☰ and pick a tile; the tools open over the pad and the ☰ still switches
between them. Every field recalculates as you type — there is no Solve button.

| Tool | What it does |
| --- | --- |
| **GST** | Exclusive/inclusive split with CGST and SGST halves, any rate |
| **Tax** | Indian income tax, FY 2025-26 (new regime) and old regime, slab breakdown, rebate and marginal relief, cess, effective rate |
| **EMI** | Monthly instalment, total interest, and a month-by-month amortisation table |
| **BMI** | Body mass index with the category and the healthy weight range |
| **Percent** | X% of Y, X is what % of Y, % change, and ±% change of a value |
| **Interest** | Simple and compound interest side by side, with yearly/quarterly/monthly compounding |
| **Profit** | Profit or loss, the profit percentage, margin on cost and selling price, markup |
| **Algebra** | Quadratic roots (including complex), discriminant and vertex, primes, prime factorisation |
| **Bases** | Convert between binary, octal, decimal, hex and any base from 2 to 36 |
| **Age** | Age in years/months/days plus total days/weeks/months and days to the next birthday |
| **Date** | Days/weeks/months/years between two dates, and "add or subtract days or months" |

### Ask AI (bring your own key — nothing to sign up for)
The **Ask AI** icon in the readout's toolbar (beside the history button) opens
a chat, and carries what is on the display with it: an answer travels as "explain
step by step how this is worked out", an error as "this gave an error, what went
wrong?".

* **type** a question, **dictate** it, **photograph** it with the in-app camera,
  or **attach** a photo or text file — then send
* the answer **streams in** as it is written, so words appear while the model is
  still working rather than after a long pause
* answers come back with the working, ready to copy
* **no memory**: every question is sent on its own, nothing is stored on the
  phone, and *New chat* (or closing the app) wipes the thread
* if an answer arrives while you are elsewhere in the app, a notification tells
  you it is ready

It runs on a **free API key the user pastes in themselves**, for whichever
service they prefer — they all speak the same OpenAI-style API, so switching is
a tap:

| Service | Default model | Where the key comes from |
| --- | --- | --- |
| **Groq** (default) | GPT-OSS 120B | `console.groq.com/keys` — email login, no card |
| OpenRouter | you pick from the live list | `openrouter.ai/keys` |
| Cerebras | you pick from the live list | `cloud.cerebras.ai` |
| Other | any OpenAI-compatible service | the address and key of that service |

* Groq is the default because nothing else answers as fast: its hardware runs
  open models at several hundred tokens a second, which is what makes a chat
  feel instant. *Load models* in the settings card asks the service what the key
  can use, so a retired model is a tap to replace rather than a broken app
* a photo needs a model that can see; when the chosen one cannot, the app uses
  the provider's vision model for that question, and the answer says which model
  wrote it
* the key stays in the app's private storage, is sent only to that service with
  the question, and can be removed from the same card
* no sign-in, no account, and **no key is compiled into the APK** — so a copy of
  the app someone else installs cannot spend the publisher's quota

Permissions, and what they are for:

| Permission | Why |
| --- | --- |
| `INTERNET` | Ask AI talking to the AI service, and the ad banner. Nothing else leaves the phone. |
| `CAMERA` | Photographing a question inside Ask AI — requested the first time the camera button is tapped, and never needed otherwise. |
| `RECORD_AUDIO` | Dictating a question. The phone's own speech recogniser turns speech into text; the app records nothing and keeps no audio. |
| `POST_NOTIFICATIONS` | The "your answer is ready" notification (Android 13+), requested when Ask AI opens. |

Optional build properties:

```bash
-PtelemetryUrl=…        # the Cloudflare Worker that counts anonymous usage
-PversionCode=…         # the versionCode Play wants for an upload
```

### Usage dashboard (optional)
`admin/worker` holds a Cloudflare Worker that serves a small read-only dashboard:
installs, active users (today / week / range) and calculations and AI questions
per day, with a per-day table and chart. One D1 database, no other service, and
the whole setup is one command:

There are two ways to set it up: entirely in the Cloudflare dashboard — see
[`admin/WEB_SETUP.md`](admin/WEB_SETUP.md), where the Worker even creates its own
tables — or from a terminal with one command:

```bash
cd admin/worker && ./setup.sh        # creates the database, deploys, prints the URL
```

Then add the printed URL as the repository secret `TELEMETRY_URL` and the next
build reports counts. `…/health` lists what is configured; see
[`admin/README.md`](admin/README.md) for the details and the optional
auto-deploy workflow.

The app sends only aggregate counts (app opens, calculations, AI questions) with
a random per-install id: no expressions, results, files or accounts. The switch
*Anonymous usage stats* in the drop-down menu turns it off, and builds without a
`TELEMETRY_URL` never send anything at all.

## Look and feel

Material 3 throughout: the app follows your phone theme — light in the day, dark
at night — with a violet/cyan/amber palette tuned for both. Cards, keys and
messages carry soft shadows so the surfaces read as layers rather than flat
blocks: the readout sits on a raised card, keypad keys are physical buttons, the
menu and the chat header are elevated sheets, and chat bubbles lift off the chat
background.

The pad holds still: the keypad is laid out at one size — measured for the page
with the most keys, so switching pages and an ad appearing both change nothing —
and the readout above it takes every dp that is left over. The readout's text
does the adapting instead: the expression and the answer start big and shrink to
fit, so a short answer shows up large and a long one comes back smaller but
whole rather than cut off.

## Building it yourself

```bash
# Android Studio: open the project and press Run.
# Command line:
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # math engine test suite
./gradlew assembleRelease        # release APK (uses the bundled key in
                                 # app/signing/ unless keystore.properties exists)

# Optional signing for a real release build:
cat > keystore.properties <<'EOF'
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
EOF
```

Toolchain: AGP 8.5.2, Kotlin 2.4.10, Gradle 8.9, JDK 17, compileSdk and
targetSdk 36, minSdk 24. (Kotlin 2.4 is not optional: the AdMob SDKs carry Kotlin
2.3+ metadata that older compilers refuse to read.)

Optional build properties (all safe to leave out):

```bash
-PtelemetryUrl=…        # sends anonymous usage counts to the admin dashboard
-PversionCode=…         # the versionCode Play wants for an upload
-PadmobAppId=…          # override the AdMob app ID from gradle.properties
-PadmobBannerUnitId=…   # override the banner unit ID
```

## Project layout

```
app/src/main/java/com/advanceai/calculator/
├── engine/          pure Kotlin math: lexer, parser, evaluator, complex,
│                    matrix, calculus, units, formatter (no Android imports)
├── data/            SharedPreferences store for settings, variables, history,
│                    the user's own API key and the anonymous usage counters
├── ai/              the chat state, the OpenAI-compatible client, dictation,
│                    image shrinking and the answer notification
├── ui/              Compose UI: view model, keypads, sheets, theme
│   ├── components/  keypad grid and display panel
│   └── screens/     pad, graph, solver, matrix, units, stats, Ask AI, everyday tools
└── MainActivity.kt
app/signing/         the key every build is signed with + its fingerprints
admin/worker/        the Cloudflare Worker that serves the usage dashboard
app/src/test/        JUnit suite for the engine
tools/               icon generator and the Python reference oracle
```

## Testing

`app/src/test` contains golden-value tests for precedence, implicit
multiplication, angle modes, complex arithmetic, matrices, calculus, unit
conversion and the error messages. Expected strings come from `tools/reference.py`,
which re-computes every case with Python's `math` module using the same rounding
rules as `Formatter`.

```bash
python3 tools/make_icons.py               # redraw the launcher icons
python3 tools/check_theme.py              # palette + contrast audit
python3 tools/reference.py                # print expected values for the tests
```

## The launcher icon

The icon is drawn in code: `tools/make_icons.py` is Pillow-only (the build
environment has no SVG rasteriser), and every shape is written in the
coordinates of the source artwork's own 640-unit viewBox, so one shape set
renders from 48 px up to 512 px without redrawing anything.

```bash
python3 tools/make_icons.py        # writes the icons and the preview sheets
```

A run writes, for each density, `ic_launcher`, `ic_launcher_round`, the adaptive
background, foreground and monochrome layers, plus
`assets/play-store-icon-512.png`, `assets/app-icon-preview.png` (the sheet that
shows the icon, the launcher masks, every size and a home screen) and
`assets/icon-layers.png` (each Android layer on its own). The adaptive
foreground and the monochrome glyph are scaled to 0.80 and 0.90 so that the AI
badge survives a circular mask; `assets/app-icon.svg` is the source the drawing
follows, kept for reference.

## Publishing to Google Play

The Android App Bundle (`app-release.aab`) is built by CI on every push and sits
next to the APK on the `latest-build` release:

```bash
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
```

`compileSdk`/`targetSdk` are **36** because Play requires new apps and updates to
target Android 16 (API 36) since 31 August 2026. The store listing text, icon,
feature graphic, screenshots, the data-safety answers, the signing-key decision
and the version-bumping flow are all written out in [`PLAY.md`](PLAY.md). The
Play icon (512×512), feature graphic (1024×500) and three phone screenshots come
from `tools/make_icons.py`, and the privacy policy Play asks for is served by the
admin Worker at `/privacy`.

## Colours and themes

The palette lives entirely in `ui/theme/Theme.kt` — both the dark and the light
scheme define every Material role, plus the chart colours Material has no slot
for. Screens read roles (`onSurfaceVariant`, `primaryContainer`, …) and never
paint a raw colour, which is what keeps the text readable when the theme flips.
The **Appearance** switch in the drop-down menu picks System, Light or Dark, and
the window's status-bar icons follow that choice rather than the phone's.

```bash
python3 tools/check_theme.py      # no stray colours, both palettes complete, contrast measured
```

The check fails the build when a colour appears outside the theme file, when the
two palettes drift apart, or when any (text, background) pair the screens use
drops under 4.5:1 for body text / 3:1 for large text and plot strokes.

## Continuous integration

`.github/workflows/android.yml` runs on every push: it executes the unit tests,
builds the debug and release APKs, uploads them as artifacts, and refreshes the
`latest-build` release. If a build or test fails, the job posts the compiler
errors and failing assertions to a `CI failure` issue so the run can be fixed
from the report alone.

## License

Mozilla Public License 2.0 — see [LICENSE](LICENSE).
