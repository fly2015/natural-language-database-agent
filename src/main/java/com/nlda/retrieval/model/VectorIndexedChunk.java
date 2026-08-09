package com.nlda.retrieval.model;

public record VectorIndexedChunk(
        RetrievedChunk chunk,
        String fingerprint,
        String contentHash,
        String embeddingModel,
        float[] embedding
) {
}
