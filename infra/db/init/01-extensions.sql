-- =============================================================================
-- SentinelOps incident-memory store — bootstrap.
-- Runs automatically on first container init (empty data dir).
--
-- NOTE: this only enables the extension. The application-managed schema
-- (incidents table, embedding columns, indexes) is created and versioned by
-- Flyway migrations inside the agent service in Phase 5 — kept here as a stub
-- so the DB is queryable from day one and the extension is guaranteed present.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- Sanity marker so `\dx` / smoke tests can confirm bootstrap ran.
CREATE TABLE IF NOT EXISTS sentinelops_bootstrap (
    id          smallint PRIMARY KEY DEFAULT 1,
    bootstrapped_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO sentinelops_bootstrap (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
