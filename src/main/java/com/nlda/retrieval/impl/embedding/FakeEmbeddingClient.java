package com.nlda.retrieval.impl.embedding;

import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.text.TextNormalizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "agent.retrieval.embedding", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final TextNormalizer textNormalizer;

    public FakeEmbeddingClient(EmbeddingProperties properties, TextNormalizer textNormalizer) {
        this.properties = properties;
        this.textNormalizer = textNormalizer;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[properties.dimensions()];
        for (String term : textNormalizer.retrievalTerms(text)) {
            int hash = Math.abs(term.hashCode());
            int index = hash % vector.length;
            vector[index] += 1.0f;
            vector[(index * 31 + 7) % vector.length] += 0.35f;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        double magnitude = Math.sqrt(sum);
        if (magnitude == 0.0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / magnitude);
        }
    }
}
