package com.nlda.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent.retrieval.vocabulary")
public record VocabularyProperties(
        String provider,
        String datasourceId,
        double similarityThreshold,
        double ambiguityDelta,
        int maxCandidates,
        Duration rebuildTimeout
) {
    public VocabularyProperties {
        provider = blankDefault(provider, "in-memory");
        datasourceId = blankDefault(datasourceId, "default");
        similarityThreshold = similarityThreshold <= 0.0 ? 0.58 : similarityThreshold;
        ambiguityDelta = ambiguityDelta <= 0.0 ? 0.05 : ambiguityDelta;
        maxCandidates = maxCandidates <= 0 ? 5 : maxCandidates;
        rebuildTimeout = rebuildTimeout == null ? Duration.ofSeconds(30) : rebuildTimeout;
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
