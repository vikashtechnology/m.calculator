-- Storage for the anonymous usage counters.
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
