package com.nlda.retrieval;

import java.util.List;

public record IndexedSchemaChunks(
        String fingerprint,
        List<RetrievedChunk> chunks
) {
}
