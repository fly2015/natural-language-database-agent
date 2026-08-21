package com.nlda.orchestrator;

import com.nlda.api.QueryResponse;
import com.nlda.audit.AuditContext;
import com.nlda.audit.AuditEvent;
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
import com.nlda.retrieval.RetrievalService;
import com.nlda.retrieval.model.RetrievalContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        AuditEvent audit = auditLogger.start(traceId, question);
        if (audit == null) {
            audit = new AuditEvent(traceId, question);
        }
        AuditContext.set(audit);

        try {
            audit.step("request.received", "OK", 0, map(), map("question", question));

            long stepStarted = System.nanoTime();
            RetrievalContext context = retrievalService.retrieve(question);
            audit.step("retrieval.retrieve", context.proceed() ? "OK" : "REJECTED", elapsedMs(stepStarted),
                    map("question", question),
                    map("proceed", context.proceed(), "confidence", context.confidence(), "finalMode",
                            context.finalMode(), "failureCode", context.failureCode(), "reason", context.reason(),
                            "snippets", context.snippets(), "attempts", context.attempts()));

            stepStarted = System.nanoTime();
            GeneratedSql generatedSql = sqlGenerationService.generate(question, context);
            audit.step("llm.sql_generation", generatedSql.status(), elapsedMs(stepStarted),
                    llmInput("generation", question, context, null, List.of()),
                    generatedSqlOutput(generatedSql));
            if (!"OK".equals(generatedSql.status())) {
                QueryResponse response = rejected(traceId, started, generatedSql.reason());
                complete(audit, response, started);
                return response;
            }

            stepStarted = System.nanoTime();
            GuardrailResult guardrail = guardrailService.validateAndSanitize(generatedSql.sql());
            audit.step("guardrail.validate", guardrail.allowed() ? "ALLOW" : "DENY", elapsedMs(stepStarted),
                    map("sql", generatedSql.sql()),
                    map("allowed", guardrail.allowed(), "sql", guardrail.sql(), "violations", guardrail.violations()));
            for (int attempt = 0; !guardrail.allowed() && attempt < generationProperties.getRepairRetries(); attempt++)
            {
                stepStarted = System.nanoTime();
                GeneratedSql repairedSql = sqlGenerationService.repair(question, context, generatedSql.sql(),
                        guardrail.violations());
                audit.step("llm.sql_repair", repairedSql.status(), elapsedMs(stepStarted),
                        llmInput("repair", question, context, generatedSql.sql(), guardrail.violations()),
                        generatedSqlOutput(repairedSql));
                if (!"OK".equals(repairedSql.status()))
                {
                    QueryResponse response = rejected(traceId, started, repairedSql.reason());
                    complete(audit, response, started);
                    return response;
                }
                generatedSql = repairedSql;
                stepStarted = System.nanoTime();
                guardrail = guardrailService.validateAndSanitize(generatedSql.sql());
                audit.step("guardrail.validate", guardrail.allowed() ? "ALLOW" : "DENY", elapsedMs(stepStarted),
                        map("sql", generatedSql.sql()),
                        map("allowed", guardrail.allowed(), "sql", guardrail.sql(), "violations",
                                guardrail.violations()));
            }
            if (!guardrail.allowed())
            {
                QueryResponse response = rejected(traceId, started, String.join(" ", guardrail.violations()));
                complete(audit, response, started);
                return response;
            }

            TableResult table;
            try {
                stepStarted = System.nanoTime();
                table = queryExecutor.execute(guardrail.sql());
                audit.step("database.execute", "OK", elapsedMs(stepStarted),
                        map("database", queryExecutor.database(), "sql", guardrail.sql()),
                        tableOutput(table));
            } catch (SqlExecutionRejectedException ex) {
                audit.step("database.execute", "REJECTED", 0,
                        map("database", queryExecutor.database(), "sql", guardrail.sql()),
                        map("error", ex.getMessage()));
                QueryResponse response = rejected(traceId, started, ex.getMessage());
                complete(audit, response, started);
                return response;
            }
            stepStarted = System.nanoTime();
            String answer = resultFormatter.answer(table);
            audit.step("response.format", "OK", elapsedMs(stepStarted), tableOutput(table), map("answer", answer));
            QueryResponse response = new QueryResponse("OK", answer, table, guardrail.sql(), traceId, elapsedMs(started),
                    generatedSql.assumptions(), "");
            complete(audit, response, started);
            return response;
        } catch (RuntimeException ex) {
            audit.complete("ERROR", elapsedMs(started), map("error", ex.getMessage()));
            auditLogger.record(audit);
            throw ex;
        } finally {
            AuditContext.clear();
        }
    }

    private QueryResponse rejected(String traceId, long started, String reason) {
        return new QueryResponse("REJECTED", "", new TableResult(List.of(), List.of()), null, traceId,
                elapsedMs(started), List.of(), reason);
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private void complete(AuditEvent audit, QueryResponse response, long started) {
        audit.complete(response.status(), elapsedMs(started), responseOutput(response));
        auditLogger.record(audit);
    }

    private Map<String, Object> llmInput(String phase, String question, RetrievalContext context, String rejectedSql,
            List<String> validationErrors) {
        return map("phase", phase, "provider", generationProperties.getProvider(), "model",
                generationProperties.getModel(), "question", question, "retrievalConfidence", context.confidence(),
                "retrievalMode", context.finalMode(), "contextSnippetCount", context.snippets().size(),
                "rejectedSql", rejectedSql, "validationErrors", validationErrors);
    }

    private Map<String, Object> generatedSqlOutput(GeneratedSql generatedSql) {
        return map("status", generatedSql.status(), "sql", generatedSql.sql(), "assumptions",
                generatedSql.assumptions(), "reason", generatedSql.reason());
    }

    private Map<String, Object> tableOutput(TableResult table) {
        return map("columns", table.columns(), "rowCount", table.rows().size(), "rows", table.rows());
    }

    private Map<String, Object> responseOutput(QueryResponse response) {
        return map("status", response.status(), "answer", response.answer(), "table", response.table(), "sql",
                response.sql(), "traceId", response.traceId(), "latencyMs", response.latencyMs(), "assumptions",
                response.assumptions(), "reason", response.reason());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }
}
