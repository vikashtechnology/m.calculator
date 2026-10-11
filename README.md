# Ai Calculator

An advanced scientific calculator for Android — offline, ad-free and open source.
Written in Kotlin with Jetpack Compose, it ships a hand-written math engine that
handles real and complex numbers, matrices, calculus, units and statistics.

> **Status:** version 1.0.0. Unit tests cover the math engine, and every push is
> compiled into an installable APK by GitHub Actions.

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

Requirements: Android 7.0 (API 24) or newer. Everything except **Ask AI** runs
completely offline — the app asks for a single permission, `INTERNET`, and only
the Ask AI screen ever uses it.

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

### Ask AI (bring your own key — nothing to sign up for)
The hero card at the top of the pad opens **Ask AI**: upload a photo, a PDF or a
text file of a question and it comes back solved, with the working.

It runs on a **Gemini API key the user pastes in themselves**:

* the first time, Ask AI shows a card: paste a key, press *Save the key*
* *Get a free key* opens `aistudio.google.com/apikey` in the browser — one click
  with any Google account, no card, no sign-up form
* the key is kept in the app's private storage, sent only to Google with the
  question, and can be deleted from the same card at any time
* nobody signs in to anything, no account is created, and nothing about the user
  reaches this app's author

Builders can still ship a fallback: with `GEMINI_API_KEY` (or `-PgeminiApiKey=`)
the app calls Gemini with that key when the user has not saved one, and with
`AI_PROXY_URL` it hands the question to the Worker from `admin/worker`, which
verifies the user's Google sign-in and holds the key itself — that OAuth mode is
there but off unless you configure it. The bundled Puter page
(`assets/ai/index.html`) stays as a no-key-at-all option, reachable from the key
card; note that Puter's own *Sign in with Google* opens Google's web page, which
cannot list the accounts on the phone.

Optional build properties:

```bash
-PaiProxyUrl=…          # Ask AI through your Worker (Google sign-in, key on the Worker)
-PgoogleWebClientId=…   # needed only for that Worker mode
-PgeminiApiKey=…        # a key that ships with the build, used when the user has none
-PgeminiModel=…         # optional model override, default gemini-2.5-flash
-PtelemetryUrl=…        # sends anonymous usage counts to the admin dashboard
```

### Usage dashboard (optional)
`admin/worker` holds a Cloudflare Worker that serves a small read-only dashboard:
installs, active users (today / week / range) and calculations and AI questions
per day, with a per-day table and chart. It needs one D1 database, no other
service, and the whole deploy is five `wrangler` commands — see
[`admin/README.md`](admin/README.md).

The app sends only aggregate counts (app opens, calculations, AI questions) with
a random per-install id: no expressions, results, files or accounts. The switch
*Anonymous usage stats* in the drop-down menu turns it off, and builds without a
`TELEMETRY_URL` never send anything at all.

### Ask AI (optional, needs internet)
The hero card at the top of the pad opens **Ask AI**, which solves a question you
upload — a photo, a PDF or a text file — and shows the working. It is powered by
[Puter.js](https://developer.puter.com/), which supplies both the account and the
model, so the app needs no API keys and no backend of its own:

* sign in with a Puter account, or create a temporary one with *Continue as guest*
* upload a question, choose whether you want the steps, and press **Solve this question**
* the prompt is deliberately narrow: the model is told to solve *only* the
  question in the file, and to say so when the upload holds no maths question
* photos go to the model directly; PDFs are read with pdf.js (first five pages);
  `.txt`/`.md`/`.csv` files are read as text
* sign-in opens a popup, which the app hosts in its own window, because that is
  how Puter hands the token back

The page lives in `app/src/main/assets/ai/index.html` and is served to the WebView
over `https://appassets.androidplatform.net` by `WebViewAssetLoader` — a `file://`
page has no origin Puter can sign in with.

## Light and dark

The app follows your phone theme — light in the day, dark at night — with a
Material 3 palette tuned for both.

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

Toolchain: AGP 8.5.2, Kotlin 2.0.21, Gradle 8.9, JDK 17, compileSdk 34, minSdk 24.

Optional build properties (all safe to leave out):

```bash
-PaiProxyUrl=…          # Ask AI through your Worker: users just sign in with Google
-PgoogleWebClientId=…   # the Web OAuth client ID every Google sign-in needs
-PgeminiApiKey=…        # alternative: call Gemini straight from the app
-PgeminiModel=…         # optional model override, default gemini-2.5-flash
-PtelemetryUrl=…        # sends anonymous usage counts to the admin dashboard
```

## Project layout

```
app/src/main/java/com/advanceai/calculator/
├── engine/          pure Kotlin math: lexer, parser, evaluator, complex,
│                    matrix, calculus, units, formatter (no Android imports)
├── data/            SharedPreferences store for settings, variables, history,
│                    the user's own API key and the anonymous usage counters
├── ai/              Google sign-in (Credential Manager) and the Gemini client
├── ui/              Compose UI: view model, keypads, sheets, theme
│   ├── components/  keypad grid and display panel
│   └── screens/     pad, graph, solver, matrix, units, stats, Ask AI, everyday tools
└── MainActivity.kt
app/src/main/assets/ai/  the Ask AI web page (HTML + Puter.js glue, used only
                         when no Gemini key is configured)
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
python3 tools/make_icons.py     # regenerate the launcher icons
python3 tools/reference.py      # print expected values for the tests
```

## Continuous integration

`.github/workflows/android.yml` runs on every push: it executes the unit tests,
builds the debug and release APKs, uploads them as artifacts, and refreshes the
`latest-build` release. If a build or test fails, the job posts the compiler
errors and failing assertions to a `CI failure` issue so the run can be fixed
from the report alone.

## License

Mozilla Public License 2.0 — see [LICENSE](LICENSE).
