package com.nlda.retrieval;

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
import java.util.LinkedHashSet;
import java.util.List;
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
        RetrievalContext recovered = runAttempt(question, process(question), RetrievalMode.NORMALIZED, 1, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        recovered = runAttempt(question, process(expandedQuery(question)), RetrievalMode.EXPANDED, 2, attempts);
        if (recovered.proceed()) {
            return recovered;
        }
        recovered = runAttempt(question, process(expandedQuery(question)), RetrievalMode.HYBRID, 3, attempts);
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
            ProcessedQuery retrievalQuery,
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
        ProcessedQuery processedQuery = process(question);
        List<RetrievedChunk> chunks = schemaRetriever.fallback(processedQuery);
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

    private String safeStopReason() {
        return "The system could not retrieve enough trusted schema context to generate a safe SQL query. "
                + "Please clarify the metric, period, and target entity.";
    }

    private void logAttempt(RetrievalAttempt attempt) {
        log.info("retrievalAttempt={} mode={} confidence={} failureCode={} resultCount={}",
                attempt.attemptNumber(), attempt.mode(), attempt.confidence(), attempt.failureCode(),
                attempt.resultCount());
    }

}
