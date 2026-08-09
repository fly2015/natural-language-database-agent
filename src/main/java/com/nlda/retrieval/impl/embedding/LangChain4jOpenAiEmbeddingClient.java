package com.nlda.retrieval.impl.embedding;

import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.contract.EmbeddingClient;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "agent.retrieval.embedding", name = "provider", havingValue = "openai")
public class LangChain4jOpenAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final OpenAiEmbeddingModel model;

    public LangChain4jOpenAiEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(properties.apiKey())
                .modelName(properties.model())
                .dimensions(properties.dimensions())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (!properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        this.model = builder.build();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text).content().vector();
    }
}
