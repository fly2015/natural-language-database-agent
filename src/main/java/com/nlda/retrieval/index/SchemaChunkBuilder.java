package com.nlda.retrieval.index;

import com.nlda.retrieval.contract.BusinessRuleSource;
import com.nlda.retrieval.model.BusinessRule;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.schema.SchemaColumnMetadata;
import com.nlda.retrieval.model.schema.SchemaForeignKeyMetadata;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.model.schema.SchemaTableMetadata;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SchemaChunkBuilder {

    private final BusinessRuleSource businessRuleSource;

    public SchemaChunkBuilder(BusinessRuleSource businessRuleSource) {
        this.businessRuleSource = businessRuleSource;
    }

    public List<RetrievedChunk> build(SchemaMetadataSnapshot snapshot) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (SchemaTableMetadata table : snapshot.tables()) {
            chunks.add(tableChunk(table));
        }
        for (BusinessRule rule : businessRuleSource.rules()) {
            chunks.add(new RetrievedChunk(rule.id(), rule.text() + " Aliases: " + aliases(rule),
                    0.0, rule.schemaRefs()));
        }
        return List.copyOf(chunks);
    }

    private RetrievedChunk tableChunk(SchemaTableMetadata table) {
        Set<String> schemaRefs = new LinkedHashSet<>();
        schemaRefs.add(table.name());
        for (SchemaForeignKeyMetadata foreignKey : table.foreignKeys()) {
            schemaRefs.add(foreignKey.referencedTable());
        }
        String text = "table: " + table.name()
                + "; columns: " + columns(table.columns())
                + "; primary key: " + emptyDefault(table.primaryKeys(), "none")
                + "; foreign keys: " + foreignKeys(table.foreignKeys());
        return new RetrievedChunk("schema." + table.name(), text, 0.0, schemaRefs);
    }

    private String columns(List<SchemaColumnMetadata> columns) {
        List<String> parts = new ArrayList<>();
        for (SchemaColumnMetadata column : columns) {
            String nullable = column.nullable() ? "nullable" : "not null";
            parts.add(column.name() + " " + column.typeName() + " " + nullable);
        }
        return String.join(", ", parts);
    }

    private String foreignKeys(List<SchemaForeignKeyMetadata> foreignKeys) {
        if (foreignKeys.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (SchemaForeignKeyMetadata foreignKey : foreignKeys) {
            parts.add(foreignKey.columnName() + " -> " + foreignKey.referencedTable() + "."
                    + foreignKey.referencedColumn());
        }
        return String.join(", ", parts);
    }

    private String aliases(BusinessRule rule) {
        return rule.aliases().stream()
                .sorted()
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String emptyDefault(Set<String> values, String defaultValue) {
        if (values.isEmpty()) {
            return defaultValue;
        }
        return String.join(", ", values);
    }
}


