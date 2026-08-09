package com.nlda.retrieval.index;

import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VectorIndexedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
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
        List<VectorIndexedChunk> indexed = new ArrayList<>();
        int failed = 0;
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
                log.warn("embeddingChunkIndexFailed chunkId={} message={}", chunk.id(), ex.getMessage());
            }
        }
        vectorRepository.replace(fingerprint, embeddingClient.model(), indexed);
        log.info("embeddingIndexRebuild fingerprint={} model={} indexedCount={} failedCount={}",
                fingerprint, embeddingClient.model(), indexed.size(), failed);
    }
}
