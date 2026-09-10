package com.nlda.execution;

import com.nlda.audit.AuditContext;
import com.nlda.format.TableResult;
import com.nlda.guardrail.SqlExecutionRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

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
        long started = System.nanoTime();
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(approvedSql);
        } catch (DataAccessException ex) {
            log.warn("flowEvent=database.execution.completed traceId={} status=REJECTED latencyMs={} database={} sqlHash={} reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(started), database, sqlHash(approvedSql),
                    "The approved SQL could not be executed safely.");
            throw new SqlExecutionRejectedException("The approved SQL could not be executed safely.");
        }
        List<String> columns = rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.getFirst().keySet());
        List<Map<String, Object>> orderedRows = rows.stream()
                .map(LinkedHashMap::new)
                .map(row -> (Map<String, Object>) row)
                .toList();
        TableResult result = new TableResult(columns, orderedRows);
        log.info("flowEvent=database.execution.completed traceId={} status=OK latencyMs={} database={} sqlHash={} rowCount={}",
                AuditContext.traceId(), elapsedMs(started), database, sqlHash(approvedSql), result.rows().size());
        return result;
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

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String sqlHash(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }
}
