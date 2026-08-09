package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;

import java.util.List;

public interface RetrievalVocabularyIndexService {

    void rebuild(SchemaMetadataSnapshot snapshot, List<RetrievedChunk> chunks);
}
