package com.nlda.guardrail;

import com.nlda.audit.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SqlGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardrailService.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final Pattern DENIED_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|MERGE|CALL|EXEC|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([a-zA-Z_][\\w.]*)\\s*(?:AS\\s+)?([a-zA-Z_]\\w*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_COLUMN_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_]\\w*)\\.([a-zA-Z_]\\w*)\\b");

    private final SchemaCatalog schemaCatalog;

    public SqlGuardrailService(SchemaCatalog schemaCatalog) {
        this.schemaCatalog = schemaCatalog;
    }

    public GuardrailResult validateAndSanitize(String sql) {
        long started = System.nanoTime();
        List<String> violations = new ArrayList<>();
        if (sql == null || sql.isBlank()) {
            log.warn("flowEvent=guardrail.validation.completed traceId={} status=DENY latencyMs={} sqlHash={} violationCount=1 reason=\"SQL is empty.\"",
                    AuditContext.traceId(), elapsedMs(started), sqlHash(sql));
            return GuardrailResult.deny(List.of("SQL is empty."));
        }

        String trimmed = sql.strip();
        String keywordSafeSql = removeSqlCommentsWithoutGap(trimmed);
        String uncommented = removeSqlComments(trimmed);
        String normalized = uncommented.strip().toUpperCase(Locale.ROOT);

        if (DENIED_KEYWORDS.matcher(keywordSafeSql).find()) {
            violations.add("SQL contains a mutation or destructive keyword.");
        }
        if (!normalized.startsWith("SELECT ")) {
            violations.add("Only SELECT statements are allowed.");
        }
        if (hasMultipleStatements(uncommented)) {
            violations.add("Only one SQL statement is allowed.");
        }
        if (normalized.contains("SELECT *")) {
            violations.add("SELECT star is not allowed in this phase.");
        }
        violations.addAll(validateSchemaReferences(uncommented));

        if (!violations.isEmpty()) {
            log.warn("flowEvent=guardrail.validation.completed traceId={} status=DENY latencyMs={} sqlHash={} violationCount={} reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(started), sqlHash(sql), violations.size(),
                    safe(String.join(" ", violations)));
            return GuardrailResult.deny(violations);
        }

        String limitedSql = stripTrailingSemicolon(uncommented);
        java.util.regex.Matcher limitMatcher = LIMIT_PATTERN.matcher(limitedSql);
        if (!limitMatcher.find()) {
            limitedSql = limitedSql + " LIMIT " + DEFAULT_LIMIT;
        } else {
            int limit = Integer.parseInt(limitMatcher.group(1));
            if (limit > MAX_LIMIT) {
                limitedSql = limitMatcher.replaceFirst("LIMIT " + MAX_LIMIT);
            }
        }
        log.info("flowEvent=guardrail.validation.completed traceId={} status=ALLOW latencyMs={} sqlHash={} violationCount=0",
                AuditContext.traceId(), elapsedMs(started), sqlHash(limitedSql));
        return new GuardrailResult(true, limitedSql, List.of());
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

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }

    private List<String> validateSchemaReferences(String sql) {
        List<String> violations = new ArrayList<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        java.util.regex.Matcher tableMatcher = TABLE_PATTERN.matcher(sql);
        while (tableMatcher.find()) {
            String rawTable = tableMatcher.group(1);
            String table = unqualifiedName(rawTable);
            if (!schemaCatalog.tableExists(table)) {
                violations.add("SQL references an unknown table: " + table + ".");
                continue;
            }
            aliases.put(table.toLowerCase(Locale.ROOT), table);
            String alias = tableMatcher.group(2);
            if (alias != null && !isReservedAlias(alias)) {
                aliases.put(alias.toLowerCase(Locale.ROOT), table);
            }
        }

        java.util.regex.Matcher columnMatcher = QUALIFIED_COLUMN_PATTERN.matcher(sql);
        while (columnMatcher.find()) {
            String qualifier = columnMatcher.group(1).toLowerCase(Locale.ROOT);
            String column = columnMatcher.group(2);
            String table = aliases.get(qualifier);
            if (table == null) {
                continue;
            }
            if (!schemaCatalog.columnExists(table, column)) {
                violations.add("SQL references an unknown column: " + qualifier + "." + column + ".");
            }
        }
        return violations.stream().distinct().toList();
    }

    private boolean hasMultipleStatements(String sql) {
        String withoutTrailing = stripTrailingSemicolon(sql);
        return withoutTrailing.contains(";");
    }

    private String stripTrailingSemicolon(String sql) {
        String value = sql.strip();
        while (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1).strip();
        }
        return value;
    }

    private String removeSqlComments(String sql) {
        String withoutLineComments = sql.replaceAll("(?m)--.*$", " ");
        return withoutLineComments.replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private String removeSqlCommentsWithoutGap(String sql) {
        String withoutLineComments = sql.replaceAll("(?m)--.*$", " ");
        return withoutLineComments.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private String unqualifiedName(String tableName) {
        int lastDot = tableName.lastIndexOf('.');
        if (lastDot >= 0) {
            return tableName.substring(lastDot + 1);
        }
        return tableName;
    }

    private boolean isReservedAlias(String alias) {
        return switch (alias.toUpperCase(Locale.ROOT)) {
            case "WHERE", "JOIN", "ON", "GROUP", "ORDER", "LIMIT", "LEFT", "RIGHT", "INNER", "OUTER", "FULL",
                    "CROSS" -> true;
            default -> false;
        };
    }
}
