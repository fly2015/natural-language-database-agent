package com.nlda.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private final ObjectMapper objectMapper;

    public AuditLogger() {
        this.objectMapper = new ObjectMapper();
    }

    public AuditEvent start(String traceId, String question) {
        return new AuditEvent(traceId, sanitize(question));
    }

    public void record(AuditEvent event) {
        try {
            log.info("auditEvent={}", objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            log.warn("auditEventSerializationFailed traceId={} message={}", event.getTraceId(), ex.getMessage());
        }
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
}
