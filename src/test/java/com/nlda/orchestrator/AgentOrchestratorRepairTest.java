package com.nlda.orchestrator;

import com.nlda.api.QueryResponse;
import com.nlda.audit.AuditLogger;
import com.nlda.execution.QueryExecutor;
import com.nlda.format.ResultFormatter;
import com.nlda.format.TableResult;
import com.nlda.generation.GeneratedSql;
import com.nlda.generation.SqlGenerationProperties;
import com.nlda.generation.SqlGenerationService;
import com.nlda.guardrail.GuardrailResult;
import com.nlda.guardrail.SqlGuardrailService;
import com.nlda.retrieval.RetrievalContext;
import com.nlda.retrieval.RetrievalMode;
import com.nlda.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorRepairTest {

    @Test
    void invalidTableResponseTriggersBoundedRepairAndExecutesOnlyApprovedSql() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        SqlGenerationService generationService = mock(SqlGenerationService.class);
        SqlGuardrailService guardrailService = mock(SqlGuardrailService.class);
        QueryExecutor queryExecutor = mock(QueryExecutor.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        SqlGenerationProperties properties = new SqlGenerationProperties();
        properties.setRepairRetries(1);

        RetrievalContext context = new RetrievalContext(true, 0.9, List.of("table: customers"), "",
                "NONE", RetrievalMode.NORMALIZED, List.of());
        String invalidSql = "SELECT i.id FROM invoices i LIMIT 100";
        String repairedSql = "SELECT c.id AS customer_id FROM customers c LIMIT 100";

        when(retrievalService.retrieve("List customers")).thenReturn(context);
        when(generationService.generate("List customers", context))
                .thenReturn(GeneratedSql.ok(invalidSql, List.of()));
        when(guardrailService.validateAndSanitize(invalidSql))
                .thenReturn(GuardrailResult.deny(List.of("SQL references an unknown table: invoices.")));
        when(generationService.repair(eq("List customers"), eq(context), eq(invalidSql), any()))
                .thenReturn(GeneratedSql.ok(repairedSql, List.of("Repaired table reference.")));
        when(guardrailService.validateAndSanitize(repairedSql))
                .thenReturn(new GuardrailResult(true, repairedSql, List.of()));
        when(queryExecutor.execute(repairedSql)).thenReturn(table());

        AgentOrchestrator orchestrator = new AgentOrchestrator(retrievalService, generationService, guardrailService,
                queryExecutor, new ResultFormatter(), auditLogger, properties);

        QueryResponse response = orchestrator.answer("List customers");

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.sql()).isEqualTo(repairedSql);
        verify(queryExecutor, never()).execute(invalidSql);
        verify(queryExecutor).execute(repairedSql);
    }

    @Test
    void repairExhaustionReturnsSafeRejection() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        SqlGenerationService generationService = mock(SqlGenerationService.class);
        SqlGuardrailService guardrailService = mock(SqlGuardrailService.class);
        QueryExecutor queryExecutor = mock(QueryExecutor.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        SqlGenerationProperties properties = new SqlGenerationProperties();
        properties.setRepairRetries(1);

        RetrievalContext context = new RetrievalContext(true, 0.9, List.of("table: customers"), "",
                "NONE", RetrievalMode.NORMALIZED, List.of());
        String invalidSql = "SELECT i.id FROM invoices i LIMIT 100";
        when(retrievalService.retrieve("List customers")).thenReturn(context);
        when(generationService.generate("List customers", context))
                .thenReturn(GeneratedSql.ok(invalidSql, List.of()));
        when(guardrailService.validateAndSanitize(invalidSql))
                .thenReturn(GuardrailResult.deny(List.of("SQL references an unknown table: invoices.")));
        when(generationService.repair(eq("List customers"), eq(context), eq(invalidSql), any()))
                .thenReturn(GeneratedSql.rejected("The SQL could not be repaired safely from the retrieved context."));

        AgentOrchestrator orchestrator = new AgentOrchestrator(retrievalService, generationService, guardrailService,
                queryExecutor, new ResultFormatter(), auditLogger, properties);

        QueryResponse response = orchestrator.answer("List customers");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reason()).contains("could not be repaired safely");
        verify(queryExecutor, never()).execute(any());
    }

    private TableResult table() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer_id", 1L);
        return new TableResult(List.of("customer_id"), List.of(row));
    }
}
