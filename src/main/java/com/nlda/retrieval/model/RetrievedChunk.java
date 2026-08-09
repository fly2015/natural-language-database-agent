package com.nlda.retrieval.model;

import java.util.Set;

public record RetrievedChunk(
        String id,
        String text,
        double score,
        Set<String> schemaRefs,
        ChunkKind kind,
        Set<String> aliases
) {
    public RetrievedChunk(String id, String text, double score, Set<String> schemaRefs) {
        this(id, text, score, schemaRefs, ChunkKind.SCHEMA, Set.of());
    }

    public static RetrievedChunk schema(String id, String text, double score, Set<String> schemaRefs) {
        return new RetrievedChunk(id, text, score, schemaRefs, ChunkKind.SCHEMA, Set.of());
    }

    public static RetrievedChunk businessRule(
            String id,
            String text,
            double score,
            Set<String> schemaRefs,
            Set<String> aliases
    ) {
        return new RetrievedChunk(id, text, score, schemaRefs, ChunkKind.BUSINESS_RULE, aliases);
    }

    public static RetrievedChunk joinPath(String id, String text, double score, Set<String> schemaRefs) {
        return new RetrievedChunk(id, text, score, schemaRefs, ChunkKind.JOIN_PATH, Set.of());
    }

    public RetrievedChunk withScore(double score) {
        return new RetrievedChunk(id, text, score, schemaRefs, kind, aliases);
    }
}
