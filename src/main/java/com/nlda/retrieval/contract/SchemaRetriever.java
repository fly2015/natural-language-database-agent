package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;

import java.util.List;

public interface SchemaRetriever {

    default void prepare() {
    }

    List<RetrievedChunk> retrieve(ProcessedQuery query, RetrievalMode mode);

    List<RetrievedChunk> fallback(ProcessedQuery query);
}

