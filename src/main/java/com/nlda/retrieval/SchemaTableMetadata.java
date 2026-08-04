package com.nlda.retrieval;

import java.util.List;
import java.util.Set;

public record SchemaTableMetadata(
        String name,
        List<SchemaColumnMetadata> columns,
        Set<String> primaryKeys,
        List<SchemaForeignKeyMetadata> foreignKeys
) {
}
