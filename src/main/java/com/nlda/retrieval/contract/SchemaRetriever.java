package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;

import java.util.List;

public interface SchemaRetriever {

    List<RetrievedChunk> retrieve(String query, RetrievalMode mode);

    List<RetrievedChunk> fallback(String query);
}


