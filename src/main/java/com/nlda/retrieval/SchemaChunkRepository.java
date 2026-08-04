package com.nlda.retrieval;

import java.util.List;
import java.util.Optional;

public interface SchemaChunkRepository {

    Optional<IndexedSchemaChunks> current();

    void replace(IndexedSchemaChunks chunks);

    List<RetrievedChunk> fallbackChunks();
}
