/**
 * AI Calculator API + Telegram-only admin. D1 binding: DB.
 * Secrets: TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID (your positive private user ID).
 * Register the webhook with tools/setup_telegram.py after deploying.
 * App routes remain public: events, About GET, privacy, optional AI solve.
 * Web login/stats/editing are retired, even with old public-mode settings.
 */

const EVENT_KINDS = ["app_open", "calc", "ai"];
const MAX_COUNT = 100_000;
const ID_PATTERN = /^[A-Za-z0-9_-]{8,64}$/;

/**
 * The tables this Worker needs. Kept in step with schema.sql (the test runner
 * compares the two), and created on demand: deploying from the Cloudflare
 * dashboard does not give you a way to run a whole .sql file, so the Worker
 * brings its own schema and runs it the first time it touches a fresh database.
 * Every statement is IF NOT EXISTS, so it is a no-op afterwards.
 */
const SCHEMA_SQL = [
  `CREATE TABLE IF NOT EXISTS events (
     id      TEXT    NOT NULL,
     day     TEXT    NOT NULL,
     kind    TEXT    NOT NULL,
     count   INTEGER NOT NULL,
     version TEXT,
     sdk     INTEGER,
     ts      INTEGER NOT NULL
   )`,
  `CREATE INDEX IF NOT EXISTS idx_events_day ON events (day)`,
  `CREATE INDEX IF NOT EXISTS idx_events_kind_day ON events (kind, day)`,
  `CREATE TABLE IF NOT EXISTS installs (
     id        TEXT PRIMARY KEY,
     first_day TEXT NOT NULL,
     version   TEXT,
     sdk       INTEGER
   )`,
  `CREATE INDEX IF NOT EXISTS idx_installs_day ON installs (first_day)`,
  `CREATE TABLE IF NOT EXISTS about (
     id      TEXT PRIMARY KEY,
     json    TEXT    NOT NULL,
     updated INTEGER NOT NULL
   )`,
  `CREATE TABLE IF NOT EXISTS settings (
     key   TEXT PRIMARY KEY,
     value TEXT NOT NULL
   )`,
];

let schemaPromise = null;

/** Creates the tables once per isolate; later calls reuse the same promise. */
function ensureSchema(env) {
  if (!schemaPromise) {
    schemaPromise = env.DB.batch(SCHEMA_SQL.map((sql) => env.DB.prepare(sql)))
      .then(() => true)
      .catch((error) => {
        schemaPromise = null; // let the next request try again
        throw error;
      });
  }
  return schemaPromise;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    // The dashboard is a static page on GitHub Pages (admin/web); this Worker
    // only answers its questions. Everything below is that API.
    if (url.pathname === "/telegram/webhook") return handleTelegram(request, env);
    if (["/v1/login", "/v1/stats", "/v1/admin-mode"].includes(url.pathname)) {
      return json({ error: "Web admin removed. Use the Telegram bot." }, 410);
    }
    if (url.pathname === "/v1/events") {
      return handleEvents(request, env);
    }
    if (url.pathname === "/v1/solve") {
      return handleSolve(request, env);
    }
    if (url.pathname === "/v1/stats") {
      return json({ error: "Use Telegram" }, 410);
    }
    if (url.pathname === "/v1/about") {
      return handleAbout(request, env, url);
    }
    if (url.pathname === "/privacy" || url.pathname === "/privacy.html") {
      return new Response(PRIVACY_HTML, {
        headers: { "content-type": "text/html; charset=utf-8" },
      });
    }
    if (url.pathname === "/health") {
      return handleHealth(env);
    }
    if (
      url.pathname === "/" ||
      url.pathname === "/index.html" ||
      url.pathname === "/dashboard" ||
      url.pathname === "/admin"
    ) {
      // No web admin: direct visitors to Telegram.
      return json({ service: "AI Calculator", admin: "Telegram only" });
    }
    return json({ error: "not found" }, 404);
  },
};

