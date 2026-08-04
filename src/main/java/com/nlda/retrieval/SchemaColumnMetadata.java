package com.nlda.retrieval;

public record SchemaColumnMetadata(
        String name,
        String typeName,
        boolean nullable,
        int ordinal
) {
}
