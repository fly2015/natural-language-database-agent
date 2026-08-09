package com.nlda.retrieval.model.schema;

import java.util.List;
import java.util.Set;

public record SchemaTableMetadata(
        String name,
        List<SchemaColumnMetadata> columns,
        Set<String> primaryKeys,
        List<SchemaForeignKeyMetadata> foreignKeys
) {
}


