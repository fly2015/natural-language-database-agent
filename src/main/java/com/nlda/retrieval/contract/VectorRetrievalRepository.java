package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.RetrievalIndexRecord;
import com.nlda.retrieval.model.VectorIndexedChunk;

import java.util.List;

public interface VectorRetrievalRepository {

    void replace(String fingerprint, String embeddingModel, List<VectorIndexedChunk> chunks);

    List<RetrievedChunk> search(float[] queryEmbedding, String fingerprint, String embeddingModel, int limit);

    default boolean hasActiveEmbeddings(String fingerprint, String embeddingModel) {
        return activeCount(fingerprint, embeddingModel) > 0;
    }

    default long activeCount(String fingerprint, String embeddingModel) {
        return records().stream()
                .filter(RetrievalIndexRecord::active)
                .filter(record -> record.schemaFingerprint().equals(fingerprint))
                .filter(record -> record.embeddingModel().equals(embeddingModel))
                .count();
    }

    default List<RetrievalIndexRecord> records() {
        return List.of();
    }
}
