package com.nlda.retrieval.model.schema;

public record SchemaColumnMetadata(
        String name,
        String typeName,
        boolean nullable,
        int ordinal
) {
}


