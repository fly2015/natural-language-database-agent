package com.nlda.retrieval;

import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.model.RetrievalAttempt;
import com.nlda.retrieval.model.RetrievalContext;
import com.nlda.retrieval.model.RetrievalFailureCode;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final double CONFIDENCE_THRESHOLD = 0.65;

    private final SchemaRetriever schemaRetriever;

    public RetrievalService(SchemaRetriever schemaRetriever) {
        this.schemaRetriever = schemaRetriever;
    }

    public RetrievalContext retrieve(String question) {
        List<RetrievalAttempt> attempts = new ArrayList<>();
        RetrievalContext recovered = runAttempt(question, normalize(question), RetrievalMode.NORMALIZED, 1, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        recovered = runAttempt(question, expandAliases(question), RetrievalMode.EXPANDED, 2, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        recovered = runAttempt(question, expandAliases(question), RetrievalMode.HYBRID, 3, attempts);
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
        return fallback(question, attempts, RetrievalFailureCode.RF_04.code());
    }

    private RetrievalContext runAttempt(
            String originalQuestion,
            String retrievalQuery,
            RetrievalMode mode,
            int attemptNumber,
            List<RetrievalAttempt> attempts
    ) {
        try {
            List<RetrievedChunk> chunks = schemaRetriever.retrieve(retrievalQuery, mode);
            double confidence = confidence(originalQuestion, chunks);
            RetrievalFailureCode failureCode = classify(chunks, confidence);
            RetrievalAttempt attempt = new RetrievalAttempt(attemptNumber, mode, confidence, failureCode.code(),
                    chunks.size());
            attempts.add(attempt);
            logAttempt(attempt);
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
            return clarify(0.0, RetrievalFailureCode.RF_03.code(), mode, attempts);
        }
    }

    private RetrievalContext fallback(String question, List<RetrievalAttempt> attempts, String previousFailureCode) {
        List<RetrievedChunk> chunks = schemaRetriever.fallback(question);
        double confidence = confidence(question, chunks);
        String failureCode = chunks.isEmpty() ? RetrievalFailureCode.RF_01.code() : previousFailureCode;
        RetrievalAttempt attempt = new RetrievalAttempt(attempts.size() + 1, RetrievalMode.FALLBACK_CACHE, confidence,
                failureCode, chunks.size());
        attempts.add(attempt);
        logAttempt(attempt);
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
        return new RetrievalContext(true, confidence, snippets, "", RetrievalFailureCode.NONE.code(), mode,
                List.copyOf(attempts));
    }

    private RetrievalContext clarify(
            double confidence,
            String failureCode,
            RetrievalMode mode,
            List<RetrievalAttempt> attempts
    ) {
        return new RetrievalContext(false, confidence, List.of(), safeStopReason(), failureCode, mode,
                List.copyOf(attempts));
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
        double coverage = schemaCoverage(question, chunks);
        double confidence = (max * 0.45) + (mean * 0.25) + (coverage * 0.30);
        return Math.min(0.95, Math.round(confidence * 100.0) / 100.0);
    }

    private double schemaCoverage(String question, List<RetrievedChunk> chunks) {
        String normalized = normalize(question);
        Set<String> expected = new LinkedHashSet<>();
        if (containsAny(normalized, "customer", "customers", "client", "clients", "region")) {
            expected.add("customers");
        }
        if (containsAny(normalized, "order", "orders", "revenue", "spending", "spend", "sale", "sales",
                "undelivered")) {
            expected.add("orders");
        }
        if (containsAny(normalized, "product", "products")) {
            expected.add("products");
            expected.add("order_items");
        }
        if (expected.isEmpty()) {
            return 0.0;
        }
        Set<String> actual = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            actual.addAll(chunk.schemaRefs());
        }
        long covered = expected.stream().filter(actual::contains).count();
        return (double) covered / expected.size();
    }

    private String expandAliases(String question) {
        String normalized = normalize(question);
        return normalized
                .replace("clients", "clients customers")
                .replace("client", "client customer")
                .replace("sales", "sales orders revenue")
                .replace("sale", "sale order revenue")
                .replace("spend", "spend spending revenue");
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
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

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