function corsHeaders() {
  return {
    // No credentials mode: the sign-in header carries the session, so `*` is
    // enough and a strict allow-list is not needed. Cookies are never read on
    // a cross-site request.
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, PUT, GET, OPTIONS",
    "access-control-allow-headers": "content-type, authorization",
    "access-control-max-age": "600",
  };
}

function json(value, status = 200, extra = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...corsHeaders(),
      ...extra,
    },
  });
}

async function handleEvents(request, env) {
  if (request.method !== "POST") return json({ error: "use POST" }, 405);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid json" }, 400);
  }

  const id = String(body.id ?? "");
  if (!ID_PATTERN.test(id)) return json({ error: "invalid id" }, 400);

  const day = /^\d{4}-\d{2}-\d{2}$/.test(String(body.day ?? ""))
    ? body.day
    : new Date().toISOString().slice(0, 10);
  const version = String(body.v ?? "").slice(0, 24);
  const sdk = Number.isFinite(Number(body.sdk)) ? Number(body.sdk) : 0;
  const events = body.events ?? {};

  const rows = [];
  for (const kind of EVENT_KINDS) {
    const count = Math.floor(Number(events[kind] ?? 0));
    if (count > 0) rows.push([id, day, kind, Math.min(count, MAX_COUNT)]);
  }
  if (rows.length === 0) return json({ ok: true, stored: 0 });

  const now = Date.now();
  const statements = rows.map(([installId, eventDay, kind, count]) =>
    env.DB.prepare(
      "INSERT INTO events (id, day, kind, count, version, sdk, ts) VALUES (?, ?, ?, ?, ?, ?, ?)"
    ).bind(installId, eventDay, kind, count, version, sdk, now)
  );
  // The first time an install is seen is what "installs" means on the dashboard.
  statements.push(
    env.DB.prepare(
      "INSERT OR IGNORE INTO installs (id, first_day, version, sdk) VALUES (?, ?, ?, ?)"
    ).bind(id, day, version, sdk)
  );

  try {
    await ensureSchema(env);
    await env.DB.batch(statements);
  } catch (error) {
    return json({ error: "database error", detail: String(error) }, 500);
  }
  return json({ ok: true, stored: rows.length });
}

/* ---------------------------------------------------------------------------- *
 * Ask AI (OAuth in, answer out)
 *
 * The app sends the user's Google ID token; this Worker checks the signature
 * against Google's published keys, and only then calls Gemini with the key that
 * lives here as a secret. Users never see or need an API key: they tap
 * "Continue with Google" and ask away.
 * ---------------------------------------------------------------------------- */

const GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
const GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const GOOGLE_ISSUERS = ["accounts.google.com", "https://accounts.google.com"];
const DEFAULT_MODEL = "gemini-2.5-flash";

let certsCache = { keys: null, at: 0 };
let certsFetch = null;

