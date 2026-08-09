package com.nlda.retrieval;

import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.impl.embedding.FakeEmbeddingClient;
import com.nlda.retrieval.text.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FakeEmbeddingClientTest {

    @Test
    void fakeEmbeddingIsDeterministicAndNormalized() {
        FakeEmbeddingClient client = new FakeEmbeddingClient(
                new EmbeddingProperties("fake", "fake-hash-embedding", "", "", 16, 0,
                        Duration.ofSeconds(1), 3),
                new TextNormalizer()
        );

        float[] first = client.embed("customer revenue");
        float[] second = client.embed("customer revenue");

        assertThat(first).containsExactly(second);
        assertThat(magnitude(first)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    private double magnitude(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
