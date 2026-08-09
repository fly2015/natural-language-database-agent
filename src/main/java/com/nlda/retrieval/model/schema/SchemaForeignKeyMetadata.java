package com.nlda.retrieval.model.schema;

public record SchemaForeignKeyMetadata(
        String columnName,
        String referencedTable,
        String referencedColumn
) {
}


