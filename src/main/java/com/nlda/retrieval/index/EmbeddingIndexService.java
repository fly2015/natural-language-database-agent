package com.nlda.retrieval.index;

import com.nlda.audit.AuditContext;
import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VectorIndexedChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnBean(EmbeddingClient.class)
public class EmbeddingIndexService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexService.class);

    private final EmbeddingClient embeddingClient;
    private final VectorRetrievalRepository vectorRepository;
    private final ChunkCanonicalizer canonicalizer;

    public EmbeddingIndexService(
            EmbeddingClient embeddingClient,
            VectorRetrievalRepository vectorRepository,
            ChunkCanonicalizer canonicalizer
    ) {
        this.embeddingClient = embeddingClient;
        this.vectorRepository = vectorRepository;
        this.canonicalizer = canonicalizer;
    }

    public void rebuild(String fingerprint, List<RetrievedChunk> chunks) {
        long started = System.nanoTime();
        log.info("operationEvent=embedding.index_rebuild.started traceId={} status=STARTED fingerprint={} model={} chunkCount={}",
                AuditContext.traceId(), fingerprint, embeddingClient.model(), chunks.size());
        List<VectorIndexedChunk> indexed = new ArrayList<>();
        int failed = 0;
        String firstFailure = "";
        for (RetrievedChunk chunk : chunks) {
            try {
                String canonicalText = canonicalizer.canonicalText(chunk);
                indexed.add(new VectorIndexedChunk(
                        chunk,
                        fingerprint,
                        canonicalizer.contentHash(chunk),
                        embeddingClient.model(),
                        embeddingClient.embed(canonicalText)
                ));
            } catch (RuntimeException ex) {
                failed++;
                if (firstFailure.isBlank()) {
                    firstFailure = ex.getMessage();
                }
                log.warn("operationEvent=embedding.chunk_index_failed traceId={} status=FAILED chunkId={} model={} failedCount={} reason=\"{}\"",
                        AuditContext.traceId(), chunk.id(), embeddingClient.model(), failed, safe(ex.getMessage()));
            }
        }
        if (failed > 0) {
            log.warn("operationEvent=embedding.index_rebuild.completed traceId={} status=FAILED latencyMs={} fingerprint={} model={} chunkCount={} indexedCount={} failedCount={} reason=\"{}\"",
                    AuditContext.traceId(), elapsedMs(started), fingerprint, embeddingClient.model(), chunks.size(),
                    indexed.size(), failed, safe(firstFailure));
            throw new IllegalStateException("embedding index rebuild failed for " + failed + " of " + chunks.size()
                    + " chunks: " + firstFailure);
        }
        vectorRepository.replace(fingerprint, embeddingClient.model(), indexed);
        log.info("operationEvent=embedding.index_rebuild.completed traceId={} status=OK latencyMs={} fingerprint={} model={} indexedCount={} failedCount={}",
                AuditContext.traceId(), elapsedMs(started), fingerprint, embeddingClient.model(), indexed.size(),
                failed);
    }

    public String indexKey(String fingerprint) {
        return fingerprint + ":" + embeddingClient.model();
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
