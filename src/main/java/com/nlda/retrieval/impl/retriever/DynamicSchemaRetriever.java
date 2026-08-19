package com.nlda.retrieval.impl.retriever;

import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;
import com.nlda.retrieval.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DynamicSchemaRetriever implements SchemaRetriever {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaRetriever.class);

    private final SchemaIndexService indexService;
    private final TextNormalizer textNormalizer;
    private final EmbeddingClient embeddingClient;
    private final VectorRetrievalRepository vectorRepository;
    private final EmbeddingProperties embeddingProperties;

    public DynamicSchemaRetriever(SchemaIndexService indexService) {
        this(indexService, new TextNormalizer(), null, null, null);
    }

    @Autowired
    public DynamicSchemaRetriever(
            SchemaIndexService indexService,
            TextNormalizer textNormalizer,
            EmbeddingClient embeddingClient,
            VectorRetrievalRepository vectorRepository,
            EmbeddingProperties embeddingProperties
    ) {
        this.indexService = indexService;
        this.textNormalizer = textNormalizer;
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public void prepare() {
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
            log.info("retrievalQueryAmbiguous original={} normalized={} corrections={}",
                    query.original(), query.normalized(), query.correctedTerms());
        }
        List<RetrievedChunk> lexical = score(query, mode, chunks);
        List<RetrievedChunk> semantic = semantic(query, indexed);
        return merge(lexical, semantic).stream()
                .filter(chunk -> chunk.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(8)
                .toList();
    }

    private List<RetrievedChunk> semantic(ProcessedQuery query, IndexedSchemaChunks indexed) {
        if (embeddingClient == null || vectorRepository == null || embeddingProperties == null) {
            return List.of();
        }
        try {
            float[] queryEmbedding = embeddingClient.embed(query.retrievalQuery());
            return vectorRepository.search(queryEmbedding, indexed.fingerprint(), embeddingClient.model(),
                    embeddingProperties.searchLimit());
        } catch (RuntimeException ex) {
            log.warn("semanticRetrievalFailed fingerprint={} model={} message={}", indexed.fingerprint(),
                    embeddingClient.model(), ex.getMessage());
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
            return score(query, RetrievalMode.FALLBACK_CACHE, fallbackChunks).stream()
                    .filter(chunk -> chunk.score() >= 0.25)
                    .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                    .limit(10)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("dynamic retrieval fallback failed message={}", ex.getMessage());
            return List.of();
        }
    }

    private List<RetrievedChunk> score(ProcessedQuery query, RetrievalMode mode, List<RetrievedChunk> chunks) {
        Set<String> queryTokens = tokens(query.retrievalQuery());
        Set<String> inferredSchemaRefs = inferredSchemaRefs(queryTokens, chunks);
        List<RetrievedChunk> scored = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            double correctionPenalty = query.ambiguous() ? 0.45 : query.correctionConfidence();
            scored.add(chunk.withScore(score(queryTokens, inferredSchemaRefs, chunk, mode) * correctionPenalty));
        }
        return scored;
    }

    private Set<String> inferredSchemaRefs(Set<String> queryTokens, List<RetrievedChunk> chunks) {
        Set<String> refs = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk.kind() == ChunkKind.BUSINESS_RULE && aliasMatches(queryTokens, chunk.aliases())) {
                refs.addAll(chunk.schemaRefs());
            }
        }
        return refs;
    }

    private double score(
            Set<String> queryTokens,
            Set<String> inferredSchemaRefs,
            RetrievedChunk chunk,
            RetrievalMode mode
    ) {
        Set<String> chunkTokens = tokens(chunk.text());
        Set<String> schemaTokens = schemaTokens(chunk.schemaRefs());
        double score = 0.0;

        score += exactTokenScore(queryTokens, chunkTokens, 0.12);
        score += exactTokenScore(queryTokens, schemaTokens, 0.18);
        score += aliasScore(queryTokens, chunk.aliases());

        if (!inferredSchemaRefs.isEmpty() && intersects(inferredSchemaRefs, chunk.schemaRefs())) {
            score += chunk.kind() == ChunkKind.SCHEMA ? 0.28 : 0.16;
        }
        if (chunk.kind() == ChunkKind.JOIN_PATH && overlapsAtLeast(chunk.schemaRefs(), inferredSchemaRefs, 2)) {
            score += 0.24;
        }
        if (mode == RetrievalMode.EXPANDED) {
            score += fuzzyTokenScore(queryTokens, chunkTokens, 0.04);
        }
        if (mode == RetrievalMode.HYBRID) {
            score += fuzzyTokenScore(queryTokens, chunkTokens, 0.06);
            if (chunk.kind() == ChunkKind.JOIN_PATH && !inferredSchemaRefs.isEmpty()) {
                score += 0.08;
            }
        }
        if (mode == RetrievalMode.FALLBACK_CACHE && score > 0.0) {
            score += 0.05;
        }
        return Math.min(score, 0.95);
    }

    private double exactTokenScore(Set<String> queryTokens, Set<String> chunkTokens, double weight) {
        double score = 0.0;
        for (String token : queryTokens) {
            if (chunkTokens.contains(token) || chunkTokens.contains(singular(token))) {
                score += weight;
            }
        }
        return score;
    }

    private double aliasScore(Set<String> queryTokens, Set<String> aliases) {
        double score = 0.0;
        for (String alias : aliases) {
            Set<String> aliasTokens = tokens(alias);
            if (!aliasTokens.isEmpty() && queryTokens.containsAll(aliasTokens)) {
                score += 0.34;
            }
        }
        return score;
    }

    private double fuzzyTokenScore(Set<String> queryTokens, Set<String> chunkTokens, double weight) {
        double score = 0.0;
        for (String queryToken : queryTokens) {
            for (String chunkToken : chunkTokens) {
                if (queryToken.length() >= 4 && chunkToken.length() >= 4
                        && (queryToken.contains(chunkToken) || chunkToken.contains(queryToken))) {
                    score += weight;
                    break;
                }
            }
        }
        return score;
    }

    private Set<String> schemaTokens(Set<String> schemaRefs) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String schemaRef : schemaRefs) {
            tokens.addAll(tokens(schemaRef));
        }
        return tokens;
    }

    private boolean aliasMatches(Set<String> queryTokens, Set<String> aliases) {
        for (String alias : aliases) {
            Set<String> aliasTokens = tokens(alias);
            if (!aliasTokens.isEmpty() && queryTokens.containsAll(aliasTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersects(Set<String> first, Set<String> second) {
        for (String value : first) {
            if (second.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsAtLeast(Set<String> first, Set<String> second, int expected) {
        int count = 0;
        for (String value : first) {
            if (second.contains(value)) {
                count++;
            }
        }
        return count >= expected;
    }

    private Set<String> tokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : textNormalizer.retrievalTerms(value)) {
            if (token.length() >= 3) {
                tokens.add(token);
                tokens.add(singular(token));
            }
        }
        return tokens;
    }

    private String singular(String token) {
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
