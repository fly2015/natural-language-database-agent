package com.nlda.retrieval.model;

import java.util.List;

public record IndexedSchemaChunks(
        String fingerprint,
        List<RetrievedChunk> chunks
) {
}


