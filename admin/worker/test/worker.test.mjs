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
let ddlRuns = 0;

class Stmt {
  constructor(sql, args) { this.sql = sql; this.args = args || []; }
  bind(...args) { return new Stmt(this.sql, args); }
  async run() {
    this.guard();
    if (this.sql.startsWith("INSERT INTO about")) {
      aboutRow = { json: this.args[1], updated: this.args[2] };
    }
    return { success: true };
  }
  async first() {
    this.guard();
    if (this.sql.includes("COUNT(*) AS total FROM installs")) return { total: installs.size };
    if (this.sql.includes("FROM about")) {
      return aboutRow ? { json: aboutRow.json, updated: aboutRow.updated } : null;
    }
    return {};
  }
  async all() { this.guard(); return { results: [] }; }
  guard() {
    const needs = /events|installs|about/.test(this.sql) && !/CREATE (TABLE|INDEX)/.test(this.sql);
    if (!needs) return;
    for (const table of ["events", "installs", "about"]) {
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
const env = { DB, ADMIN_TOKEN: "localdev" };
const worker = (await import("./module-under-test.mjs")).default;

let failures = 0;
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
      tables.has("events") && tables.has("installs") && tables.has("about"),
      [...tables].join());
check("health lists secrets", body.secrets && body.secrets.ADMIN_TOKEN === true);
check("health hides no values", JSON.stringify(body).indexOf("localdev") === -1);
// The endpoints map doubles as "which version is deployed?" — /v1/about only
// exists in the round that added the editable About page, so seeing it here
// means the paste took.
check(
  "health lists the about endpoint",
  typeof body.endpoints?.about === "string" && body.endpoints.about.includes("/v1/about"),
  String(body.endpoints?.about)
);

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

// stats guard
res = await worker.fetch(new Request("https://x.workers.dev/v1/stats?days=7"), env);
check("stats needs the token", res.status === 401);
res = await worker.fetch(new Request("https://x.workers.dev/v1/stats?days=7&token=localdev"), env);
body = await res.json();
check("stats answers with the token", res.status === 200 && body.days === 7, JSON.stringify(body).slice(0, 80));

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
check("changing about needs the token", res.status === 401, String(res.status));

res = await worker.fetch(new Request("https://x.workers.dev/v1/about?token=localdev", {
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
check("about saves with the token", res.status === 200 && body.source === "saved",
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

// dashboard page
res = await worker.fetch(new Request("https://x.workers.dev/"), env);
const html = await res.text();
check("dashboard served", res.status === 200 && html.includes("<html>ok</html>"));

// the privacy policy page the Play listing points at
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
const brokenEnv = { ADMIN_TOKEN: "localdev" };
res = await worker.fetch(new Request("https://x.workers.dev/health"), brokenEnv);
body = await res.json();
check("health fails without a binding", res.status === 503 && body.ok === false, JSON.stringify(body).slice(0, 90));

console.log(failures === 0 ? "\nALL CHECKS PASSED" : "\nFAILURES: " + failures);
process.exit(failures === 0 ? 0 : 1);
