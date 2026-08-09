package com.nlda.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent.retrieval.embedding")
public record EmbeddingProperties(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int dimensions,
        int maxRetries,
        Duration timeout,
        int searchLimit
) {
    public EmbeddingProperties {
        provider = blankDefault(provider, "fake");
        model = blankDefault(model, "fake-hash-embedding");
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? "" : baseUrl;
        dimensions = dimensions <= 0 ? 64 : dimensions;
        maxRetries = maxRetries < 0 ? 0 : maxRetries;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        searchLimit = searchLimit <= 0 ? 8 : searchLimit;
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
