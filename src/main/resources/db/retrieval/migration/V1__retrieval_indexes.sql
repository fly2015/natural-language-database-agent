CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS retrieval_vocabulary (
    id BIGSERIAL PRIMARY KEY,
    datasource_id VARCHAR(120) NOT NULL,
    schema_fingerprint VARCHAR(128) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id VARCHAR(240) NOT NULL,
    term TEXT NOT NULL,
    normalized_term TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_retrieval_vocabulary UNIQUE (
        datasource_id,
        schema_fingerprint,
        source_type,
        source_id,
        normalized_term
    )
);

CREATE INDEX IF NOT EXISTS idx_retrieval_vocabulary_active
    ON retrieval_vocabulary (datasource_id, active, schema_fingerprint);

CREATE INDEX IF NOT EXISTS idx_retrieval_vocabulary_trgm
    ON retrieval_vocabulary USING gin (normalized_term gin_trgm_ops);

CREATE TABLE IF NOT EXISTS retrieval_chunk_embedding (
    id BIGSERIAL PRIMARY KEY,
    chunk_id VARCHAR(240) NOT NULL,
    datasource_id VARCHAR(120) NOT NULL,
    schema_fingerprint VARCHAR(128) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    text TEXT NOT NULL,
    schema_refs TEXT[] NOT NULL DEFAULT '{}',
    aliases TEXT[] NOT NULL DEFAULT '{}',
    content_hash VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(120) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    embedding vector(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_retrieval_chunk_embedding UNIQUE (
        datasource_id,
        schema_fingerprint,
        chunk_id,
        embedding_model
    )
);

CREATE INDEX IF NOT EXISTS idx_retrieval_chunk_embedding_active
    ON retrieval_chunk_embedding (datasource_id, active, schema_fingerprint, embedding_model);

CREATE INDEX IF NOT EXISTS idx_retrieval_chunk_embedding_vector
    ON retrieval_chunk_embedding USING hnsw (embedding vector_cosine_ops);
