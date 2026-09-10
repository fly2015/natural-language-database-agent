package com.nlda.audit;

import com.nlda.format.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    public AuditLogger() {
    }

    public AuditEvent start(String traceId, String question) {
        return new AuditEvent(traceId, sanitize(question));
    }

    public void record(AuditEvent event) {
        Map<String, Object> response = event.getResponse();
        String sql = stringValue(response.get("sql"));
        log.info("auditEvent traceId={} status={} latencyMs={} question=\"{}\" sqlHash={} guardrailDecision={} rowCount={} reason=\"{}\"",
                event.getTraceId(), event.getStatus(), event.getDurationMs(), sanitize(event.getQuestion()),
                sqlHash(sql), guardrailDecision(event), rowCount(response.get("table")), sanitize(stringValue(response.get("reason"))));
    }

    public void record(String traceId, String question, String sql, String guardrailDecision, String outcome) {
        log.info("traceId={} guardrailDecision={} outcome={} question=\"{}\" sql=\"{}\"",
                traceId, guardrailDecision, outcome, sanitize(question), sanitize(sql));
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String guardrailDecision(AuditEvent event) {
        return event.getSteps().stream()
                .filter(step -> "guardrail.validate".equals(step.name()))
                .reduce((first, second) -> second)
                .map(step -> step.status() == null ? "SKIPPED" : step.status())
                .orElse("SKIPPED");
    }

    private int rowCount(Object value) {
        if (value instanceof TableResult table) {
            return table.rows().size();
        }
        if (value instanceof Map<?, ?> map && map.get("rowCount") instanceof Number number) {
            return number.intValue();
        }
        return 0;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
