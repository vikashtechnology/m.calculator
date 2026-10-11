/**
 * M Calculator — usage stats API and admin dashboard.
 *
 * One Worker, no dependencies, no build step:
 *   POST /v1/events        the app posts anonymous counters here
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
<title>M Calculator — usage</title>
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
<h1>M Calculator — usage</h1>
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
