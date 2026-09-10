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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

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
            log.info("flowEvent=request.received traceId={} status=OK questionLength={}", traceId,
                    question == null ? 0 : question.length());

            long stepStarted = System.nanoTime();
            log.info("flowEvent=retrieval.started traceId={} status=STARTED", traceId);
            RetrievalContext context = retrievalService.retrieve(question);
            long retrievalLatencyMs = elapsedMs(stepStarted);
            log.info("flowEvent=retrieval.completed traceId={} status={} latencyMs={} mode={} confidence={} failureCode={} snippetCount={} attemptCount={}",
                    traceId, context.proceed() ? "OK" : "REJECTED", retrievalLatencyMs, context.finalMode(),
                    context.confidence(), context.failureCode(), context.snippets().size(), context.attempts().size());
            audit.step("retrieval.retrieve", context.proceed() ? "OK" : "REJECTED", elapsedMs(stepStarted),
                    map("question", question),
                    map("proceed", context.proceed(), "confidence", context.confidence(), "finalMode",
                            context.finalMode(), "failureCode", context.failureCode(), "reason", context.reason(),
                            "snippetCount", context.snippets().size(), "attempts", context.attempts()));

            stepStarted = System.nanoTime();
            log.info("flowEvent=llm.sql_generation.started traceId={} status=STARTED provider={} model={} phase=generation contextSnippetCount={}",
                    traceId, generationProperties.getProvider(), generationProperties.getModel(),
                    context.snippets().size());
            GeneratedSql generatedSql = sqlGenerationService.generate(question, context);
            long generationLatencyMs = elapsedMs(stepStarted);
            logAtStatus(generatedSql.status(), "flowEvent=llm.sql_generation.completed traceId={} status={} latencyMs={} provider={} model={} phase=generation sqlHash={} assumptionCount={} reason=\"{}\"",
                    traceId, generatedSql.status(), generationLatencyMs, generationProperties.getProvider(),
                    generationProperties.getModel(), sqlHash(generatedSql.sql()), generatedSql.assumptions().size(),
                    safe(generatedSql.reason()));
            audit.step("llm.sql_generation", generatedSql.status(), generationLatencyMs,
                    llmInput("generation", question, context, null, List.of()),
                    generatedSqlOutput(generatedSql));
            if (!"OK".equals(generatedSql.status())) {
                QueryResponse response = rejected(traceId, started, generatedSql.reason());
                complete(audit, response, started);
                return response;
            }

            stepStarted = System.nanoTime();
            GuardrailResult guardrail = guardrailService.validateAndSanitize(generatedSql.sql());
            long guardrailLatencyMs = elapsedMs(stepStarted);
            logAtDecision(guardrail.allowed(), "flowEvent=guardrail.validation.completed traceId={} status={} latencyMs={} sqlHash={} violationCount={} reason=\"{}\"",
                    traceId, guardrail.allowed() ? "ALLOW" : "DENY", guardrailLatencyMs, sqlHash(generatedSql.sql()),
                    guardrail.violations().size(), safe(String.join(" ", guardrail.violations())));
            audit.step("guardrail.validate", guardrail.allowed() ? "ALLOW" : "DENY", guardrailLatencyMs,
                    map("sql", generatedSql.sql()),
                    map("allowed", guardrail.allowed(), "sqlHash", sqlHash(guardrail.sql()), "violationCount",
                            guardrail.violations().size(), "violations", guardrail.violations()));
            for (int attempt = 0; !guardrail.allowed() && attempt < generationProperties.getRepairRetries(); attempt++)
            {
                stepStarted = System.nanoTime();
                log.info("flowEvent=llm.sql_generation.started traceId={} status=STARTED provider={} model={} phase=repair validationErrorCount={}",
                        traceId, generationProperties.getProvider(), generationProperties.getModel(),
                        guardrail.violations().size());
                GeneratedSql repairedSql = sqlGenerationService.repair(question, context, generatedSql.sql(),
                        guardrail.violations());
                generationLatencyMs = elapsedMs(stepStarted);
                logAtStatus(repairedSql.status(), "flowEvent=llm.sql_generation.completed traceId={} status={} latencyMs={} provider={} model={} phase=repair sqlHash={} assumptionCount={} reason=\"{}\"",
                        traceId, repairedSql.status(), generationLatencyMs, generationProperties.getProvider(),
                        generationProperties.getModel(), sqlHash(repairedSql.sql()), repairedSql.assumptions().size(),
                        safe(repairedSql.reason()));
                audit.step("llm.sql_repair", repairedSql.status(), generationLatencyMs,
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
                guardrailLatencyMs = elapsedMs(stepStarted);
                logAtDecision(guardrail.allowed(), "flowEvent=guardrail.validation.completed traceId={} status={} latencyMs={} sqlHash={} violationCount={} reason=\"{}\"",
                        traceId, guardrail.allowed() ? "ALLOW" : "DENY", guardrailLatencyMs,
                        sqlHash(generatedSql.sql()), guardrail.violations().size(),
                        safe(String.join(" ", guardrail.violations())));
                audit.step("guardrail.validate", guardrail.allowed() ? "ALLOW" : "DENY", guardrailLatencyMs,
                        map("sql", generatedSql.sql()),
                        map("allowed", guardrail.allowed(), "sqlHash", sqlHash(guardrail.sql()), "violationCount",
                                guardrail.violations().size(), "violations", guardrail.violations()));
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
                long executionLatencyMs = elapsedMs(stepStarted);
                log.info("flowEvent=database.execution.completed traceId={} status=OK latencyMs={} database={} sqlHash={} rowCount={}",
                        traceId, executionLatencyMs, queryExecutor.database(), sqlHash(guardrail.sql()),
                        table.rows().size());
                audit.step("database.execute", "OK", executionLatencyMs,
                        map("database", queryExecutor.database(), "sql", guardrail.sql()),
                        tableOutput(table));
            } catch (SqlExecutionRejectedException ex) {
                log.warn("flowEvent=database.execution.completed traceId={} status=REJECTED latencyMs=0 database={} sqlHash={} reason=\"{}\"",
                        traceId, queryExecutor.database(), sqlHash(guardrail.sql()), safe(ex.getMessage()));
                audit.step("database.execute", "REJECTED", 0,
                        map("database", queryExecutor.database(), "sql", guardrail.sql()),
                        map("error", ex.getMessage()));
                QueryResponse response = rejected(traceId, started, ex.getMessage());
                complete(audit, response, started);
                return response;
            }
            stepStarted = System.nanoTime();
            String answer = resultFormatter.answer(table);
            long formatLatencyMs = elapsedMs(stepStarted);
            log.info("flowEvent=response.format.completed traceId={} status=OK latencyMs={} rowCount={}",
                    traceId, formatLatencyMs, table.rows().size());
            audit.step("response.format", "OK", formatLatencyMs, tableOutput(table), map("answer", answer));
            QueryResponse response = new QueryResponse("OK", answer, table, guardrail.sql(), traceId, elapsedMs(started),
                    generatedSql.assumptions(), "");
            complete(audit, response, started);
            return response;
        } catch (RuntimeException ex) {
            log.error("flowEvent=request.completed traceId={} status=ERROR latencyMs={} reason=\"{}\"",
                    traceId, elapsedMs(started), safe(ex.getMessage()));
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
        logAtStatus(response.status(), "flowEvent=request.completed traceId={} status={} latencyMs={} sqlHash={} rowCount={} reason=\"{}\"",
                response.traceId(), response.status(), response.latencyMs(), sqlHash(response.sql()),
                response.table().rows().size(), safe(response.reason()));
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
        return map("columns", table.columns(), "rowCount", table.rows().size());
    }

    private Map<String, Object> responseOutput(QueryResponse response) {
        return map("status", response.status(), "answer", response.answer(), "table", tableOutput(response.table()), "sql",
                response.sql(), "traceId", response.traceId(), "latencyMs", response.latencyMs(), "assumptions",
                response.assumptions(), "reason", response.reason());
    }

    private void logAtStatus(String status, String pattern, Object... args) {
        if ("OK".equals(status) || "ALLOW".equals(status)) {
            log.info(pattern, args);
        } else {
            log.warn(pattern, args);
        }
    }

    private void logAtDecision(boolean allowed, String pattern, Object... args) {
        if (allowed) {
            log.info(pattern, args);
        } else {
            log.warn(pattern, args);
        }
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

    private Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }
}
