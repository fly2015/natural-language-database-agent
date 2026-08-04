package com.nlda.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

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
