/**
 * Runs the Worker against a stub D1 — no Cloudflare account needed:
 *
 *     node admin/worker/test/worker.test.mjs
 *
 * tools/run_worker_tests.sh rebuilds module-under-test.mjs (the Worker with the
 * dashboard HTML replaced by a marker) and then runs this file.
 */

// --- a D1 stand-in that starts *empty* -------------------------------------- //
// It refuses queries until the Worker has created its tables, which is exactly
// what a freshly created database in the Cloudflare dashboard does.
const rows = [];
const installs = new Set();
const tables = new Set();
let aboutRow = null;   // { json, updated } once the dashboard saves a page
let settings = {};     // key → value (the session-signing key lives here)
let ddlRuns = 0;

class Stmt {
  constructor(sql, args) { this.sql = sql; this.args = args || []; }
  bind(...args) { return new Stmt(this.sql, args); }
  async run() {
    this.guard();
    if (this.sql.startsWith("INSERT INTO about")) {
      aboutRow = { json: this.args[1], updated: this.args[2] };
    }
    if (this.sql.startsWith("INSERT OR IGNORE INTO settings")) {
      if (!(this.args[0] in settings)) settings[this.args[0]] = this.args[1];
    }
    return { success: true };
  }
  async first() {
    this.guard();
    if (this.sql.includes("COUNT(*) AS total FROM installs")) return { total: installs.size };
    if (this.sql.includes("FROM about")) {
      return aboutRow ? { json: aboutRow.json, updated: aboutRow.updated } : null;
    }
    if (this.sql.includes("FROM settings")) {
      const key = this.args[0];
      return key in settings ? { value: settings[key] } : null;
    }
    return {};
  }
  async all() { this.guard(); return { results: [] }; }
  guard() {
    const needs = /events|installs|about/.test(this.sql) && !/CREATE (TABLE|INDEX)/.test(this.sql);
    if (!needs) return;
    for (const table of ["events", "installs", "about", "settings"]) {
      if (this.sql.includes(table) && !tables.has(table)) {
        throw new Error("no such table: " + table);
      }
    }
  }
}

const DB = {
  prepare: (sql) => new Stmt(sql),
  batch: async (stmts) => {
    const ddl = stmts.length > 0 && stmts.every((s) => /^CREATE (TABLE|INDEX)/.test(s.sql));
    if (ddl) {
      ddlRuns += 1;
      for (const s of stmts) {
        const name = /CREATE TABLE IF NOT EXISTS (\w+)/.exec(s.sql);
        if (name) tables.add(name[1]);
      }
      return stmts.map(() => ({ results: [] }));
    }
    for (const s of stmts) {
      s.guard();
      if (s.sql.startsWith("INSERT INTO events")) rows.push(s.args);
      if (s.sql.startsWith("INSERT OR IGNORE INTO installs")) installs.add(s.args[0]);
    }
    return stmts.map(() => ({ results: [] }));
  },
};
globalThis.fetch = async () => { throw new Error("no network in the harness"); };
// No ADMIN_EMAIL / ADMIN_PASSWORD_HASH: the built-in account is under test.
const env = { DB };
const worker = (await import("./module-under-test.mjs")).default;

let failures = 0;
let adminToken = "";

/**
 * A request that carries the signed-in session once there is one — as
 * `Authorization: Bearer …`. That header is the only way in: the dashboard is
 * a static page on GitHub Pages, so no cookie travels with it.
 */
function req(path, init = {}) {
  const headers = Object.assign({}, init.headers || {});
  if (adminToken) headers.authorization = "Bearer " + adminToken;
  return new Request("https://x.workers.dev" + path, Object.assign({}, init, { headers }));
}

/** POST /v1/login, the way admin/web does it. */
async function signIn(email, password) {
  const res = await worker.fetch(new Request("https://x.workers.dev/v1/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password }),
  }), env);
  const data = await res.json().catch(() => ({}));
  return { res, data };
}

