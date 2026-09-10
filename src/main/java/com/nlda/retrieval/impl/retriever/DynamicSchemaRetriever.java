package com.nlda.retrieval.impl.retriever;

import com.nlda.audit.AuditContext;
import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;
import com.nlda.retrieval.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DynamicSchemaRetriever implements SchemaRetriever {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaRetriever.class);

    private final SchemaIndexService indexService;
    private final LexicalSchemaChunkScorer lexicalScorer;
    private final EmbeddingClient embeddingClient;
    private final VectorRetrievalRepository vectorRepository;
    private final EmbeddingProperties embeddingProperties;
    private final BusinessRuleProperties businessRuleProperties;

    public DynamicSchemaRetriever(SchemaIndexService indexService) {
        this.indexService = indexService;
        this.lexicalScorer = new LexicalSchemaChunkScorer(new TextNormalizer());
        this.embeddingClient = null;
        this.vectorRepository = null;
        this.embeddingProperties = null;
        this.businessRuleProperties = new BusinessRuleProperties();
    }

    @Autowired
    public DynamicSchemaRetriever(
            SchemaIndexService indexService,
            LexicalSchemaChunkScorer lexicalScorer,
            ObjectProvider<EmbeddingClient> embeddingClient,
            ObjectProvider<VectorRetrievalRepository> vectorRepository,
            EmbeddingProperties embeddingProperties,
            BusinessRuleProperties businessRuleProperties
    ) {
        this.indexService = indexService;
        this.lexicalScorer = lexicalScorer;
        this.embeddingClient = embeddingClient.getIfAvailable();
        this.vectorRepository = vectorRepository.getIfAvailable();
        this.embeddingProperties = embeddingProperties;
        this.businessRuleProperties = businessRuleProperties;
    }

    @Override
    public void prepare() {
        if (businessRuleProperties.isConsumeOnly()) {
            try {
                indexService.readyIndex();
            } catch (RuntimeException ex) {
                log.warn("retrievalPrepareConsumeOnlyFailed message={}", ex.getMessage());
            }
            return;
        }
        try {
            indexService.refresh();
        } catch (RuntimeException ex) {
            log.warn("retrievalPrepareFailed message={}", ex.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> retrieve(ProcessedQuery query, RetrievalMode mode) {
        IndexedSchemaChunks indexed = indexService.readyIndex();
        List<RetrievedChunk> chunks = indexed.chunks();
        if (query.ambiguous()) {
            log.info("flowEvent=retrieval.query_ambiguous traceId={} status=AMBIGUOUS correctedTermCount={}",
                    AuditContext.traceId(), query.correctedTerms().size());
        }
        List<RetrievedChunk> lexical = lexicalScorer.score(query, mode, chunks);
        log.info("flowEvent=retrieval.lexical_search traceId={} status=OK mode={} fingerprint={} resultCount={}",
                AuditContext.traceId(), mode, indexed.fingerprint(), lexical.size());
        List<RetrievedChunk> semantic = semantic(query, indexed);
        List<RetrievedChunk> merged = merge(lexical, semantic).stream()
                .filter(chunk -> chunk.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(8)
                .toList();
        log.info("flowEvent=retrieval.schema_search.completed traceId={} status=OK mode={} fingerprint={} lexicalCount={} semanticCount={} resultCount={} topChunkIds={}",
                AuditContext.traceId(), mode, indexed.fingerprint(), lexical.size(), semantic.size(), merged.size(),
                chunkIds(merged));
        return merged;
    }

    private List<RetrievedChunk> semantic(ProcessedQuery query, IndexedSchemaChunks indexed) {
        long semanticStarted = System.nanoTime();
        if (embeddingClient == null || vectorRepository == null || embeddingProperties == null) {
            log.info("flowEvent=retrieval.semantic_search traceId={} status=SKIPPED reason=no semantic dependencies",
                    AuditContext.traceId());
            return List.of();
        }
        String model = embeddingClient.model();
        long activeCount = vectorRepository.activeCount(indexed.fingerprint(), model);
        if (activeCount <= 0) {
            log.info("flowEvent=retrieval.semantic_search traceId={} status=SKIPPED fingerprint={} model={} activeEmbeddingCount={} reason=no active embeddings",
                    AuditContext.traceId(), indexed.fingerprint(), model, activeCount);
            return List.of();
        }

        float[] queryEmbedding;
        long embeddingStarted = System.nanoTime();
        log.info("flowEvent=retrieval.embedding.started traceId={} status=STARTED model={} inputTokenCount={}",
                AuditContext.traceId(), model, query.tokens().size());
        try {
            queryEmbedding = embeddingClient.embed(query.retrievalQuery());
        } catch (RuntimeException ex) {
            log.warn("flowEvent=retrieval.embedding.completed traceId={} status=FAILED latencyMs={} model={} failureCode=EMBEDDING_QUERY_FAILED reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(embeddingStarted), model, safe(ex.getMessage()));
            log.warn("flowEvent=retrieval.semantic_search traceId={} status=FAILED latencyMs={} fingerprint={} model={} failureCode=EMBEDDING_QUERY_FAILED reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(semanticStarted), indexed.fingerprint(), model, safe(ex.getMessage()));
            return List.of();
        }
        log.info("flowEvent=retrieval.embedding.completed traceId={} status=OK latencyMs={} model={} dimensionCount={}",
                AuditContext.traceId(), elapsedMs(embeddingStarted), model, queryEmbedding.length);

        long searchStarted = System.nanoTime();
        log.info("flowEvent=retrieval.vector_search.started traceId={} status=STARTED fingerprint={} model={} activeEmbeddingCount={} limit={}",
                AuditContext.traceId(), indexed.fingerprint(), model, activeCount, embeddingProperties.searchLimit());
        try {
            List<RetrievedChunk> results = vectorRepository.search(queryEmbedding, indexed.fingerprint(), model,
                    embeddingProperties.searchLimit());
            log.info("flowEvent=retrieval.vector_search.completed traceId={} status=OK latencyMs={} fingerprint={} model={} resultCount={} topChunkIds={}",
                    AuditContext.traceId(), elapsedMs(searchStarted), indexed.fingerprint(), model, results.size(),
                    chunkIds(results));
            log.info("flowEvent=retrieval.semantic_search traceId={} status=OK latencyMs={} fingerprint={} model={} resultCount={} topChunkIds={}",
                    AuditContext.traceId(), elapsedMs(semanticStarted), indexed.fingerprint(), model, results.size(),
                    chunkIds(results));
            return results;
        } catch (RuntimeException ex) {
            log.warn("flowEvent=retrieval.vector_search.completed traceId={} status=FAILED latencyMs={} fingerprint={} model={} failureCode=VECTOR_SEARCH_FAILED reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(searchStarted), indexed.fingerprint(), model, safe(ex.getMessage()));
            log.warn("flowEvent=retrieval.semantic_search traceId={} status=FAILED latencyMs={} fingerprint={} model={} failureCode=VECTOR_SEARCH_FAILED reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(semanticStarted), indexed.fingerprint(), model, safe(ex.getMessage()));
            return List.of();
        }
    }

    private List<RetrievedChunk> merge(List<RetrievedChunk> lexical, List<RetrievedChunk> semantic) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (RetrievedChunk chunk : lexical) {
            merged.put(chunk.id(), chunk);
        }
        for (RetrievedChunk chunk : semantic) {
            merged.merge(chunk.id(), chunk, (existing, candidate) -> existing.withScore(
                    Math.min(0.95, (existing.score() * 0.65) + (candidate.score() * 0.35) + 0.08)
            ));
        }
        return List.copyOf(merged.values());
    }

    @Override
    public List<RetrievedChunk> fallback(ProcessedQuery query) {
        try {
            List<RetrievedChunk> fallbackChunks = indexService.fallbackChunks();
            return lexicalScorer.score(query, RetrievalMode.FALLBACK_CACHE, fallbackChunks).stream()
                    .filter(chunk -> chunk.score() >= 0.25)
                    .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                    .limit(10)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("dynamic retrieval fallback failed message={}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> chunkIds(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::id).limit(8).toList();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }
}
