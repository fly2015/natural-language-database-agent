package com.nlda.retrieval;

import java.util.List;

public interface SchemaRetriever {

    List<RetrievedChunk> retrieve(String query, RetrievalMode mode);

    List<RetrievedChunk> fallback(String query);
}
