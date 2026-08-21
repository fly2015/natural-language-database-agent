DELETE FROM retrieval_chunk_embedding current_row
USING retrieval_chunk_embedding newer_row
WHERE current_row.datasource_id = newer_row.datasource_id
  AND current_row.schema_fingerprint = newer_row.schema_fingerprint
  AND current_row.chunk_id = newer_row.chunk_id
  AND (
      current_row.updated_at < newer_row.updated_at
      OR (
          current_row.updated_at = newer_row.updated_at
          AND current_row.id < newer_row.id
      )
  );

ALTER TABLE retrieval_chunk_embedding
    DROP CONSTRAINT IF EXISTS uq_retrieval_chunk_embedding;

ALTER TABLE retrieval_chunk_embedding
    ADD CONSTRAINT uq_retrieval_chunk_embedding UNIQUE (
        datasource_id,
        schema_fingerprint,
        chunk_id
    );
