package com.nlda.retrieval;

public record SchemaForeignKeyMetadata(
        String columnName,
        String referencedTable,
        String referencedColumn
) {
}
