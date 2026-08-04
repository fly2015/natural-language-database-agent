package com.metajpa.nlda.orchestrator;

import com.metajpa.nlda.api.QueryResponse;
import com.metajpa.nlda.audit.AuditLogger;
import com.metajpa.nlda.execution.QueryExecutor;
import com.metajpa.nlda.format.ResultFormatter;
import com.metajpa.nlda.format.TableResult;
import com.metajpa.nlda.generation.GeneratedSql;
import com.metajpa.nlda.generation.SqlGenerationService;
import com.metajpa.nlda.guardrail.GuardrailResult;
import com.metajpa.nlda.guardrail.SqlExecutionRejectedException;
import com.metajpa.nlda.guardrail.SqlGuardrailService;
import com.metajpa.nlda.retrieval.RetrievalContext;
import com.metajpa.nlda.retrieval.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentOrchestrator {

    private final RetrievalService retrievalService;
    private final SqlGenerationService sqlGenerationService;
    private final SqlGuardrailService guardrailService;
    private final QueryExecutor queryExecutor;
    private final ResultFormatter resultFormatter;
    private final AuditLogger auditLogger;

    public AgentOrchestrator(
            RetrievalService retrievalService,
            SqlGenerationService sqlGenerationService,
            SqlGuardrailService guardrailService,
            QueryExecutor queryExecutor,
            ResultFormatter resultFormatter,
            AuditLogger auditLogger
    ) {
        this.retrievalService = retrievalService;
        this.sqlGenerationService = sqlGenerationService;
        this.guardrailService = guardrailService;
        this.queryExecutor = queryExecutor;
        this.resultFormatter = resultFormatter;
        this.auditLogger = auditLogger;
    }

    public QueryResponse answer(String question) {
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();

        RetrievalContext context = retrievalService.retrieve(question);
        GeneratedSql generatedSql = sqlGenerationService.generate(question, context);
        if (!"OK".equals(generatedSql.status())) {
            auditLogger.record(traceId, question, null, "SKIPPED", "REJECTED");
            return rejected(traceId, started, generatedSql.reason());
        }

        GuardrailResult guardrail = guardrailService.validateAndSanitize(generatedSql.sql());
        if (!guardrail.allowed()) {
            auditLogger.record(traceId, question, generatedSql.sql(), "DENY", "REJECTED");
            return rejected(traceId, started, String.join(" ", guardrail.violations()));
        }

        TableResult table;
        try {
            table = queryExecutor.execute(guardrail.sql());
        } catch (SqlExecutionRejectedException ex) {
            auditLogger.record(traceId, question, guardrail.sql(), "ALLOW", "EXECUTION_REJECTED");
            return rejected(traceId, started, ex.getMessage());
        }
        String answer = resultFormatter.answer(table);
        auditLogger.record(traceId, question, guardrail.sql(), "ALLOW", "OK");
        return new QueryResponse("OK", answer, table, guardrail.sql(), traceId, elapsedMs(started),
                generatedSql.assumptions(), "");
    }

    private QueryResponse rejected(String traceId, long started, String reason) {
        return new QueryResponse("REJECTED", "", new TableResult(List.of(), List.of()), null, traceId,
                elapsedMs(started), List.of(), reason);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