function check(name, condition, extra = "") {
  console.log((condition ? "PASS " : "FAIL ") + name + (condition ? "" : "  " + extra));
  if (!condition) failures += 1;
}

// health — on an empty database this is what creates the tables
let res = await worker.fetch(new Request("https://x.workers.dev/health"), env);
let body = await res.json();
check("health ok on an empty database", res.status === 200 && body.ok === true, JSON.stringify(body));
check("health reports the schema was created", body.schema === "ready", String(body.schema));
check("tables were created",
      tables.has("events") && tables.has("installs") && tables.has("about") && tables.has("settings"),
      [...tables].join());
check("health describes the sign-in", body.signIn &&
      body.signIn.method.includes("email and password") &&
      body.signIn.method.includes("Authorization") &&
      body.signIn.email === "rajkatrina90@gmail.com", JSON.stringify(body.signIn));
check("health says whether the built-in password is in use",
      body.signIn.customPassword === false, JSON.stringify(body.signIn));
check("health hides no values", JSON.stringify(body).indexOf("pbkdf2") === -1);
// The endpoints map doubles as "which version is deployed?" — /v1/about only
// exists in the round that added the editable About page, so seeing it here
// means the paste took.
check(
  "health lists the about endpoint",
  typeof body.endpoints?.about === "string" && body.endpoints.about.includes("/v1/about"),
  String(body.endpoints?.about)
);
check("health points at the dashboard on GitHub Pages",
      typeof body.endpoints?.dashboard === "string" && body.endpoints.dashboard.includes("github.io"),
      String(body.endpoints?.dashboard));

// the tables are only created once per isolate
res = await worker.fetch(new Request("https://x.workers.dev/health"), env);
check("schema runs once", ddlRuns === 1, "ddlRuns=" + ddlRuns);

// events
res = await worker.fetch(new Request("https://x.workers.dev/v1/events", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ id: "abc12345def", day: "2026-10-11", v: "1.0.0", sdk: 34,
                         events: { calc: 5, app_open: 1, junk: 9 } }),
}), env);
body = await res.json();
check("events accepted", res.status === 200 && body.stored === 2, JSON.stringify(body));
check("junk event dropped", rows.every((r) => r[2] !== "junk"));
check("install recorded", installs.size === 1);

// bad id
res = await worker.fetch(new Request("https://x.workers.dev/v1/events", {
  method: "POST", body: JSON.stringify({ id: "x", events: { calc: 1 } }),
}), env);
check("bad id rejected", res.status === 400);

// --- signing in -----------------------------------------------------------
res = await worker.fetch(new Request("https://x.workers.dev/v1/stats?days=7"), env);
check("stats needs a session", res.status === 401, String(res.status));
check("the 401 says how to get one", (await res.json()).login === "/v1/login");

let attempt = await signIn("rajkatrina90@gmail.com", "wrong password");
check("a wrong password is refused", attempt.res.status === 401, String(attempt.res.status));
check("the refusal does not say which part was wrong",
      attempt.data.error === "That email and password do not match." &&
      !JSON.stringify(attempt.data).includes("no such user"), JSON.stringify(attempt.data));

attempt = await signIn("someone@example.com", "admin123");
check("a wrong email is refused", attempt.res.status === 401, String(attempt.res.status));

attempt = await signIn("rajkatrina90@gmail.com", "admin123");
check("the right password signs in", attempt.res.status === 200, String(attempt.res.status));
check("it answers with a signed session",
      typeof attempt.data.token === "string" && attempt.data.token.includes("."),
      JSON.stringify(attempt.data).slice(0, 80));
check("the session expires in the future",
      Number(attempt.data.expiresAt) > Date.now(), String(attempt.data.expiresAt));
check("the sign-in sets no cookie", attempt.res.headers.get("set-cookie") === null,
      String(attempt.res.headers.get("set-cookie")));
