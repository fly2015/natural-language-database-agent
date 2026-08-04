package com.nlda.retrieval;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class PgVectorReadySchemaChunkRepository implements SchemaChunkRepository {

    private final AtomicReference<IndexedSchemaChunks> current = new AtomicReference<>();

    @Override
    public Optional<IndexedSchemaChunks> current() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public void replace(IndexedSchemaChunks chunks) {
        current.set(chunks);
    }

    @Override
    public List<RetrievedChunk> fallbackChunks() {
        return current()
                .map(IndexedSchemaChunks::chunks)
                .orElse(List.of());
    }
}
