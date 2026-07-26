-- Incident-memory store. ${embeddingDimension} is a Flyway placeholder bound
-- from application.yml (spring.flyway.placeholders.embeddingDimension) so the
-- vector width follows the configured embedding model.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS incident (
    id                     text PRIMARY KEY,
    source                 text NOT NULL DEFAULT 'postmortem',   -- postmortem | sentinelops
    title                  text NOT NULL,
    occurred_on            date,
    affected_services      text[] NOT NULL DEFAULT '{}',
    service_types          text[] NOT NULL DEFAULT '{}',
    symptoms               text[] NOT NULL DEFAULT '{}',
    symptom_type           text,
    error_signatures       text[] NOT NULL DEFAULT '{}',
    error_pattern_category text,
    root_cause             text,
    fix                    text,
    source_url             text,
    embedding              vector(${embeddingDimension}) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now()
);

-- Approximate-nearest-neighbour index for the vector recall stage.
CREATE INDEX IF NOT EXISTS incident_embedding_idx
    ON incident USING hnsw (embedding vector_cosine_ops);

-- Structured-signature filters used by the hybrid re-ranker.
CREATE INDEX IF NOT EXISTS incident_symptom_type_idx ON incident (symptom_type);
CREATE INDEX IF NOT EXISTS incident_error_pattern_idx ON incident (error_pattern_category);
CREATE INDEX IF NOT EXISTS incident_source_idx ON incident (source);
