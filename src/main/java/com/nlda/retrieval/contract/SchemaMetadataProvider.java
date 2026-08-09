package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;

public interface SchemaMetadataProvider {

    String dialect();

    SchemaMetadataSnapshot extract();
}
