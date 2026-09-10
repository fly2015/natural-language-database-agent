package com.nlda.audit;

import com.nlda.format.TableResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AuditLoggerTest {

    @Test
    void recordWritesCompactAuditSummaryWithoutStepPayloads(CapturedOutput output) {
        AuditLogger logger = new AuditLogger();
        AuditEvent event = logger.start("trace-1", "list 10 customers");
        event.step("retrieval.retrieve", "OK", 12, Map.of("question", "list 10 customers"),
                Map.of("snippets", List.of("table: customers; columns: id, name")));
        event.complete("OK", 34, Map.of(
                "status", "OK",
                "sql", "SELECT c.id AS customer_id FROM customers c LIMIT 10",
                "table", new TableResult(List.of("customer_id"), List.of(Map.of("customer_id", 1))),
                "reason", ""
        ));

        logger.record(event);

        assertThat(output.getOut()).contains("auditEvent traceId=trace-1 status=OK latencyMs=34");
        assertThat(output.getOut()).contains("question=\"list 10 customers\"");
        assertThat(output.getOut()).contains("rowCount=1");
        assertThat(output.getOut()).contains("sqlHash=");
        assertThat(output.getOut()).doesNotContain("retrieval.retrieve");
        assertThat(output.getOut()).doesNotContain("table: customers");
        assertThat(output.getOut()).doesNotContain("customer_id=1");
    }
}
