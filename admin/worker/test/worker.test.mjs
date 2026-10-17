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
const sent = [];
globalThis.fetch = async (url, options) => {
  if (!url.startsWith("https://api.telegram.org/bot")) throw new Error("unexpected network");
  sent.push(JSON.parse(options.body));
  return new Response(JSON.stringify({ ok: true }));
};
const env = { DB, TELEGRAM_CHAT_ID: "12345", TELEGRAM_BOT_TOKEN: "test-token" };
const worker = (await import("./module-under-test.mjs")).default;
let failures = 0;
function check(name, ok) { console.log((ok ? "PASS " : "FAIL ") + name); if (!ok) failures++; }
const secret = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",
  new TextEncoder().encode("telegram-webhook:" + env.TELEGRAM_BOT_TOKEN))),
  b => b.toString(16).padStart(2, "0")).join("");
async function command(text, opts = {}) {
  return worker.fetch(new Request("https://x.workers.dev/telegram/webhook", {
    method: "POST", headers: { "content-type": "application/json",
      "x-telegram-bot-api-secret-token": opts.secret ?? secret },
    body: JSON.stringify({ update_id: 1, message: {
      chat: { id: opts.chat ?? 12345, type: opts.type ?? "private" },
      from: { id: opts.user ?? 12345 }, text,
    } }),
  }), opts.env ?? env);
}
let res = await worker.fetch(new Request("https://x.workers.dev/health"), env);
let body = await res.json();
check("health ready", body.ok && body.admin.configured);
check("health does not expose credentials", !JSON.stringify(body).includes("test-token") && !JSON.stringify(body).includes("12345"));
for (const path of ["/v1/login", "/v1/stats", "/v1/admin-mode"]) {
  res = await worker.fetch(new Request("https://x.workers.dev" + path), { ...env, ADMIN_PUBLIC_ENABLED: "true" });
  check("retired " + path, res.status === 410);
}
res = await worker.fetch(new Request("https://x.workers.dev/v1/about", { method: "PUT", body: "{}" }), env);
check("web edits blocked", res.status === 405);
res = await command("/help", { secret: "wrong" });
check("forged webhook denied", res.status === 403 && sent.length === 0);
for (const opts of [{ chat: 999 }, { user: 999 }, { type: "group" }]) {
  await command("/settitle Hacked", opts);
}
check("other chats, users and groups ignored", sent.length === 0);
res = await command("/help", { env: { DB } });
check("missing config fails closed", res.status === 503);
res = await command("/help");
check("help delivered", res.status === 200 && sent.at(-1).text.includes("/stats"));
await command("/stats 7");
check("stats delivered", sent.at(-1).text.includes("Stats: 7 days"));
await command("/stats 999");
check("stats days validated", sent.at(-1).text.includes("Usage:"));
for (const text of ["/settitle My Calculator", "/settagline Fast maths", "/setabout First paragraph.\n\nSecond paragraph.", "/setcontact help@example.com", "/setupi someone@okbank", "/setdonorname Someone", "/donation off"]) {
  await command(text);
  check(text.split(" ")[0] + " saved", sent.at(-1).text.startsWith("Saved"));
}
res = await worker.fetch(new Request("https://x.workers.dev/v1/about"), env);
body = await res.json();
check("app reads Telegram changes", body.title === "My Calculator" && body.body.length === 2 && body.tagline === "Fast maths");
check("donation edits persist", body.donate.upi === "someone@okbank" && body.donate.name === "Someone" && !body.donate.enabled);
await command("/setupi not-valid");
check("bad UPI rejected", sent.at(-1).text.startsWith("Invalid"));
await command("/donation on");
await command("/donation");
check("donation enabled", JSON.parse(sent.at(-1).text).enabled);
await command("/about");
check("about delivered", sent.at(-1).text.includes("My Calculator"));
res = await worker.fetch(new Request("https://x.workers.dev/v1/events", {
  method: "POST", body: JSON.stringify({ id: "abc12345def", events: { calc: 5, junk: 9 } }),
}), env);
check("app events still accepted", res.status === 200 && (await res.json()).stored === 1);
res = await worker.fetch(new Request("https://x.workers.dev/privacy"), env);
check("privacy still available", res.status === 200 && (await res.text()).includes("privacy"));
res = await worker.fetch(new Request("https://x.workers.dev/"), env);
check("root no longer redirects to web", res.status === 200 && !res.headers.get("location"));
globalThis.fetch = async () => { throw new Error("delivery failed"); };
res = await command("/help");
check("delivery failure requests retry", res.status === 502);
console.log(failures ? "FAILURES: " + failures : "ALL CHECKS PASSED");
process.exit(failures ? 1 : 0);
