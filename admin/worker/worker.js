/**
 * AI Calculator — usage stats API and admin dashboard.
 *
 * One Worker, no dependencies, no build step:
 *   POST /v1/events        the app posts anonymous counters here
 *   POST /v1/solve        Ask AI: verifies the user's Google sign-in and asks
 *                         Gemini with the key kept here, never in the app
 *   GET  /v1/stats        aggregated numbers for the dashboard (JSON)
 *   GET  /                the dashboard itself (asks for the admin token)
 *
 * Storage is a D1 database bound as DB (see wrangler.toml and schema.sql).
 * Only whitelisted counters are accepted, so nothing unexpected can be stored.
 */

const EVENT_KINDS = ["app_open", "calc", "ai"];
const MAX_COUNT = 100_000;
const ID_PATTERN = /^[A-Za-z0-9_-]{8,64}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (url.pathname === "/v1/events") {
      return handleEvents(request, env);
    }
    if (url.pathname === "/v1/solve") {
      return handleSolve(request, env);
    }
    if (url.pathname === "/v1/stats") {
      return handleStats(request, env, url);
    }
    if (url.pathname === "/" || url.pathname === "/index.html") {
      return new Response(DASHBOARD_HTML, {
        headers: { "content-type": "text/html; charset=utf-8" },
      });
    }
    if (url.pathname === "/health") {
      return json({ ok: true });
    }
    return json({ error: "not found" }, 404);
  },
};

function corsHeaders() {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, GET, OPTIONS",
    "access-control-allow-headers": "content-type",
  };
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...corsHeaders() },
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
      mimeType: String(attachment.mimeType ?? "application/octet-stream").slice(0, 100),
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

async function handleStats(request, env, url) {
  const token = url.searchParams.get("token") ?? request.headers.get("x-admin-token") ?? "";
  if (!env.ADMIN_TOKEN || token !== env.ADMIN_TOKEN) {
    return json({ error: "unauthorised" }, 401);
  }

  const days = Math.min(Math.max(Number(url.searchParams.get("days")) || 30, 1), 365);
  const since = new Date(Date.now() - (days - 1) * 86_400_000).toISOString().slice(0, 10);
  const today = new Date().toISOString().slice(0, 10);
  const weekAgo = new Date(Date.now() - 6 * 86_400_000).toISOString().slice(0, 10);

  try {
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

    return json({
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
    });
  } catch (error) {
    return json({ error: "database error", detail: String(error) }, 500);
  }
}

