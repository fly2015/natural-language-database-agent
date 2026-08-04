package com.nlda.execution;

import com.nlda.format.TableResult;
import com.nlda.guardrail.SqlExecutionRejectedException;
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

    public QueryExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.setQueryTimeout(10);
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
}
