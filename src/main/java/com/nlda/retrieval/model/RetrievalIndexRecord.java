package com.nlda.retrieval.model;

import java.time.LocalDateTime;

public record RetrievalIndexRecord(
        String chunkId,
        ChunkKind kind,
        String schemaFingerprint,
        String contentHash,
        String embeddingModel,
        boolean active,
        LocalDateTime indexedAt
) {
}
