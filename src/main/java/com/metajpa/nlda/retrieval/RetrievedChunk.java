package com.metajpa.nlda.retrieval;

import java.util.Set;

public record RetrievedChunk(
        String id,
        String text,
        double score,
        Set<String> schemaRefs
) {
    public RetrievedChunk withScore(double score) {
        return new RetrievedChunk(id, text, score, schemaRefs);
    }
}
