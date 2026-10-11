# M Calculator

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

### Sign in with Google (optional)
Ask AI can start from the app's own **Continue with Google**. That account is the
same one Puter offers inside its sign-in window, so one Google account covers both:

* the app signs in with Google (Credential Manager) and hands the ID token to the
  Ask AI page, which remembers the account and, after the Puter sign-in, checks
  that both are the same address — the header chip shows `Google ✓` when linked
* the Google button only appears once an OAuth *Web* client ID is configured,
  because a client ID that does not match the signing certificate can only fail

To switch it on:

1. Register the signing certificate's SHA-1 (see `app/signing/fingerprints.txt`)
   for the package name `com.vikashtechnology.mcalculator` in the Google Cloud
   console → *APIs & Services* → *Credentials* → *Create OAuth client ID* → Android.
2. Create a second client ID of type **Web** and put it in the repository secret
   `GOOGLE_WEB_CLIENT_ID` (CI) or in `gradle.properties` as `googleWebClientId`.

Every build is signed with the key in `app/signing/`, so the fingerprint above
never changes and Android updates install straight over the previous version.

### Ask AI (optional, needs internet)
The hero card at the top of the pad opens **Ask AI**, which solves a question you
upload — a photo, a PDF or a text file — and shows the working. It is powered by
[Puter.js](https://developer.puter.com/), which supplies both the account and the
model, so the app needs no API keys and no backend of its own:

* sign in with a Puter account (or *Continue with Google* in the app header), or
  create a temporary one with *Continue as guest*
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

## Project layout

```
app/src/main/java/com/vikashtechnology/mcalculator/
├── engine/          pure Kotlin math: lexer, parser, evaluator, complex,
│                    matrix, calculus, units, formatter (no Android imports)
├── data/            SharedPreferences store for settings, variables, history
├── ui/              Compose UI: view model, keypads, sheets, theme
│   ├── components/  keypad grid and display panel
│   └── screens/     pad, graph, solver, matrix, units, stats
└── MainActivity.kt
app/src/main/assets/ai/  the Ask AI page (HTML + Puter.js glue, no build step)
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