function base64UrlToBytes(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function base64UrlToString(value) {
  return new TextDecoder().decode(base64UrlToBytes(value));
}

async function googleKeys() {
  const fresh = Date.now() - certsCache.at < 30 * 60 * 1000;
  if (certsCache.keys && fresh) return certsCache.keys;
  if (!certsFetch) {
    certsFetch = fetch(GOOGLE_CERTS_URL)
      .then((response) => response.json())
      .then((data) => {
        certsCache = { keys: data.keys ?? [], at: Date.now() };
        certsFetch = null;
        return certsCache.keys;
      })
      .catch((error) => {
        certsFetch = null;
        throw error;
      });
  }
  return certsFetch;
}

/** Returns the token's claims when it really is a Google token for this app. */
async function verifyGoogleIdToken(token, audience) {
  const parts = String(token ?? "").split(".");
  if (parts.length !== 3) return null;

  let header;
  let claims;
  try {
    header = JSON.parse(base64UrlToString(parts[0]));
    claims = JSON.parse(base64UrlToString(parts[1]));
  } catch {
    return null;
  }
  if (header.alg !== "RS256") return null;

  const now = Math.floor(Date.now() / 1000);
  if (typeof claims.exp !== "number" || claims.exp < now) return null;
  if (claims.aud !== audience) return null;
  if (!GOOGLE_ISSUERS.includes(claims.iss)) return null;

  const keys = await googleKeys();
  const jwk = keys.find((key) => key.kid === header.kid);
  if (!jwk) return null;

  const key = await crypto.subtle.importKey(
    "jwk",
    { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: "RS256", ext: true },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const signed = new TextEncoder().encode(parts[0] + "." + parts[1]);
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    base64UrlToBytes(parts[2]),
    signed
  );
  return ok ? claims : null;
}

function solverPrompt() {
  return [
    "You are the solver inside a maths calculator app.",
    "Solve only the question contained in the file the user uploads.",
    "Reply with the answer first, then the steps, using short lines and plain",
    "text maths (^ for powers, sqrt() for roots, * for multiplication).",
    'If the upload does not contain a maths question, reply exactly:',
    '"I could not find a maths question in this file." Never invent a question,',
    "and never solve anything that is not in the file.",
  ].join(" ");
}

async function handleSolve(request, env) {
  if (request.method !== "POST") return json({ error: "use POST" }, 405);

  if (!env.GEMINI_API_KEY) {
    return json({ error: "This worker has no GEMINI_API_KEY secret yet." }, 503);
  }

  const header = request.headers.get("authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";

  let user = null;
  if (env.GOOGLE_WEB_CLIENT_ID) {
    try {
      user = await verifyGoogleIdToken(token, env.GOOGLE_WEB_CLIENT_ID);
    } catch (error) {
      return json({ error: "Could not check the sign-in: " + String(error) }, 502);
    }
    if (!user) {
      return json({ error: "Sign in with Google to use Ask AI." }, 401);
    }
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid json" }, 400);
  }

  const attachment = body.attachment ?? null;
  if (!attachment || typeof attachment.data !== "string" || attachment.data.length === 0) {
    return json({ error: "No file was uploaded." }, 400);
  }
  if (attachment.data.length > 22_000_000) {
    return json({ error: "That file is larger than 15 MB. Try a screenshot instead." }, 413);
  }

  const model = /^[a-zA-Z0-9._-]{1,60}$/.test(String(body.model ?? ""))
    ? body.model
    : DEFAULT_MODEL;

  const parts = [{ text: solverPrompt() }];
  const question = String(body.question ?? "").slice(0, 2000).trim();
  if (question) parts.push({ text: "The user also added: " + question });
  parts.push({
    inlineData: {
      // `||`, not `??`: an empty string is not a mime type either, and the
      // model answers 400 for one.
      mimeType: String(attachment.mimeType || "application/octet-stream").slice(0, 100),
      data: attachment.data,
    },
  });

  const payload = {
    contents: [{ role: "user", parts }],
    generationConfig: { temperature: 0.2, maxOutputTokens: 4096 },
  };

  let response;
  try {
    response = await fetch(
      GEMINI_ENDPOINT + "/" + encodeURIComponent(model) + ":generateContent",
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-goog-api-key": env.GEMINI_API_KEY,
        },
        body: JSON.stringify(payload),
      }
    );
  } catch (error) {
    return json({ error: "Could not reach Gemini: " + String(error) }, 502);
  }

  const text = await response.text();
  if (!response.ok) {
    let message = "The model could not answer that (HTTP " + response.status + ").";
    try {
      const parsed = JSON.parse(text);
      if (parsed?.error?.message) message = parsed.error.message;
    } catch {
      /* keep the generic message */
    }
    if (response.status === 429) message = "Too many questions just now. Try again shortly.";
    return json({ error: message }, response.status);
  }

  let answer = "";
  let blocked = "";
  try {
    const parsed = JSON.parse(text);
    blocked = parsed?.promptFeedback?.blockReason ?? "";
    const pieces = parsed?.candidates?.[0]?.content?.parts ?? [];
    answer = pieces.map((piece) => piece.text ?? "").join("").trim();
    if (!answer && parsed?.candidates?.[0]?.finishReason) {
      blocked = blocked || parsed.candidates[0].finishReason;
    }
  } catch {
    return json({ error: "Gemini sent something unreadable." }, 502);
  }

  if (!answer) {
    return json(
      { error: blocked ? "That upload was blocked (" + blocked + ")." : "No answer came back." },
      502
    );
  }

  return json({ answer, by: user ? user.email ?? user.sub ?? "" : "" });
}

