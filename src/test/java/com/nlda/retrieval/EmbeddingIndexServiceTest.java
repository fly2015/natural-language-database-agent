package com.nlda.retrieval;

import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.impl.vector.InMemoryVectorRetrievalRepository;
import com.nlda.retrieval.index.ChunkCanonicalizer;
import com.nlda.retrieval.index.EmbeddingIndexService;
import com.nlda.retrieval.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingIndexServiceTest {

    @Test
    void canonicalChunkHashIsStableAndChangesWithContent() {
        ChunkCanonicalizer canonicalizer = new ChunkCanonicalizer();
        RetrievedChunk first = RetrievedChunk.schema("schema.orders", "table: orders", 0.0, Set.of("orders"));
        RetrievedChunk same = RetrievedChunk.schema("schema.orders", "table: orders", 0.5, Set.of("orders"));
        RetrievedChunk changed = RetrievedChunk.schema("schema.orders", "table: orders; columns: id", 0.0,
                Set.of("orders"));

        assertThat(canonicalizer.contentHash(first)).isEqualTo(canonicalizer.contentHash(same));
        assertThat(canonicalizer.contentHash(first)).isNotEqualTo(canonicalizer.contentHash(changed));
    }

    @Test
    void indexesAndSearchesRealVectorsInMemory() {
        InMemoryVectorRetrievalRepository repository = new InMemoryVectorRetrievalRepository();
        EmbeddingClient embeddingClient = new StaticEmbeddingClient();
        EmbeddingIndexService indexService = new EmbeddingIndexService(
                embeddingClient,
                repository,
                new ChunkCanonicalizer()
        );
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.businessRule("rule.revenue", "revenue spending sales", 0.0,
                        Set.of("orders.total_amount"), Set.of("revenue")),
                RetrievedChunk.schema("schema.products", "products category inventory", 0.0, Set.of("products"))
        );

        indexService.rebuild("fingerprint-1", chunks);

        List<RetrievedChunk> results = repository.search(embeddingClient.embed("spending"), "fingerprint-1",
                embeddingClient.model(), 2);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().id()).isEqualTo("rule.revenue");
    }

    private static class StaticEmbeddingClient implements EmbeddingClient {

        @Override
        public String model() {
            return "static-test";
        }

        @Override
        public float[] embed(String text) {
            if (text.contains("revenue") || text.contains("spending") || text.contains("sales")) {
                return new float[]{1.0f, 0.0f, 0.0f};
            }
            return new float[]{0.0f, 1.0f, 0.0f};
        }
    }
}
