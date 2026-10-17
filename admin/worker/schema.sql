-- Storage for the anonymous usage counters.
--
-- You only need this file when you deploy with wrangler. The Worker also
-- carries the same statements in SCHEMA_SQL and runs them the first time it
-- touches an empty database, which is what makes the dashboard-only setup work.
-- Keep the two in step: tools/run_worker_tests.sh compares them.
-- Run once per database:
--   npx wrangler d1 execute m_calculator_stats --remote --file=schema.sql

CREATE TABLE IF NOT EXISTS events (
  id      TEXT    NOT NULL,   -- random per-install id, never an account
  day     TEXT    NOT NULL,   -- YYYY-MM-DD (UTC)
  kind    TEXT    NOT NULL,   -- app_open | calc | ai
  count   INTEGER NOT NULL,
  version TEXT,
  sdk     INTEGER,
  ts      INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_day ON events (day);
CREATE INDEX IF NOT EXISTS idx_events_kind_day ON events (kind, day);

-- One row per install, written the first time that install reports anything.
CREATE TABLE IF NOT EXISTS installs (
  id        TEXT PRIMARY KEY,
  first_day TEXT NOT NULL,
  version   TEXT,
  sdk       INTEGER
);

CREATE INDEX IF NOT EXISTS idx_installs_day ON installs (first_day);

-- The About page the app shows, as one JSON row. The admin dashboard writes it
-- through PUT /v1/about, so the wording and links can change without shipping a
-- new APK. Empty means the Worker's built-in default text is served.
CREATE TABLE IF NOT EXISTS about (
  id      TEXT PRIMARY KEY,   -- always "about"
  json    TEXT NOT NULL,      -- { title, tagline, body[], links[], contact }
  updated INTEGER NOT NULL
);

-- One row per setting the Worker must remember between requests. Today that is
-- only the key that signs admin sessions, generated on first use so no secret
-- has to be configured by hand.
CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
