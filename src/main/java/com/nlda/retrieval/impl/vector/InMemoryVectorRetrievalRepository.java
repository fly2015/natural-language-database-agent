package com.nlda.retrieval.impl.vector;

import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VectorIndexedChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Repository
@ConditionalOnProperty(prefix = "agent.retrieval.vector", name = "provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryVectorRetrievalRepository implements VectorRetrievalRepository {

    private final AtomicReference<List<VectorIndexedChunk>> current = new AtomicReference<>(List.of());

    @Override
    public void replace(String fingerprint, String embeddingModel, List<VectorIndexedChunk> chunks) {
        current.set(List.copyOf(chunks));
    }

    @Override
    public List<RetrievedChunk> search(float[] queryEmbedding, String fingerprint, String embeddingModel, int limit) {
        return current.get().stream()
                .filter(chunk -> chunk.fingerprint().equals(fingerprint))
                .filter(chunk -> chunk.embeddingModel().equals(embeddingModel))
                .map(chunk -> chunk.chunk().withScore(cosine(queryEmbedding, chunk.embedding())))
                .filter(chunk -> chunk.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private double cosine(float[] first, float[] second) {
        int length = Math.min(first.length, second.length);
        double dot = 0.0;
        double firstMagnitude = 0.0;
        double secondMagnitude = 0.0;
        for (int i = 0; i < length; i++) {
            dot += first[i] * second[i];
            firstMagnitude += first[i] * first[i];
            secondMagnitude += second[i] * second[i];
        }
        if (firstMagnitude == 0.0 || secondMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(firstMagnitude) * Math.sqrt(secondMagnitude));
    }
}
