package com.nlda.retrieval.contract;

public interface EmbeddingClient {

    String model();

    float[] embed(String text);
}
