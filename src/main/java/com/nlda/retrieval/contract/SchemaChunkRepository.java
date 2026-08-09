package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievedChunk;

import java.util.List;
import java.util.Optional;

public interface SchemaChunkRepository {

    Optional<IndexedSchemaChunks> current();

    void replace(IndexedSchemaChunks chunks);

    List<RetrievedChunk> fallbackChunks();
}