check("the sign-in is not cached",
      (attempt.res.headers.get("cache-control") || "").includes("no-store"),
      String(attempt.res.headers.get("cache-control")));
adminToken = attempt.data.token;

res = await worker.fetch(req("/v1/stats?days=7"), env);
body = await res.json();
check("the session reads the stats", res.status === 200 && body.days === 7,
      JSON.stringify(body).slice(0, 80));

// garbage in the header is a refusal, not a crash
for (const [label, value] of [
  ["a made-up session", "Bearer not-a-session"],
  ["a session with a broken signature", "Bearer " + encodeURIComponent("cmFqQGV4YW1wbGUuY29tCjQxMDI0NDQ4MDAwMDA") + ".AAAA"],
  ["a malformed value", "Bearer %E0%A4%A.bad"],
]) {
  res = await worker.fetch(new Request("https://x.workers.dev/v1/stats?days=7", {
    headers: { authorization: value },
  }), env);
  check(label + " is refused", res.status === 401, String(res.status));
}

res = await worker.fetch(new Request("https://x.workers.dev/v1/login", { method: "GET" }), env);
check("the sign-in answers POST only", res.status === 405, String(res.status));

res = await worker.fetch(new Request("https://x.workers.dev/v1/stats?days=7", { method: "OPTIONS" }), env);
check("the preflight allows the Authorization header",
      (res.headers.get("access-control-allow-headers") || "").includes("authorization"),
      String(res.headers.get("access-control-allow-headers")));

// --- the Worker carries no pages any more ----------------------------------
for (const path of ["/login", "/stats", "/about/edit"]) {
  res = await worker.fetch(new Request("https://x.workers.dev" + path), env);
  check("no page at " + path, res.status === 404, String(res.status));
}

// an old bookmark still lands on the dashboard, wherever it was pointed
for (const path of ["/", "/dashboard", "/admin", "/index.html"]) {
  res = await worker.fetch(new Request("https://x.workers.dev" + path), env);
  const landing = res.headers.get("location") || "";
  check("the Worker forwards " + path + " to the dashboard",
        res.status === 302 &&
        landing.startsWith("https://vikkiroy899.github.io/m.calculator"), landing);
}

// the About page the app shows: public to read, admin-only to change
res = await worker.fetch(new Request("https://x.workers.dev/v1/about"), env);
body = await res.json();
check("about defaults are served", res.status === 200 && body.source === "default" &&
      typeof body.title === "string" && Array.isArray(body.body), JSON.stringify(body).slice(0, 90));
check("about default names the app", body.title === "AI Calculator", String(body.title));

res = await worker.fetch(new Request("https://x.workers.dev/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ title: "AI Calculator by Vikas" }),
}), env);
check("changing about needs the session", res.status === 401, String(res.status));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({
    title: "AI Calculator",
    tagline: "Fast maths",
    body: ["First line.", "  ", "Second line."],
    links: [
      { label: "Website", url: "https://example.com" },
      { label: "Bad", url: "javascript:alert(1)" },
      { label: "", url: "https://example.com" },
    ],
    contact: "help@example.com",
  }),
}), env);
body = await res.json();
check("about saves with the session", res.status === 200 && body.source === "saved",
      JSON.stringify(body).slice(0, 90));
check("blank paragraphs are dropped", body.body.length === 2, JSON.stringify(body.body));
check("non-http links are dropped", body.links.length === 1 &&
      body.links[0].url === "https://example.com", JSON.stringify(body.links));

res = await worker.fetch(new Request("https://x.workers.dev/v1/about"), env);
body = await res.json();
check("the saved page is what the app gets", body.source === "saved" &&
      body.tagline === "Fast maths" && body.contact === "help@example.com",
      JSON.stringify(body).slice(0, 120));

res = await worker.fetch(new Request("https://x.workers.dev/v1/about", { method: "DELETE" }), env);
check("about rejects other methods", res.status === 405, String(res.status));

