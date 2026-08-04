package com.nlda.orchestrator;

import com.nlda.api.QueryResponse;
import com.nlda.audit.AuditLogger;
import com.nlda.execution.QueryExecutor;
import com.nlda.format.ResultFormatter;
import com.nlda.format.TableResult;
import com.nlda.generation.GeneratedSql;
import com.nlda.generation.SqlGenerationService;
import com.nlda.guardrail.GuardrailResult;
import com.nlda.guardrail.SqlExecutionRejectedException;
import com.nlda.guardrail.SqlGuardrailService;
import com.nlda.generation.SqlGenerationProperties;
import com.nlda.retrieval.RetrievalContext;
import com.nlda.retrieval.RetrievalService;
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
    private final SqlGenerationProperties generationProperties;

    public AgentOrchestrator(
            RetrievalService retrievalService,
            SqlGenerationService sqlGenerationService,
            SqlGuardrailService guardrailService,
            QueryExecutor queryExecutor,
            ResultFormatter resultFormatter,
            AuditLogger auditLogger,
            SqlGenerationProperties generationProperties
    ) {
        this.retrievalService = retrievalService;
        this.sqlGenerationService = sqlGenerationService;
        this.guardrailService = guardrailService;
        this.queryExecutor = queryExecutor;
        this.resultFormatter = resultFormatter;
        this.auditLogger = auditLogger;
        this.generationProperties = generationProperties;
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
        for (int attempt = 0; !guardrail.allowed() && attempt < generationProperties.getRepairRetries(); attempt++) {
            GeneratedSql repairedSql = sqlGenerationService.repair(question, context, generatedSql.sql(),
                    guardrail.violations());
            if (!"OK".equals(repairedSql.status())) {
                auditLogger.record(traceId, question, generatedSql.sql(), "DENY", "REPAIR_REJECTED");
                return rejected(traceId, started, repairedSql.reason());
            }
            generatedSql = repairedSql;
            guardrail = guardrailService.validateAndSanitize(generatedSql.sql());
        }
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