/**
 * A setup checklist you can open in a browser: says whether the database is
 * reachable and which secrets are configured, without revealing any of them.
 */
async function handleHealth(env) {
  let database = "ok";
  let schema = "ready";
  try {
    await ensureSchema(env); // a fresh database gets its tables right here
    await env.DB.prepare("SELECT COUNT(*) AS n FROM installs").first();
  } catch (error) {
    database = "error: " + String(error);
    schema = "could not be created — is the D1 database bound as DB?";
  }
  const body = {
    ok: database === "ok",
    service: "AI Calculator stats",
    database,
    schema,
    admin: { method: "Telegram private chat", configured: Boolean(env.TELEGRAM_BOT_TOKEN && /^[1-9]\d*$/.test(String(env.TELEGRAM_CHAT_ID || ""))) },
    endpoints: {
      about: "GET /v1/about (public, read only)",
      events: "POST /v1/events",
      privacy: "GET /privacy",
      telegram: "POST /telegram/webhook (authenticated)",
    },
    note: "Admin management is available only through the configured Telegram private chat.",
  };
  return json(body, database === "ok" ? 200 : 503);
}

/** The aggregation itself, for GET /v1/stats. */
async function collectStats(env, days) {
  const since = new Date(Date.now() - (days - 1) * 86_400_000).toISOString().slice(0, 10);
  const today = new Date().toISOString().slice(0, 10);
  const weekAgo = new Date(Date.now() - 6 * 86_400_000).toISOString().slice(0, 10);

  await ensureSchema(env);
  const [totals, series, active, installsByDay, versions] = await env.DB.batch([
    env.DB.prepare(
      "SELECT kind, SUM(count) AS total FROM events WHERE day >= ? GROUP BY kind"
    ).bind(since),
    env.DB.prepare(
      "SELECT day, kind, SUM(count) AS total FROM events WHERE day >= ? GROUP BY day, kind ORDER BY day"
    ).bind(since),
    env.DB.prepare(
      `SELECT
         COUNT(DISTINCT CASE WHEN day = ? THEN id END) AS dau,
         COUNT(DISTINCT CASE WHEN day >= ? THEN id END) AS wau,
         COUNT(DISTINCT id) AS mau
       FROM events WHERE day >= ?`
    ).bind(today, weekAgo, since),
    env.DB.prepare(
      "SELECT first_day AS day, COUNT(*) AS total FROM installs WHERE first_day >= ? GROUP BY first_day ORDER BY first_day"
    ).bind(since),
    env.DB.prepare(
      "SELECT version, COUNT(*) AS total FROM installs GROUP BY version ORDER BY total DESC LIMIT 8"
    ),
  ]);

  const installTotals = await env.DB.prepare("SELECT COUNT(*) AS total FROM installs").first();

  return {
    generatedAt: new Date().toISOString(),
    days,
    totals: Object.fromEntries(
      (totals.results ?? []).map((row) => [row.kind, Number(row.total ?? 0)])
    ),
    active: {
      dau: Number(active.results?.[0]?.dau ?? 0),
      wau: Number(active.results?.[0]?.wau ?? 0),
      mau: Number(active.results?.[0]?.mau ?? 0),
      installs: Number(installTotals?.total ?? 0),
    },
    series: series.results ?? [],
    installs: installsByDay.results ?? [],
    versions: versions.results ?? [],
  };
}

