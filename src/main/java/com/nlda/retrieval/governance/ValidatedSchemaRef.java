package com.nlda.retrieval.governance;

public record ValidatedSchemaRef(
        String schemaRef,
        boolean valid,
        String schemaFingerprint
) {
}
