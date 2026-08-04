package com.nlda.retrieval;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class JdbcSchemaMetadataExtractor {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "pg_catalog", "sys");

    private final DataSource dataSource;

    public JdbcSchemaMetadataExtractor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SchemaMetadataSnapshot extract() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, MutableTable> tables = loadTables(metaData);
            loadColumns(metaData, tables);
            loadPrimaryKeys(metaData, tables);
            loadForeignKeys(metaData, tables);
            List<SchemaTableMetadata> tableMetadata = tables.values().stream()
                    .map(MutableTable::toMetadata)
                    .sorted(Comparator.comparing(SchemaTableMetadata::name))
                    .toList();
            return new SchemaMetadataSnapshot(tableMetadata);
        } catch (SQLException ex) {
            throw new SchemaMetadataException("Unable to extract schema metadata.", ex);
        }
    }

    private Map<String, MutableTable> loadTables(DatabaseMetaData metaData) throws SQLException {
        Map<String, MutableTable> tables = new LinkedHashMap<>();
        try (ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String schema = normalize(nullable(resultSet.getString("TABLE_SCHEM")));
                String table = normalize(resultSet.getString("TABLE_NAME"));
                if (table.isBlank() || SYSTEM_SCHEMAS.contains(schema)) {
                    continue;
                }
                tables.put(table, new MutableTable(table));
            }
        }
        return tables;
    }

    private void loadColumns(DatabaseMetaData metaData, Map<String, MutableTable> tables) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(null, null, "%", "%")) {
            while (resultSet.next()) {
                String schema = normalize(nullable(resultSet.getString("TABLE_SCHEM")));
                String table = normalize(resultSet.getString("TABLE_NAME"));
                MutableTable mutableTable = tables.get(table);
                if (mutableTable == null || SYSTEM_SCHEMAS.contains(schema)) {
                    continue;
                }
                String column = normalize(resultSet.getString("COLUMN_NAME"));
                String type = resultSet.getString("TYPE_NAME");
                boolean nullable = resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                int ordinal = resultSet.getInt("ORDINAL_POSITION");
                mutableTable.columns.add(new SchemaColumnMetadata(column, type, nullable, ordinal));
            }
        }
    }

    private void loadPrimaryKeys(DatabaseMetaData metaData, Map<String, MutableTable> tables) throws SQLException {
        for (MutableTable table : tables.values()) {
            try (ResultSet resultSet = metaData.getPrimaryKeys(null, null, table.name)) {
                while (resultSet.next()) {
                    table.primaryKeys.add(normalize(resultSet.getString("COLUMN_NAME")));
                }
            }
        }
    }

    private void loadForeignKeys(DatabaseMetaData metaData, Map<String, MutableTable> tables) throws SQLException {
        for (MutableTable table : tables.values()) {
            try (ResultSet resultSet = metaData.getImportedKeys(null, null, table.name)) {
                while (resultSet.next()) {
                    table.foreignKeys.add(new SchemaForeignKeyMetadata(
                            normalize(resultSet.getString("FKCOLUMN_NAME")),
                            normalize(resultSet.getString("PKTABLE_NAME")),
                            normalize(resultSet.getString("PKCOLUMN_NAME"))
                    ));
                }
            }
        }
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return nullable(value).toLowerCase(Locale.ROOT).strip();
    }

    private static class MutableTable {
        private final String name;
        private final List<SchemaColumnMetadata> columns = new ArrayList<>();
        private final Set<String> primaryKeys = new LinkedHashSet<>();
        private final List<SchemaForeignKeyMetadata> foreignKeys = new ArrayList<>();

        private MutableTable(String name) {
            this.name = name;
        }

        private SchemaTableMetadata toMetadata() {
            return new SchemaTableMetadata(
                    name,
                    columns.stream().sorted(Comparator.comparingInt(SchemaColumnMetadata::ordinal)).toList(),
                    primaryKeys,
                    foreignKeys.stream()
                            .sorted(Comparator.comparing(SchemaForeignKeyMetadata::columnName))
                            .toList()
            );
        }
    }
}