const DASHBOARD_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AI Calculator — usage</title>
<style>
  :root { color-scheme: dark; --bg:#141218; --card:#211f26; --line:#332f39;
          --text:#e7e0ec; --muted:#a49fac; --accent:#d0bcff; --bar:#b794f6; }
  * { box-sizing: border-box; }
  body { margin:0; padding:24px; background:var(--bg); color:var(--text);
         font:15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  h1 { font-size:20px; margin:0 0 4px; }
  .muted { color:var(--muted); font-size:13px; }
  .row { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }
  .cards { display:grid; gap:12px; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); margin:20px 0; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:14px; padding:14px 16px; }
  .card .k { font-size:26px; font-weight:600; }
  .card .t { color:var(--muted); font-size:13px; margin-top:2px; }
  .panel { background:var(--card); border:1px solid var(--line); border-radius:14px; padding:16px; margin-bottom:16px; }
  table { width:100%; border-collapse:collapse; font-size:14px; }
  th, td { text-align:left; padding:6px 8px; border-bottom:1px solid var(--line); }
  th { color:var(--muted); font-weight:500; }
  .bars { display:flex; align-items:flex-end; gap:3px; height:120px; margin-top:8px; }
  .bars div { flex:1; background:var(--bar); border-radius:3px 3px 0 0; min-height:2px; }
  input, button, select { font:inherit; padding:8px 10px; border-radius:10px;
        border:1px solid var(--line); background:#1b1920; color:var(--text); }
  button { background:var(--accent); color:#241a3d; font-weight:600; border:none; cursor:pointer; }
  .hidden { display:none; }
</style>
</head>
<body>
<h1>AI Calculator — usage</h1>
<div class="muted" id="sub">Anonymous counts from installed apps. Nothing personal is stored.</div>

<div class="panel" id="gate">
  <div class="row">
    <input type="password" id="token" placeholder="admin token" style="min-width:240px">
    <button id="load">Show the stats</button>
    <span class="muted">Set with <code>wrangler secret put ADMIN_TOKEN</code></span>
  </div>
</div>

<div id="dashboard" class="hidden">
  <div class="row">
    <select id="range">
      <option value="7">Last 7 days</option>
      <option value="30" selected>Last 30 days</option>
      <option value="90">Last 90 days</option>
    </select>
    <button id="refresh">Refresh</button>
    <span class="muted" id="stamp"></span>
  </div>

  <div class="cards" id="cards"></div>

  <div class="panel">
    <div class="muted">Calculations per day</div>
    <div class="bars" id="chart"></div>
  </div>

  <div class="panel">
    <table>
      <thead><tr><th>Day</th><th>App opens</th><th>Calculations</th><th>AI questions</th></tr></thead>
      <tbody id="table"></tbody>
    </table>
  </div>

  <div class="panel">
    <div class="muted">App versions (new installs)</div>
    <table><tbody id="versions"></tbody></table>
  </div>
</div>

<script>
(function () {
  var token = localStorage.getItem("mc_token") || "";
  function el(id) { return document.getElementById(id); }

  function load() {
    var days = el("range").value;
    var url = "/v1/stats?days=" + encodeURIComponent(days) + "&token=" + encodeURIComponent(token);
    el("sub").textContent = "Loading…";
    fetch(url).then(function (r) {
      if (r.status === 401) throw new Error("That token was not accepted.");
      if (!r.ok) throw new Error("The worker answered " + r.status + ".");
      return r.json();
    }).then(render).catch(function (error) {
      el("sub").textContent = error.message;
      el("gate").classList.remove("hidden");
      el("dashboard").classList.add("hidden");
    });
  }

  function render(data) {
    localStorage.setItem("mc_token", token);
    el("gate").classList.add("hidden");
    el("dashboard").classList.remove("hidden");
    el("sub").textContent = "Anonymous counts from installed apps. Nothing personal is stored.";
    el("stamp").textContent = "as of " + new Date(data.generatedAt).toLocaleString();

    var totals = data.totals || {};
    var active = data.active || {};
    var cards = [
      ["Calculations", totals.calc || 0],
      ["AI questions", totals.ai || 0],
      ["App opens", totals.app_open || 0],
      ["Installs (all time)", active.installs || 0],
      ["Active today", active.dau || 0],
      ["Active this week", active.wau || 0],
      ["Active in range", active.mau || 0],
    ];
    el("cards").innerHTML = cards.map(function (c) {
      return '<div class="card"><div class="k">' + Number(c[1]).toLocaleString() +
        '</div><div class="t">' + c[0] + "</div></div>";
    }).join("");

    var perDay = {};
    (data.series || []).forEach(function (row) {
      perDay[row.day] = perDay[row.day] || { app_open: 0, calc: 0, ai: 0 };
      perDay[row.day][row.kind] = Number(row.total || 0);
    });
    (data.installs || []).forEach(function (row) {
      perDay[row.day] = perDay[row.day] || { app_open: 0, calc: 0, ai: 0, installs: 0 };
      perDay[row.day].installs = Number(row.total || 0);
    });
    var days = Object.keys(perDay).sort();
    var peak = 1;
    days.forEach(function (d) { peak = Math.max(peak, perDay[d].calc || 0); });

    el("chart").innerHTML = days.map(function (d) {
      var value = perDay[d].calc || 0;
      var height = Math.max(2, Math.round((value / peak) * 110));
      return '<div style="height:' + height + 'px" title="' + d + ": " +
        value.toLocaleString() + ' calculations"></div>';
    }).join("") || '<div class="muted">No calculations recorded yet.</div>';

    el("table").innerHTML = days.slice().reverse().map(function (d) {
      var row = perDay[d];
      return "<tr><td>" + d + "</td><td>" + (row.app_open || 0).toLocaleString() +
        "</td><td>" + (row.calc || 0).toLocaleString() +
        "</td><td>" + (row.ai || 0).toLocaleString() + "</td></tr>";
    }).join("") || '<tr><td class="muted">Nothing yet.</td></tr>';

    el("versions").innerHTML = (data.versions || []).map(function (row) {
      return "<tr><td>" + (row.version || "unknown") + "</td><td>" +
        Number(row.total).toLocaleString() + "</td></tr>";
    }).join("") || '<tr><td class="muted">Nothing yet.</td></tr>';
  }

  el("load").addEventListener("click", function () {
    token = el("token").value.trim();
    load();
  });
  el("range").addEventListener("change", load);
  el("refresh").addEventListener("click", load);
  el("token").addEventListener("keydown", function (event) {
    if (event.key === "Enter") { token = el("token").value.trim(); load(); }
  });

  if (token) load();
})();
</script>
</html>`;
