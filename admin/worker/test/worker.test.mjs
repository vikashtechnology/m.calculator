/**
 * Runs the Worker against a stub D1 — no Cloudflare account needed:
 *
 *     node admin/worker/test/worker.test.mjs
 *
 * tools/run_worker_tests.sh rebuilds module-under-test.mjs (the Worker with the
 * dashboard HTML replaced by a marker) and then runs this file.
 */

// Minimal D1 stand-in: records inserts and answers the aggregate queries.
const rows = [];
const installs = new Set();
class Stmt {
  constructor(sql, args) { this.sql = sql; this.args = args || []; }
  bind(...args) { return new Stmt(this.sql, args); }
  async run() { return { success: true }; }
  async first() {
    if (this.sql.includes("COUNT(*) AS total FROM installs")) return { total: installs.size };
    return {};
  }
  async all() { return { results: [] }; }
}
const DB = {
  prepare: (sql) => new Stmt(sql),
  batch: async (stmts) => {
    for (const s of stmts) {
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

// health
let res = await worker.fetch(new Request("https://x.workers.dev/health"), env);
let body = await res.json();
check("health ok", res.status === 200 && body.ok === true, JSON.stringify(body));
check("health lists secrets", body.secrets && body.secrets.ADMIN_TOKEN === true);
check("health hides no values", JSON.stringify(body).indexOf("localdev") === -1);

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

// dashboard page
res = await worker.fetch(new Request("https://x.workers.dev/"), env);
const html = await res.text();
check("dashboard served", res.status === 200 && html.includes("<html>ok</html>"));

// solve without a key
res = await worker.fetch(new Request("https://x.workers.dev/v1/solve", {
  method: "POST", headers: { authorization: "Bearer nope" },
  body: JSON.stringify({ attachment: { mimeType: "image/png", data: "AAAA" } }),
}), env);
check("solve reports the missing secret", res.status === 503, String(res.status));

console.log(failures === 0 ? "\nALL CHECKS PASSED" : "\nFAILURES: " + failures);
process.exit(failures === 0 ? 0 : 1);