/**
 * The About page shown inside the app.
 *
 * `GET /v1/about` is public — the app reads it without signing in, because it is
 * only the app's own description — and `PUT /v1/about` needs the admin session,
 * which is what the dashboard on GitHub Pages sends. Whatever is stored here appears in the app
 * within a tap of the About screen's Refresh, so the text can be changed without
 * shipping a new APK.
 */
const ABOUT_DEFAULT = {
  title: "AI Calculator",
  tagline: "A calculator that does the maths a phone calculator usually cannot.",
  // Written for the person using the app, not for a developer: no framework
  // names, no unit ids, no "engine" — just what it does and what stays private.
  body: [
    "Graph, solve equations, work with matrices, convert units and run " +
      "statistics — plus everyday calculators for GST, tax, EMI, BMI and dates, " +
      "all in one place.",
    "Every answer is worked out on your phone, so the calculator keeps working " +
      "with no connection, and nothing about a calculation leaves the device.",
    "Ask AI is there when you want something explained step by step, using your " +
      "own free key. There are no accounts and no sign-in.",
  ],
  links: [],
  contact: "",
  // The donate button. Editable from the dashboard like everything else here,
  // so the UPI id can change without shipping a new APK. `enabled: false`
  // removes the button entirely.
  donate: {
    enabled: true,
    upi: "vikashraoa2343-1@oksbi",
    name: "AI Calculator",
  },
  updated: 0,
};

const ABOUT_LIMITS = { title: 80, tagline: 200, body: 12, paragraph: 900, links: 8 };
// A UPI id is `handle@provider`: letters, digits, dots, dashes and underscores,
// then @, then the bank's own short name.
const UPI_PATTERN = /^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.]{1,32}$/;
const ABOUT_LINK_LABEL = 60;

/** Trims a value to a string of at most [max] characters. */
function cleanText(value, max) {
  return String(value ?? "").trim().slice(0, max);
}

/** Reads the request body as JSON, or null when it is not an object. */
async function readJson(request) {
  try {
    const value = await request.json();
    return value && typeof value === "object" ? value : null;
  } catch (error) {
    return null;
  }
}

/** Reads the stored About page, or the built-in one when nothing is saved. */
async function readAbout(env) {
  await ensureSchema(env);
  const stored = await env.DB.prepare("SELECT json, updated FROM about WHERE id = ?")
    .bind("about")
    .first();
  if (!stored || !stored.json) return { ...ABOUT_DEFAULT, source: "default" };
  try {
    // Defaults underneath, the saved copy on top: a page stored before the
    // donate block existed still answers with one, instead of `undefined`.
    return {
      ...ABOUT_DEFAULT,
      ...JSON.parse(stored.json),
      updated: Number(stored.updated || 0),
      source: "saved",
    };
  } catch (error) {
    // A row that cannot be read is not worth breaking the page over.
    return { ...ABOUT_DEFAULT, source: "default" };
  }
}

/**
 * Checks a page and saves it. Shared by the JSON route and by the plain form,
 * so both can do exactly as much as the other — no more, no less.
 */
