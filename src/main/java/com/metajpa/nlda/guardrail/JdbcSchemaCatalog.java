package com.metajpa.nlda.guardrail;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JdbcSchemaCatalog implements SchemaCatalog {

    private final JdbcTemplate jdbcTemplate;
    private volatile Map<String, Set<String>> cachedTables;

    public JdbcSchemaCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tableExists(String tableName) {
        return tables().containsKey(normalize(tableName));
    }

    @Override
    public boolean columnExists(String tableName, String columnName) {
        return tables().getOrDefault(normalize(tableName), Set.of()).contains(normalize(columnName));
    }

    private Map<String, Set<String>> tables() {
        Map<String, Set<String>> local = cachedTables;
        if (local == null) {
            synchronized (this) {
                local = cachedTables;
                if (local == null) {
                    local = loadTables();
                    cachedTables = local;
                }
            }
        }
        return local;
    }

    private Map<String, Set<String>> loadTables() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA NOT IN ('INFORMATION_SCHEMA', 'PG_CATALOG')
                """);
        Map<String, Set<String>> tables = new ConcurrentHashMap<>();
        for (Map<String, Object> row : rows) {
            String table = normalize(String.valueOf(value(row, "table_name")));
            String column = normalize(String.valueOf(value(row, "column_name")));
            tables.compute(table, (ignored, columns) -> {
                if (columns == null) {
                    return Set.of(column);
                }
                return add(columns, column);
            });
        }
        return tables;
    }

    private Set<String> add(Set<String> columns, String column) {
        Set<String> mutable = new HashSet<>(columns);
        mutable.add(column);
        return Set.copyOf(mutable);
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).strip();
    }
}
