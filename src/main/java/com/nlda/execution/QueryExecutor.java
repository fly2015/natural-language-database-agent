package com.nlda.execution;

import com.nlda.format.TableResult;
import com.nlda.guardrail.SqlExecutionRejectedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final String database;

    public QueryExecutor(
            @Qualifier("appJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("appDataSourceProperties") DataSourceProperties dataSourceProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.setQueryTimeout(10);
        this.database = describe(dataSourceProperties.getUrl());
    }

    public TableResult execute(String approvedSql) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(approvedSql);
        } catch (DataAccessException ex) {
            throw new SqlExecutionRejectedException("The approved SQL could not be executed safely.");
        }
        List<String> columns = rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.getFirst().keySet());
        List<Map<String, Object>> orderedRows = rows.stream()
                .map(LinkedHashMap::new)
                .map(row -> (Map<String, Object>) row)
                .toList();
        return new TableResult(columns, orderedRows);
    }

    public String database() {
        return database;
    }

    private String describe(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "app";
        }
        int credentialMarker = jdbcUrl.indexOf('@');
        String sanitized = credentialMarker >= 0 ? jdbcUrl.substring(credentialMarker + 1) : jdbcUrl;
        int params = sanitized.indexOf('?');
        return params >= 0 ? sanitized.substring(0, params) : sanitized;
    }
}