async function saveAbout(env, body) {
  const paragraphs = Array.isArray(body.body)
    ? body.body
        .map((line) => cleanText(line, ABOUT_LIMITS.paragraph))
        .filter((line) => line.length > 0)
        .slice(0, ABOUT_LIMITS.body)
    : ABOUT_DEFAULT.body;

  const links = Array.isArray(body.links)
    ? body.links
        .map((link) => ({
          label: cleanText(link && link.label, ABOUT_LINK_LABEL),
          url: cleanText(link && link.url, 300),
        }))
        // Only http(s): the app opens these, and a stray scheme would be a way
        // to make every user's phone do something unexpected.
        .filter((link) => link.label && /^https?:\/\//.test(link.url))
        .slice(0, ABOUT_LIMITS.links)
    : [];

  // Missing keys keep what was saved before, so a dashboard that has not been
  // reloaded yet cannot wipe the donate details by saving the text alone.
  const previous = await readAbout(env).catch(() => ABOUT_DEFAULT);
  const prevDonate = (previous && previous.donate) || ABOUT_DEFAULT.donate;
  const incoming = body.donate && typeof body.donate === "object" ? body.donate : prevDonate;
  const upi = cleanText(incoming && incoming.upi, 80);
  const donate = {
    enabled: Boolean(incoming && incoming.enabled) && UPI_PATTERN.test(upi),
    // An id that does not look like a UPI id is refused, not stored: a typo
    // here would send well-wishers' money nowhere.
    upi: UPI_PATTERN.test(upi) ? upi : prevDonate.upi,
    name: cleanText(incoming && incoming.name, 60) || prevDonate.name,
  };

  const page = {
    title: cleanText(body.title, ABOUT_LIMITS.title) || ABOUT_DEFAULT.title,
    tagline: cleanText(body.tagline, ABOUT_LIMITS.tagline) || ABOUT_DEFAULT.tagline,
    body: paragraphs.length > 0 ? paragraphs : ABOUT_DEFAULT.body,
    links,
    contact: cleanText(body.contact, 120),
    donate,
  };
  const updated = Date.now();

  await ensureSchema(env);
  await env.DB.prepare(
    "INSERT INTO about (id, json, updated) VALUES (?, ?, ?) " +
      "ON CONFLICT(id) DO UPDATE SET json = excluded.json, updated = excluded.updated"
  )
    .bind("about", JSON.stringify(page), updated)
    .run();

  return { page, updated };
}

async function handleAbout(request, env, url) {
  let current;
  try {
    current = await readAbout(env);
  } catch (error) {
    return json({ error: "database error", detail: String(error) }, 500);
  }

  if (request.method === "GET") return json(current);

  return json({ error: "Web editing removed. Use Telegram." }, 405);
}

/**
 * The privacy policy. Google Play requires a reachable URL for an app that
 * sends anything off the device, and the Worker is already deployed, so
 * https://<your-worker>.workers.dev/privacy is that URL. Edit CONTACT below to
 * the support email you use on the Play listing.
 */
const CONTACT = "the support email on the app's Google Play listing";

const PRIVACY_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI Calculator — privacy policy</title>
<style>
  :root { color-scheme: light dark }
  body { margin: 0 auto; max-width: 720px; padding: 32px 20px 64px;
         font: 16px/1.65 -apple-system, "Segoe UI", Roboto, sans-serif;
         background: #0d0f1a; color: #e7e9f3 }
  @media (prefers-color-scheme: light) { body { background: #f5f6fb; color: #171a2b } }
  h1 { font-size: 30px; margin: 0 0 4px }
  h2 { font-size: 19px; margin: 30px 0 6px }
  .sub { opacity: .75; margin: 0 0 26px }
  code { background: rgba(140,130,255,.16); padding: 1px 6px; border-radius: 5px }
  ul { padding-left: 22px }
</style>
</head>
<body>
<h1>AI Calculator — privacy policy</h1>
<p class="sub">Last updated: 11 October 2026</p>

<p>AI Calculator is an offline calculator for Android. It has no accounts and no
sign-in, and every calculation happens on the device. This page explains the
only three things that can ever leave it, and how to switch them off.</p>

<h2>1. Anonymous usage counts (optional, on by default)</h2>
<p>The app can send anonymous counts — how many times it was opened, how many
calculations were made and how many AI questions were answered — together with
the date, the app version and the Android API level. Each install is identified
by a random string created on the device; it is not an account and cannot be
traced back to you.</p>
<ul>
  <li><b>Never sent:</b> the expressions you type, your results, your history,
      files, photos, contacts, location or any device identifier.</li>
  <li><b>Switch:</b> turn the counters off in the app under
      <code>☰ → Anonymous usage stats</code>. Nothing is sent while it is off,
      and anything still waiting is deleted.</li>
  <li><b>Why:</b> the totals tell the developer which features are used, so the
      app can be improved.</li>
</ul>

<h2>2. Ads (the free version is paid for by a banner)</h2>
<p>Some screens show one banner ad at the bottom, served by Google AdMob. When an
ad is requested, Google's Mobile Ads SDK may read your device's <b>advertising
ID</b> — the resettable identifier Android gives apps for advertising — and send
it to Google so an ad can be chosen and measured. That is Google's processing,
covered by Google's own policies:
<a href="https://policies.google.com/technologies/ads">how Google uses
information from apps that use its services</a>.</p>
<ul>
  <li><b>Never sent to advertisers:</b> the expressions you type, your results,
      your history, your variables, your files and your photos. The ad code runs
      beside the calculator, never inside it.</li>
  <li><b>Your choice in the EEA, the UK and Switzerland:</b> the first launch
      shows a consent form from Google that asks whether personalised ads are
      allowed, and no ad is requested before it is answered. Change that answer
      any time under <code>☰ → Privacy settings</code> (the row appears in the
      menu wherever Google requires one).</li>
  <li><b>Elsewhere:</b> Android's own ad settings
      (<code>Settings → Privacy → Ads</code>) reset or delete the advertising ID
      and turn off personalisation for every app on the phone.</li>
  <li><b>Offline:</b> with no connection there is no ad either — the banner is
      simply not drawn, and the calculator works exactly as before.</li>
</ul>

<h2>3. Ask AI (optional, only when you use it)</h2>
<p>Ask AI is off until you paste your own API key into
<code>☰ → Ask AI → ⚙</code>. The app sends your questions to the AI service that
key belongs to — <b>Groq</b> by default, and OpenRouter, Cerebras or another
service if you pick one — and never through this site. The key is stored on your
device only. When you send a question, the text you typed and any image or file
you attached are sent to that service to be answered, under that service's own
terms and privacy policy. The chat has no memory: each question is sent on its
own, and nothing is stored on a server by this app.</p>
<ul>
  <li><b>Camera or files:</b> used only for the question you are sending at that
      moment.</li>
  <li><b>Voice:</b> the microphone button hands your speech to the phone's own
      speech recogniser, which turns it into text. The app records nothing and
      keeps no audio.</li>
  <li><b>Delete:</b> remove the key in the app, and nothing more can be sent.</li>
</ul>

<h2>4. Everything else stays on the phone</h2>
<p>Your history, variables, memory and settings are stored in the app's private
storage on your device. They are not uploaded anywhere. Uninstalling the app
removes them.</p>

<h2>5. Children</h2>
<p>The app is a general-purpose calculator, not directed at children. It does not
knowingly collect personal information from anyone, of any age.</p>

<h2>6. Contact</h2>
<p>Questions about this policy, or a request to have anonymous counts deleted:
write to ${CONTACT}.</p>
</body>
</html>
`;

// Webhook authentication derives from the existing bot secret; no third secret
// is required. The local registration script uses exactly the same derivation.
async function telegramSecret(token) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("telegram-webhook:" + token));
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
}

async function handleTelegram(request, env) {
  if (request.method !== "POST") return json({ error: "use POST" }, 405);
  const chatId = String(env.TELEGRAM_CHAT_ID || "");
  if (!env.TELEGRAM_BOT_TOKEN || !/^[1-9]\d*$/.test(chatId)) {
    return json({ error: "Telegram private-chat settings missing" }, 503);
  }
  const expected = await telegramSecret(env.TELEGRAM_BOT_TOKEN);
  const supplied = request.headers.get("x-telegram-bot-api-secret-token") || "";
  let mismatch = supplied.length ^ expected.length;
  for (let i = 0; i < expected.length; i++) mismatch |= expected.charCodeAt(i) ^ (supplied.charCodeAt(i) || 0);
  if (mismatch) return json({ error: "unauthorised" }, 403);
  const update = await readJson(request);
  if (!update) return json({ error: "invalid update" }, 400);
  const message = update.message;
  // Private messages only: group membership is not admin authorization.
  if (!message || message.chat?.type !== "private" ||
      String(message.chat.id) !== chatId || String(message.from?.id) !== chatId ||
      message.from?.is_bot || typeof message.text !== "string") return json({ ok: true });
  let reply;
  try {
    reply = await telegramCommand(env, message.text);
  } catch (error) {
    reply = "Could not complete the command. Check the D1 binding and retry.";
  }
  try {
    // Split long About content into Telegram-safe plain-text messages.
    for (let offset = 0; offset < reply.length; offset += 3500) {
      const response = await fetch("https://api.telegram.org/bot" + env.TELEGRAM_BOT_TOKEN + "/sendMessage", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ chat_id: chatId, text: reply.slice(offset, offset + 3500) }),
      });
      if (!response.ok || !(await response.json()).ok) throw new Error("Telegram delivery failed");
    }
  } catch (error) {
    return json({ error: "Telegram delivery failed; update may be retried" }, 502);
  }
  return json({ ok: true });
}

async function telegramCommand(env, text) {
  const match = /^(\/\w+)(?:@[\w]+)?(?:\s+([\s\S]*))?$/.exec(text.trim());
  const command = match?.[1] || "/help";
  const value = (match?.[2] || "").trim();
  if (command === "/stats") {
    if (value && !/^(?:[1-9]\d{0,2})$/.test(value)) return "Usage: /stats 30 (1–365 days)";
    const days = Number(value || 30);
    if (days > 365) return "Usage: /stats 30 (1–365 days)";
    const stats = await collectStats(env, days);
    return `Stats: ${days} days\nInstalls: ${stats.active.installs}\nDaily active: ${stats.active.dau}\nWeekly active: ${stats.active.wau}\nPeriod active: ${stats.active.mau}\nApp opens: ${stats.totals.app_open || 0}\nCalculations: ${stats.totals.calc || 0}\nAI: ${stats.totals.ai || 0}`;
  }
  if (["/about", "/settitle", "/settagline", "/setabout", "/setcontact", "/donation", "/setupi", "/setdonorname"].includes(command)) {
    const page = await readAbout(env);
    if (command === "/about") return JSON.stringify(page, null, 2);
    if (command === "/donation" && !value) return JSON.stringify(page.donate, null, 2);
    if (!value) return "Provide a value after the command. Use /help.";
    if (command === "/donation") {
      if (!["on", "off"].includes(value)) return "Usage: /donation on OR /donation off";
      page.donate = { ...page.donate, enabled: value === "on" };
    } else if (command === "/setupi") {
      if (!UPI_PATTERN.test(value)) return "Invalid UPI ID. Nothing changed.";
      page.donate = { ...page.donate, upi: value };
    } else if (command === "/setdonorname") page.donate = { ...page.donate, name: value };
    else if (command === "/setabout") page.body = value.split(/\n\s*\n/);
    else page[{ "/settitle": "title", "/settagline": "tagline", "/setcontact": "contact" }[command]] = value;
    await saveAbout(env, page);
    return "Saved on the Worker. Only app versions that fetch /v1/about receive these changes.";
  }
  return "Admin commands:\n/stats [days]\n/about\n/settitle text\n/settagline text\n/setabout text (blank lines separate paragraphs)\n/setcontact text\n/donation [on|off]\n/setupi handle@bank\n/setdonorname name\n/help";
}