// --- the donate block ------------------------------------------------------
res = await worker.fetch(new Request("https://x.workers.dev/v1/about"), env);
body = await res.json();
check("the about page carries the donate details", body.donate &&
      body.donate.upi === "vikashraoa2343-1@oksbi" &&
      typeof body.donate.enabled === "boolean",
      JSON.stringify(body.donate));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  // Saving the text alone, with no donate key at all, must not lose it.
  body: JSON.stringify({ title: "Kept", body: ["Still here."] }),
}), env);
body = await res.json();
check("saving without a donate key keeps the old one", body.donate &&
      body.donate.upi === "vikashraoa2343-1@oksbi", JSON.stringify(body.donate));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ title: "Kept", donate: { enabled: true, upi: "not-a-upi", name: "X" } }),
}), env);
body = await res.json();
check("a malformed UPI id is refused, not stored",
      body.donate.upi === "vikashraoa2343-1@oksbi" && body.donate.enabled === false,
      JSON.stringify(body.donate));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ title: "Kept", donate: { enabled: true, upi: "someone@okhdfcbank", name: "Someone" } }),
}), env);
body = await res.json();
check("a good UPI id is saved",
      body.donate.upi === "someone@okhdfcbank" && body.donate.enabled === true,
      JSON.stringify(body.donate));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ donate: { enabled: false, upi: "someone@okhdfcbank", name: "Someone" } }),
}), env);
body = await res.json();
check("the donate button can be switched off", body.donate.enabled === false,
      JSON.stringify(body.donate));

res = await worker.fetch(req("/v1/about", {
  method: "PUT",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ title: "AI Calculator", donate: { enabled: true, upi: "vikashraoa2343-1@oksbi", name: "AI Calculator" } }),
}), env);
body = await res.json();
check("and back on", body.donate.enabled === true && body.donate.upi === "vikashraoa2343-1@oksbi",
      JSON.stringify(body.donate));

// --- a wrong password cannot be guessed at speed ---------------------------
const attacker = { "cf-connecting-ip": "203.0.113.9", "content-type": "application/json" };
let last = null;
for (let i = 0; i < 9; i += 1) {
  last = await worker.fetch(new Request("https://x.workers.dev/v1/login", {
    method: "POST",
    headers: attacker,
    body: JSON.stringify({ email: "rajkatrina90@gmail.com", password: "guess-" + i }),
  }), env);
}
check("nine wrong passwords are refused with 429", last.status === 429, String(last.status));
const honest = await worker.fetch(new Request("https://x.workers.dev/v1/login", {
  method: "POST",
  headers: { "cf-connecting-ip": "198.51.100.2", "content-type": "application/json" },
  body: JSON.stringify({ email: "rajkatrina90@gmail.com", password: "admin123" }),
}), env);
check("another address is not punished for it", honest.status === 200, String(honest.status));

// privacy page
res = await worker.fetch(new Request("https://x.workers.dev/privacy"), env);
const privacy = await res.text();
check("privacy page served", res.status === 200 && privacy.includes("privacy"),
      String(res.status));
check("privacy page is html", (res.headers.get("content-type") || "").includes("text/html"));

// solve without a key
res = await worker.fetch(new Request("https://x.workers.dev/v1/solve", {
  method: "POST", headers: { authorization: "Bearer nope" },
  body: JSON.stringify({ attachment: { mimeType: "image/png", data: "AAAA" } }),
}), env);
check("solve reports the missing secret", res.status === 503, String(res.status));

// a Worker with no database bound at all must not pretend to be healthy
const brokenEnv = {};
res = await worker.fetch(new Request("https://x.workers.dev/health"), brokenEnv);
body = await res.json();
check("health fails without a binding", res.status === 503 && body.ok === false, JSON.stringify(body).slice(0, 90));

console.log(failures === 0 ? "\nALL CHECKS PASSED" : "\nFAILURES: " + failures);
process.exit(failures === 0 ? 0 : 1);
