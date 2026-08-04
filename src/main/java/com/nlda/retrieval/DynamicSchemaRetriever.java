package com.nlda.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class DynamicSchemaRetriever implements SchemaRetriever {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaRetriever.class);

    private final SchemaIndexService indexService;

    public DynamicSchemaRetriever(SchemaIndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, RetrievalMode mode) {
        List<RetrievedChunk> chunks = indexService.currentChunks();
        return score(query, mode, chunks).stream()
                .filter(chunk -> chunk.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(5)
                .toList();
    }

    @Override
    public List<RetrievedChunk> fallback(String query) {
        try {
            List<RetrievedChunk> fallbackChunks = indexService.fallbackChunks();
            return score(query, RetrievalMode.FALLBACK_CACHE, fallbackChunks).stream()
                    .filter(chunk -> chunk.score() >= 0.40)
                    .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                    .limit(8)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("dynamic retrieval fallback failed message={}", ex.getMessage());
            return List.of();
        }
    }

    private List<RetrievedChunk> score(String query, RetrievalMode mode, List<RetrievedChunk> chunks) {
        String normalized = normalize(query);
        List<RetrievedChunk> scored = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            scored.add(chunk.withScore(score(normalized, chunk, mode)));
        }
        return scored;
    }

    private double score(String query, RetrievedChunk chunk, RetrievalMode mode) {
        String text = normalize(chunk.text());
        double score = 0.0;
        for (String token : query.split(" ")) {
            if (token.length() < 3) {
                continue;
            }
            if (text.contains(token)) {
                score += 0.14;
            }
        }
        if (matchesSchemaRef(query, chunk, "customer", "customers", "client", "clients", "region")) {
            score += 0.34;
        }
        if (matchesSchemaRef(query, chunk, "order", "orders", "revenue", "spending", "spend", "sale", "sales",
                "undelivered")) {
            score += 0.34;
        }
        if (matchesSchemaRef(query, chunk, "product", "products")) {
            score += 0.34;
        }
        if (mode == RetrievalMode.EXPANDED && containsAny(query, "client", "clients", "sale", "sales", "spend")) {
            score += 0.20;
        }
        if (mode == RetrievalMode.HYBRID && containsAny(query, "top", "monthly", "undelivered", "highest", "by")) {
            score += 0.12;
        }
        return Math.min(score, 0.95);
    }

    private boolean matchesSchemaRef(String query, RetrievedChunk chunk, String... terms) {
        if (!containsAny(query, terms)) {
            return false;
        }
        for (String term : terms) {
            String singular = term.endsWith("s") ? term.substring(0, term.length() - 1) : term;
            for (String schemaRef : chunk.schemaRefs()) {
                if (schemaRef.contains(singular)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
