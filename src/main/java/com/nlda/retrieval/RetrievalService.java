package com.nlda.retrieval;

import com.nlda.audit.AuditContext;
import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.model.RetrievalAttempt;
import com.nlda.retrieval.model.RetrievalContext;
import com.nlda.retrieval.model.RetrievalFailureCode;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.query.ProcessedQuery;
import com.nlda.retrieval.query.RetrievalQueryProcessor;
import com.nlda.retrieval.text.TextNormalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final double CONFIDENCE_THRESHOLD = 0.65;

    private final SchemaRetriever schemaRetriever;
    private final RetrievalQueryProcessor queryProcessor;
    private final TextNormalizer textNormalizer;

    @Autowired
    public RetrievalService(
            SchemaRetriever schemaRetriever,
            RetrievalQueryProcessor queryProcessor,
            TextNormalizer textNormalizer
    ) {
        this.schemaRetriever = schemaRetriever;
        this.queryProcessor = queryProcessor;
        this.textNormalizer = textNormalizer;
    }

    RetrievalService(SchemaRetriever schemaRetriever) {
        TextNormalizer normalizer = new TextNormalizer();
        this.schemaRetriever = schemaRetriever;
        this.textNormalizer = normalizer;
        this.queryProcessor = new RetrievalQueryProcessor(
                normalizer,
                new com.nlda.retrieval.impl.vocabulary.InMemoryVocabularyCorrectionService(
                        new com.nlda.retrieval.query.SchemaVocabularyMatcher(normalizer)
                )
        );
    }

    public RetrievalContext retrieve(String question) {
        List<RetrievalAttempt> attempts = new ArrayList<>();
        long started = System.nanoTime();
        String normalized = normalize(question);
        audit("retrieval.normalize", "OK", started, map("question", question),
                map("normalizerClass", textNormalizer.getClass().getName(), "normalized", normalized));

        ProcessedQuery normalizedQuery = processForAudit(question, RetrievalMode.NORMALIZED);
        RetrievalContext recovered = runAttempt(question, normalizedQuery, RetrievalMode.NORMALIZED, 1, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        String expanded = expandedQuery(question);
        audit("retrieval.expand", "OK", 0, map("question", question, "normalized", normalized),
                map("expanded", expanded));
        ProcessedQuery expandedQuery = processForAudit(expanded, RetrievalMode.EXPANDED);
        recovered = runAttempt(question, expandedQuery, RetrievalMode.EXPANDED, 2, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        recovered = runAttempt(question, expandedQuery, RetrievalMode.HYBRID, 3, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        return fallback(question, attempts, recovered.failureCode());
    }

    public RetrievalContext recoverFromValidationFailure(String question, RetrievalContext previousContext) {
        List<RetrievalAttempt> attempts = new ArrayList<>(previousContext.attempts());
        attempts.add(new RetrievalAttempt(attempts.size() + 1, previousContext.finalMode(), previousContext.confidence(),
                RetrievalFailureCode.RF_04.code(), previousContext.snippets().size()));
        logAttempt(attempts.getLast());
        audit("retrieval.validation_recovery", "RF-04", 0, map("question", question, "previousContext",
                previousContext), map("attempts", attempts));
        return fallback(question, attempts, RetrievalFailureCode.RF_04.code());
    }

    private RetrievalContext runAttempt(
            String originalQuestion,
            ProcessedQuery retrievalQuery,
            RetrievalMode mode,
            int attemptNumber,
            List<RetrievalAttempt> attempts
    ) {
        try {
            long started = System.nanoTime();
            audit("retrieval.schema_retriever.request", schemaRetriever.getClass().getName(), "STARTED", 0,
                    map("mode", mode, "attemptNumber", attemptNumber, "processedQuery", retrievalQuery),
                    map());
            List<RetrievedChunk> chunks = schemaRetriever.retrieve(retrievalQuery, mode);
            double confidence = confidence(originalQuestion, chunks);
            RetrievalFailureCode failureCode = classify(chunks, confidence);
            RetrievalAttempt attempt = new RetrievalAttempt(attemptNumber, mode, confidence, failureCode.code(),
                    chunks.size());
            attempts.add(attempt);
            logAttempt(attempt);
            audit("retrieval.schema_retriever.response", schemaRetriever.getClass().getName(), failureCode.code(), started,
                    map("mode", mode, "attemptNumber", attemptNumber, "retrievalQuery",
                            retrievalQuery.retrievalQuery()),
                    map("chunks", chunkSummaries(chunks), "confidence", confidence, "failureCode",
                            failureCode.code(), "attempt", attempt));
            if (confidence >= CONFIDENCE_THRESHOLD && !chunks.isEmpty()) {
                return proceed(chunks, confidence, mode, attempts);
            }
            return clarify(confidence, failureCode.code(), mode, attempts);
        } catch (RuntimeException ex) {
            RetrievalAttempt attempt = new RetrievalAttempt(attemptNumber, mode, 0.0, RetrievalFailureCode.RF_03.code(), 0);
            attempts.add(attempt);
            logAttempt(attempt);
            log.warn("retrieval failure mode={} failureCode={} message={}", mode, RetrievalFailureCode.RF_03.code(),
                    ex.getMessage());
            audit("retrieval.schema_retriever.response", schemaRetriever.getClass().getName(),
                    RetrievalFailureCode.RF_03.code(), 0,
                    map("mode", mode, "attemptNumber", attemptNumber, "retrievalQuery",
                            retrievalQuery.retrievalQuery()),
                    map("error", ex.getMessage(), "attempt", attempt));
            return clarify(0.0, RetrievalFailureCode.RF_03.code(), mode, attempts);
        }
    }

    private RetrievalContext fallback(String question, List<RetrievalAttempt> attempts, String previousFailureCode) {
        ProcessedQuery processedQuery = processForAudit(question, RetrievalMode.FALLBACK_CACHE);
        long started = System.nanoTime();
        audit("retrieval.fallback.request", schemaRetriever.getClass().getName(), "STARTED", 0, map("processedQuery", processedQuery,
                "previousFailureCode", previousFailureCode), map());
        List<RetrievedChunk> chunks = schemaRetriever.fallback(processedQuery);
        double confidence = confidence(question, chunks);
        String failureCode = chunks.isEmpty() ? RetrievalFailureCode.RF_01.code() : previousFailureCode;
        RetrievalAttempt attempt = new RetrievalAttempt(attempts.size() + 1, RetrievalMode.FALLBACK_CACHE, confidence,
                failureCode, chunks.size());
        attempts.add(attempt);
        logAttempt(attempt);
        audit("retrieval.fallback.response", schemaRetriever.getClass().getName(), failureCode, started, map("retrievalQuery",
                processedQuery.retrievalQuery()), map("chunks", chunkSummaries(chunks), "confidence", confidence,
                "failureCode", failureCode, "attempt", attempt));
        if (confidence >= CONFIDENCE_THRESHOLD && !chunks.isEmpty()) {
            return proceed(chunks, confidence, RetrievalMode.FALLBACK_CACHE, attempts);
        }
        return clarify(confidence, failureCode, RetrievalMode.FALLBACK_CACHE, attempts);
    }

    private RetrievalContext proceed(
            List<RetrievedChunk> chunks,
            double confidence,
            RetrievalMode mode,
            List<RetrievalAttempt> attempts
    ) {
        List<String> snippets = chunks.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .map(RetrievedChunk::text)
                .distinct()
                .toList();
        RetrievalContext context = new RetrievalContext(true, confidence, snippets, "", RetrievalFailureCode.NONE.code(), mode,
                List.copyOf(attempts));
        audit("retrieval.decision", "PROCEED", 0, map("chunks", chunkSummaries(chunks), "attempts", attempts),
                map("context", context));
        return context;
    }

    private RetrievalContext clarify(
            double confidence,
            String failureCode,
            RetrievalMode mode,
            List<RetrievalAttempt> attempts
    ) {
        RetrievalContext context = new RetrievalContext(false, confidence, List.of(), safeStopReason(), failureCode, mode,
                List.copyOf(attempts));
        audit("retrieval.decision", "CLARIFY", 0, map("confidence", confidence, "failureCode", failureCode,
                "mode", mode, "attempts", attempts), map("context", context));
        return context;
    }

    private RetrievalFailureCode classify(List<RetrievedChunk> chunks, double confidence) {
        if (chunks.isEmpty()) {
            return RetrievalFailureCode.RF_01;
        }
        if (confidence < CONFIDENCE_THRESHOLD) {
            return RetrievalFailureCode.RF_02;
        }
        return RetrievalFailureCode.NONE;
    }

    private double confidence(String question, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0.0;
        }
        double max = chunks.stream().mapToDouble(RetrievedChunk::score).max().orElse(0.0);
        double mean = chunks.stream().mapToDouble(RetrievedChunk::score).average().orElse(0.0);
        double coverage = contextCoverage(chunks);
        double confidence = (max * 0.50) + (mean * 0.25) + (coverage * 0.25);
        return Math.min(0.95, Math.round(confidence * 100.0) / 100.0);
    }

    private double contextCoverage(List<RetrievedChunk> chunks) {
        Set<String> schemaRefs = new LinkedHashSet<>();
        Set<ChunkKind> kinds = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            schemaRefs.addAll(chunk.schemaRefs());
            kinds.add(chunk.kind());
        }
        double schemaDiversity = Math.min(1.0, schemaRefs.size() / 3.0);
        double typedDiversity = Math.min(1.0, kinds.size() / 3.0);
        return (schemaDiversity * 0.65) + (typedDiversity * 0.35);
    }

    private String expandedQuery(String question) {
        String normalized = normalize(question);
        StringBuilder expanded = new StringBuilder(normalized);
        for (String token : normalized.split(" ")) {
            if (token.endsWith("s") && token.length() > 3) {
                expanded.append(' ').append(token, 0, token.length() - 1);
            }
        }
        return expanded.toString();
    }

    private String normalize(String value) {
        return textNormalizer.normalize(value);
    }

    private ProcessedQuery process(String value) {
        return queryProcessor.process(value);
    }

    private ProcessedQuery processForAudit(String value, RetrievalMode mode) {
        long started = System.nanoTime();
        ProcessedQuery processedQuery = process(value);
        audit("retrieval.query.process", processedQuery.ambiguous() ? "AMBIGUOUS" : "OK", started,
                map("mode", mode, "input", value), map("processorClass", queryProcessor.getClass().getName(),
                        "processedQuery", processedQuery));
        return processedQuery;
    }

    private String safeStopReason() {
        return "The system could not retrieve enough trusted schema context to generate a safe SQL query. "
                + "Please clarify the metric, period, and target entity.";
    }

    private void logAttempt(RetrievalAttempt attempt) {
        log.info("retrievalAttempt={} mode={} confidence={} failureCode={} resultCount={}",
                attempt.attemptNumber(), attempt.mode(), attempt.confidence(), attempt.failureCode(),
                attempt.resultCount());
    }

    private List<Map<String, Object>> chunkSummaries(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> map("id", chunk.id(), "kind", chunk.kind(), "score", chunk.score(), "schemaRefs",
                        chunk.schemaRefs(), "aliases", chunk.aliases(), "text", chunk.text()))
                .toList();
    }

    private void audit(String name, String status, long started, Map<String, Object> input, Map<String, Object> output) {
        audit(name, getClass().getName(), status, started, input, output);
    }

    private void audit(String name, String implementationClass, String status, long started, Map<String, Object> input,
            Map<String, Object> output) {
        long durationMs = started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000;
        AuditContext.step(name, implementationClass, status, durationMs, input, output);
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

}
