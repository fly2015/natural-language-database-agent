package com.nlda.retrieval.model.schema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public record SchemaMetadataSnapshot(
        List<SchemaTableMetadata> tables
) {
    public SchemaMetadataSnapshot {
        tables = tables.stream()
                .sorted(Comparator.comparing(SchemaTableMetadata::name))
                .toList();
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonical().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    public boolean containsSchemaRef(String schemaRef) {
        String normalized = schemaRef == null ? "" : schemaRef.toLowerCase(Locale.ROOT).strip();
        if (normalized.isBlank()) {
            return false;
        }
        String[] parts = normalized.split("\\.");
        for (SchemaTableMetadata table : tables) {
            if (parts.length == 1 && table.name().equals(normalized)) {
                return true;
            }
            if (parts.length == 2 && table.name().equals(parts[0])
                    && table.columns().stream().anyMatch(column -> column.name().equals(parts[1]))) {
                return true;
            }
        }
        return false;
    }

    private String canonical() {
        StringBuilder builder = new StringBuilder();
        for (SchemaTableMetadata table : tables) {
            builder.append(table.name()).append('|');
            for (SchemaColumnMetadata column : table.columns()) {
                builder.append(column.name()).append(':')
                        .append(column.typeName()).append(':')
                        .append(column.nullable()).append(';');
            }
            builder.append("pk=").append(String.join(",", table.primaryKeys())).append('|');
            for (SchemaForeignKeyMetadata foreignKey : table.foreignKeys()) {
                builder.append(foreignKey.columnName()).append("->")
                        .append(foreignKey.referencedTable()).append('.')
                        .append(foreignKey.referencedColumn()).append(';');
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}

